package com.example.viko.data.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * The manager side of the badge **audio** channel — the other half of
 * [BadgeControlServer], and the thing that actually moves speech.
 *
 * Mirrors `sendAudioChunk` / `poll` in
 * firmware/wearable_badge_network/network/BadgeNetwork.h. Port, framing and
 * header field names must stay in lockstep with that file and with
 * docs/networking-decisions.md.
 *
 * **UDP 5002, both directions.** One datagram per 20ms of speech:
 *
 * ```
 * [2 bytes big-endian header length N][N bytes JSON header][raw PCM16 audio]
 * ```
 *
 * A bounded FIFO separates UDP intake from blocking speaker writes. Voice-only
 * mode bypasses all utterance collection and AI; the existing AI seam is kept.
 * Playback is half duplex and selects one badge at a time.

 */
class BadgeAudioServer(context: Context) {

    companion object {
        private const val TAG = "VikoAudio"

        /** Must match VIKO_AUDIO_PORT in BadgeNetwork.h. */
        const val AUDIO_PORT = 5002

        /** Must match SAMPLE_RATE in wearable_badge_network.ino. */
        private const val SAMPLE_RATE = 16_000

        /** Must match CHUNK_SAMPLES — 20ms at 16kHz. */
        private const val CHUNK_SAMPLES = 320
        private const val CHUNK_BYTES = CHUNK_SAMPLES * 2

        /** How much wall-clock time one chunk represents. */
        private const val CHUNK_MS = 1000L * CHUNK_SAMPLES / SAMPLE_RATE
        private const val CHUNK_NANOS = CHUNK_MS * 1_000_000

        /** Must match AUDIO_FORMAT in PacketProtocol.h. */
        private const val PCM16 = "pcm16"

        /**
         * Big enough for header + chunk with room to spare. The badge sends
         * ~640 audio bytes under a ~200 byte header; anything larger than this
         * is not ours and gets truncated rather than crashing the loop.
         */
        private const val MAX_DATAGRAM = 2048

        /**
         * Silence after which the speaker is stopped and drained. Also bounds
         * how long a stalled stream holds the audio focus.
         *
         * This is what keeps latency from creeping: the badge's I2S clock and
         * the phone's DAC are never exactly 16000Hz, so over a long stream one
         * outruns the other and [AudioTrack]'s buffer either fills (adding
         * delay) or empties. Stopping between utterances resets that drift, and
         * push-to-talk gives a natural gap to do it in.
         */
        private const val IDLE_TIMEOUT_MS = 400

        private const val REBIND_DELAY_MS = 2_000L

        /**
         * Ceiling on a buffered utterance — 30s, which is also the longest window
         * Whisper can read. Exists because `ptt_end` can be lost: UDP audio keeps
         * arriving but the TCP packet that ends the turn may never come, and an
         * unbounded buffer would then grow until the process dies.
         */
        private const val MAX_UTTERANCE_BYTES = SAMPLE_RATE * 2 * 30
    }

    private val appContext = context.applicationContext

    /** Last known IP per `device_id`. Rewritten whenever a badge is heard from. */
    private val badgeAddresses = ConcurrentHashMap<String, InetAddress>()

    /**
     * Speech accumulating between `ptt_start` and `ptt_end`, per badge.
     *
     * A **tee**, not an interception: [play] still gets every chunk the moment it
     * arrives, so the supervisor hears the worker live and the voice link keeps
     * working even if nothing downstream does. Transcription happens afterwards
     * from this copy.
     *
     * Keyed by `device_id` so two badges holding their buttons at once do not
     * interleave into one another's utterance.
     */
    private val utterances = ConcurrentHashMap<String, ByteArrayOutputStream>()

    /**
     * Called on `ptt_end` with the whole utterance as PCM16.
     *
     * Invoked on the receive thread, so the handler must hand off immediately —
     * the cascade takes seconds and this loop has datagrams waiting.
     */
    @Volatile
    var onUtterance: ((deviceId: String, pcm: ByteArray) -> Unit)? = null

    /** Live relay is the default for the voice-only build. */
    @Volatile
    var relayLiveAudio: Boolean = !VoiceMode.TRANSLATION_ENABLED

    /** Write every received utterance to `files/captures/` as a WAV, for diagnosis. */
    @Volatile
    var dumpUtterances: Boolean = false

    @Volatile
    var relayLiveMic: Boolean = !VoiceMode.TRANSLATION_ENABLED

    /**
     * Called when the supervisor releases the talk button, with everything the
     * phone mic captured. The mirror of [onUtterance] for the other direction.
     */
    @Volatile
    var onSupervisorUtterance: ((deviceId: String, pcm: ByteArray) -> Unit)? = null

    /** The supervisor's turn, accumulating while they hold the button. */
    private val _isTalking = MutableStateFlow(false)
    val isTalking = _isTalking.asStateFlow()
    private val _captureError = MutableStateFlow<String?>(null)
    val captureError = _captureError.asStateFlow()
    @Volatile private var submitSupervisorTurn = false
    private val senderMutex = Mutex()
    private val playback = PcmPlaybackQueue()
    private var playbackJob: Job? = null
    private var lastStatsMs = 0L

    /** Bound in [start]; also used by [sendTo], so both directions share one port. */
    @Volatile
    private var socket: DatagramSocket? = null

    private var receiveJob: Job? = null
    private var captureJob: Job? = null

    private var track: AudioTrack? = null

    /**
     * Sequence counter for outgoing audio, reset per utterance so the badge sees
     * a stream starting at 0 exactly as its own does.
     */
    private var outgoingSequence = 0L

    val hasMicPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Binds UDP 5002 and plays whatever badges send until [scope] is cancelled.
     * Safe to call twice; the second call is a no-op.
     */
    fun start(scope: CoroutineScope) {
        if (receiveJob != null) return
        playbackJob = scope.launch(Dispatchers.IO) { playbackLoop() }
        receiveJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    DatagramSocket(AUDIO_PORT).use { bound ->
                        // soTimeout doubles as the silence detector below.
                        bound.soTimeout = IDLE_TIMEOUT_MS
                        socket = bound
                        Log.i(TAG, "audio channel listening on :$AUDIO_PORT")
                        receiveLoop(bound)
                    }
                } catch (e: IOException) {
                    // Losing the bind (Wi-Fi off, port taken) must not silently
                    // end audio for the rest of the process lifetime.
                    Log.e(TAG, "audio socket failed, rebinding in ${REBIND_DELAY_MS}ms", e)
                    delay(REBIND_DELAY_MS.milliseconds)
                } finally {
                    socket = null
                    playback.clear()
                }
            }
        }
    }

    private fun CoroutineScope.receiveLoop(bound: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isActive) {
            try {
                packet.length = buffer.size // DatagramPacket shrinks after a short datagram
                bound.receive(packet)
            } catch (_: SocketTimeoutException) {
                // The playback worker independently drains its queue and releases on idle.
                continue
            }
            onChunkReceived(packet)
        }
    }

    /** Decodes one datagram and plays it. Malformed frames are dropped, not fatal. */
    private fun onChunkReceived(packet: DatagramPacket) {
        val data = packet.data
        val length = packet.length
        if (length < 2) return

        val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        val audioOffset = 2 + headerLength
        if (headerLength <= 0 || audioOffset > length) {
            Log.w(TAG, "dropping malformed frame: header len $headerLength of $length bytes")
            return
        }

        val header = runCatching {
            JSONObject(String(data, 2, headerLength, Charsets.UTF_8))
        }.getOrNull() ?: return
        val deviceId = header.optString("device_id").ifEmpty { return }

        if (header.optString("type") != "audio" ||
            header.optString("audio_format") != PCM16 ||
            header.optInt("sample_rate", SAMPLE_RATE) != SAMPLE_RATE ||
            header.optInt("channels", 1) != 1) return
        val audioLength = length - audioOffset
        val sequence = header.optLong("sequence", -1)
        if (audioLength !in 2..CHUNK_BYTES || audioLength % 2 != 0 ||
            sequence !in 0..0xffffffffL) return
        badgeAddresses[deviceId] = packet.address
        if (relayLiveAudio && !_isTalking.value) {
            playback.offer(deviceId, sequence, data.copyOfRange(audioOffset, length),
                android.os.SystemClock.elapsedRealtime())
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastStatsMs >= 5000) {
            lastStatsMs = now
            Log.i(TAG, "playback ${playback.stats()}")
        }
        // Tee into the utterance buffer if this badge is mid-turn. After play(),
        // deliberately: a failure here must never cost the supervisor the live audio.
        utterances[deviceId]?.let { buffer ->
            if (buffer.size() + audioLength <= MAX_UTTERANCE_BYTES) {
                buffer.write(data, audioOffset, audioLength)
            } else if (buffer.size() < MAX_UTTERANCE_BYTES) {
                Log.w(TAG, "$deviceId utterance hit ${MAX_UTTERANCE_BYTES / (SAMPLE_RATE * 2)}s, truncating")
                buffer.write(data, audioOffset, MAX_UTTERANCE_BYTES - buffer.size())
            }
        }
    }

    /** The sole owner of AudioTrack; the receive thread never waits for the DAC. */
    private suspend fun CoroutineScope.playbackLoop() {
        var lastPlayed = 0L
        try {
            while (isActive) {
                if (_isTalking.value) {
                    playback.clear()
                    releasePlayback()
                    delay(10.milliseconds)
                    continue
                }
                val frame = playback.poll(40)
                if (frame == null) {
                    if (android.os.SystemClock.elapsedRealtime() - lastPlayed > IDLE_TIMEOUT_MS)
                        releasePlayback()
                    continue
                }
                try {
                    if (track == null) delay(40.milliseconds) // small startup prefill for Wi-Fi jitter
                    if (_isTalking.value) continue
                    if (android.os.SystemClock.elapsedRealtime() - frame.receivedMs > 180) continue
                    play(frame.pcm, 0, frame.pcm.size)
                    lastPlayed = android.os.SystemClock.elapsedRealtime()
                } catch (e: RuntimeException) {
                    Log.e(TAG, "speaker unavailable", e)
                    releasePlayback()
                }
            }
        } finally { releasePlayback() }
    }

    private fun play(data: ByteArray, offset: Int, length: Int) {
        val active = track ?: newTrack().also { track = it }
        if (active.playState != AudioTrack.PLAYSTATE_PLAYING) active.play()
        var written = 0
        while (written < length) {
            val count = active.write(data, offset + written, length - written, AudioTrack.WRITE_BLOCKING)
            if (count <= 0) throw IllegalStateException("AudioTrack write failed: $count")
            written += count
        }
    }

    private fun newTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        check(minBuffer > 0) { "16kHz speaker output unavailable" }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // MEDIA, not VOICE_COMMUNICATION: this plays out of the
                    // loudspeaker at media volume. VOICE_* routes to the earpiece
                    // on most phones, which is useless on a factory floor.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            // Respect the device minimum; target 60ms rather than 4x minimum.
            .setBufferSizeInBytes(maxOf(minBuffer, CHUNK_BYTES * 3))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun releasePlayback() {
        track?.let {
            runCatching { it.pause(); it.flush() }
            it.release()
        }
        track = null
    }

    /**
     * Starts collecting an utterance for this badge. Called on `ptt_start`.
     *
     * A second `ptt_start` without an intervening `ptt_end` discards whatever was
     * buffered rather than concatenating: the badge rebooted or a packet was lost,
     * and half of one turn glued to another transcribes as nonsense.
     */
    fun beginUtterance(deviceId: String) {
        if (!VoiceMode.TRANSLATION_ENABLED) return
        if (utterances.put(deviceId, ByteArrayOutputStream()) != null) {
            Log.w(TAG, "$deviceId started a new turn without ending the last — discarding it")
        }
    }

    /**
     * Ends the utterance and hands it to [onUtterance]. Called on `ptt_end`.
     *
     * Silent if nothing was buffered, which happens routinely: a badge whose
     * button was tapped rather than held sends both control packets with no audio
     * between them.
     */
    fun endUtterance(deviceId: String) {
        val buffer = utterances.remove(deviceId) ?: return
        val pcm = buffer.toByteArray()
        if (pcm.isEmpty()) return
        Log.i(
            TAG,
            "$deviceId utterance: ${pcm.size / 2} samples, " +
                "${pcm.size / (SAMPLE_RATE * 2f)}s, ${describe(pcm)}"
        )
        if (dumpUtterances) dump(deviceId, pcm)
        onUtterance?.invoke(deviceId, pcm)
    }

    /**
     * Peak, RMS and DC offset of an utterance — the three numbers that separate
     * the ways badge audio goes wrong, all of which sound alike.
     *
     * - peak at or near 32767 is clipping; the analog stage is railing before the
     *   ADC sees it and mic gain cannot help, only hurt.
     * - a large DC offset means the ADC baseline is off, which sounds like a thump
     *   and drags every mel frame with it.
     * - rms far below peak is a mic that is mostly silence with occasional spikes,
     *   i.e. a gate cutting speech rather than noise.
     */
    private fun describe(pcm: ByteArray): String {
        val n = pcm.size / 2
        if (n == 0) return "empty"
        var peak = 0
        var sum = 0.0
        var dc = 0.0
        for (i in 0 until n) {
            val v = ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort().toInt()
            if (kotlin.math.abs(v) > peak) peak = kotlin.math.abs(v)
            sum += v.toDouble() * v
            dc += v
        }
        val rms = kotlin.math.sqrt(sum / n)
        return "peak $peak, rms ${"%.0f".format(rms)}, dc ${"%.0f".format(dc / n)}" +
            if (peak >= 32000) "  ** CLIPPING **" else ""
    }

    /**
     * Writes the utterance to `files/captures/` as a playable WAV.
     *
     * Off by default. Badge audio that transcribes wrongly is nearly impossible to
     * diagnose by ear over a factory floor, and this is the only way to get the
     * exact bytes the ASR saw onto a workstation where they can be looked at.
     */
    private fun dump(deviceId: String, pcm: ByteArray) {
        runCatching {
            val dir = File(appContext.getExternalFilesDir(null), "captures").apply { mkdirs() }
            val file = File(dir, "$deviceId-${System.currentTimeMillis()}.wav")
            file.outputStream().use { out ->
                out.write(wavHeader(pcm.size))
                out.write(pcm)
            }
            Log.i(TAG, "wrote ${file.absolutePath}")
        }.onFailure { Log.w(TAG, "could not dump utterance", it) }
    }

    /** Canonical 44-byte PCM16 mono header. */
    private fun wavHeader(dataBytes: Int): ByteArray =
        ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }.array()

    /**
     * Records the IP a badge was last seen at. Called with the source address of
     * TCP control messages so the manager can push audio to a badge that has
     * not streamed anything yet — a badge only sends UDP while its button is
     * held, but registers as soon as it joins the network.
     */
    fun rememberBadge(deviceId: String, address: InetAddress) {
        badgeAddresses[deviceId] = address
    }

    /**
     * Streams the phone mic to one badge until [stopTalking]. Returns null on
     * success, or a reason to show the supervisor — a talk button that lights up
     * and sends nothing is the worst outcome, so every way this can fail says so
     * rather than returning quietly.
     *
     * ponytail: one target badge at a time. A broadcast announcement to a whole
     * channel means sending each chunk to several addresses; add that when the
     * announcement sheet is wired up, not before.
     */
    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    fun startTalking(deviceId: String, scope: CoroutineScope): String? {
        _captureError.value = null
        if (!scope.isActive) return "Call screen is closing"
        if (captureJob?.isCompleted == false) return "Microphone already active or stopping"
        if (!hasMicPermission) {
            Log.w(TAG, "cannot talk: RECORD_AUDIO not granted")
            return "Microphone permission needed to talk"
        }
        if (socket == null) {
            // Only reachable in the moment before start() binds, or mid-rebind
            // after Wi-Fi dropped. Worth naming: it resolves by itself.
            Log.w(TAG, "cannot talk: audio socket not bound yet")
            return "Audio channel starting — try again in a moment"
        }
        val target = badgeAddresses[deviceId]
        if (target == null) {
            Log.w(TAG, "cannot talk: no address known for $deviceId — has it registered?")
            return "Badge offline — nothing has been heard from it yet"
        }

        _captureError.value = null
        val record = newRecord() ?: return "Microphone unavailable — close other recording apps and retry"
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (_: Exception) {
            record.release()
            return "Could not start the microphone"
        }
        _isTalking.value = true
        submitSupervisorTurn = false
        // ATOMIC guarantees the finally block runs even if this screen closes
        // before the IO dispatcher starts the job (the recorder is already open).
        captureJob = scope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            val recorded = if (VoiceMode.TRANSLATION_ENABLED) ByteArrayOutputStream() else null
            try {
                senderMutex.withLock {
                    outgoingSequence = 0
                    val chunk = ByteArray(CHUNK_BYTES)
                    var filled = 0
                    while (isActive && _isTalking.value) {
                        val read = record.read(chunk, filled, CHUNK_BYTES - filled, AudioRecord.READ_BLOCKING)
                        if (read < 0) throw IOException("AudioRecord read failed: $read")
                        if (read == 0) { delay(1.milliseconds); continue }
                        filled += read
                        if (filled < CHUNK_BYTES) continue
                        if (relayLiveMic) sendTo(deviceId, target, chunk, filled)
                        recorded?.let {
                            val keep = minOf(filled, MAX_UTTERANCE_BYTES - it.size())
                            if (keep > 0) it.write(chunk, 0, keep)
                        }
                        filled = 0
                    }
                    if (filled > 0 && filled % 2 == 0 && relayLiveMic)
                        sendTo(deviceId, target, chunk, filled)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _captureError.value = "Voice transmission stopped: ${e.message}"
                Log.e(TAG, "microphone stream failed", e)
            } finally {
                runCatching { record.stop() }
                record.release()
                _isTalking.value = false
                if (submitSupervisorTurn && recorded != null && recorded.size() > 0)
                    onSupervisorUtterance?.invoke(deviceId, recorded.toByteArray())
            }
        }
        return null
    }

    fun stopTalking(deviceId: String? = null) {
        // Let the current blocking read finish (20ms), then release before allowing
        // another capture. Never snapshot a buffer while its producer is writing.
        submitSupervisorTurn = deviceId != null
        _isTalking.value = false
    }

    /**
     * Sends an already-prepared PCM16 buffer to a badge — synthesised Vietnamese,
     * rather than the live mic.
     *
     * **Paced in real time.** The badge has a small jitter buffer sized for one
     * chunk per 20ms, and firing a whole utterance at wire speed overruns it: the
     * chunks arrive faster than its DAC drains them and the tail is dropped. The
     * mic path gets this pacing for free from the blocking `AudioRecord.read`;
     * here it has to be deliberate.
     *
     * Suspends for roughly the duration of the audio. Returns false if the badge's
     * address is unknown or the socket is down.
     */
    suspend fun sendPcm(deviceId: String, pcm: ByteArray): Boolean {
        val target = badgeAddresses[deviceId] ?: run {
            Log.w(TAG, "cannot send to $deviceId: no address known")
            return false
        }
        if (socket == null) {
            Log.w(TAG, "cannot send to $deviceId: audio socket not bound")
            return false
        }
        if (pcm.size % 2 != 0) return false
        return try { senderMutex.withLock {
            outgoingSequence = 0
            var offset = 0
            var deadline = System.nanoTime()
            while (offset < pcm.size) {
                val length = minOf(CHUNK_BYTES, pcm.size - offset)
                sendTo(deviceId, target, pcm.copyOfRange(offset, offset + length), length)
                offset += length
                // No fast pre-roll or unbounded catch-up bursts after a stall.
                deadline = maxOf(deadline + CHUNK_NANOS, System.nanoTime())
                val waitMs = (deadline - System.nanoTime()) / 1_000_000
                if (waitMs > 0) delay(waitMs.milliseconds)
            }
            true
        } } catch (e: IOException) {
            Log.w(TAG, "prepared audio transmission failed", e)
            false
        }
    }

    private fun newRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        val record = try {
            @Suppress("MissingPermission") // checked in startTalking
            AudioRecord(
                // Request the platform speech capture path. Processing varies by
                // phone; half-duplex muting is our primary feedback protection.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK_BYTES * 4)
            )
        } catch (e: RuntimeException) {
            Log.e(TAG, "cannot open mic at ${SAMPLE_RATE}Hz mono", e)
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "mic failed to initialise (in use by another app?)")
            record.release()
            return null
        }
        return record
    }

    /**
     * Sends one chunk to a badge in the badge's own frame format. `device_id` is
     * the *target* badge — its firmware drops any chunk not addressed to it,
     * since every badge listens on the same port.
     */
    private fun sendTo(deviceId: String, target: InetAddress, audio: ByteArray, length: Int) {
        val bound = socket ?: throw IOException("Audio socket unavailable")
        val header = JSONObject().apply {
            put("type", "audio")
            put("device_id", deviceId)
            put("sequence", outgoingSequence)
            outgoingSequence = (outgoingSequence + 1) and 0xffffffffL
            put("audio_format", PCM16)
        }.toString().toByteArray(Charsets.UTF_8)

        val frame = ByteArray(2 + header.size + length)
        frame[0] = (header.size shr 8).toByte()
        frame[1] = (header.size and 0xFF).toByte()
        header.copyInto(frame, 2)
        audio.copyInto(frame, 2 + header.size, 0, length)

        bound.send(DatagramPacket(frame, frame.size, target, AUDIO_PORT))
    }
}
