package com.example.viko.data.network

import android.Manifest
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
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
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
        private const val RECEIVE_POLL_MS = 100
        private const val UTTERANCE_IDLE_MS = 450L

        private const val REBIND_DELAY_MS = 2_000L

        /**
         * Ceiling on a buffered utterance — 30s, which is also the longest window
         * Whisper can read. A long press must not grow memory without bound.
         */
        private const val MAX_UTTERANCE_BYTES = SAMPLE_RATE * 2 * 30
    }

    private val appContext = context.applicationContext
    private val voiceWifiLock = VoiceWifiLock(appContext)

    /** Last known IP per `device_id`. Rewritten whenever a badge is heard from. */
    private val badgeAddresses = ConcurrentHashMap<String, InetAddress>()
    @Volatile private var savedBadgeAddresses = emptyMap<String, InetAddress>()
    private val incomingVoice = IncomingVoiceActivity()
    val activeBadges = incomingVoice.active

    @Volatile internal var translationWorkerIds: Set<String> = emptySet()

    // Owned by the UDP receiver. Capture mode is chosen on the first packet.
    private val utterances = mutableMapOf<String, BadgeUtterance>()

    private class BadgeUtterance(val submit: ((deviceId: String, pcm: ByteArray, endedAt: Long) -> Unit)?) {
        private val pcm = submit?.let { ByteArrayOutputStream() }
        private var expected: Long? = null
        private var sawFirstPacket = false
        var lastReceivedAt = 0L
            private set

        fun startsNextTurn(sequence: Long): Boolean {
            val next = expected ?: return false
            // The badge resets its sequence on each press. A first packet
            // arriving slightly out of order should not split a turn.
            return sequence == 0L && next > 1 && (sawFirstPacket || next > 8)
        }

        fun append(sequence: Long, data: ByteArray, offset: Int, length: Int, now: Long) {
            lastReceivedAt = now
            val gap = (sequence - (expected ?: sequence)).toInt()
            if (gap < 0) return // Duplicate or late packet.
            if (sequence == 0L) sawFirstPacket = true
            if (pcm != null) {
                val missing = minOf(gap, 3) * CHUNK_BYTES
                if (missing > 0 && pcm.size() + missing <= MAX_UTTERANCE_BYTES) {
                    pcm.write(ByteArray(missing))
                }
                val count = minOf(length, MAX_UTTERANCE_BYTES - pcm.size())
                if (count > 0) pcm.write(data, offset, count)
            }
            expected = (sequence + 1) and 0xffffffffL
        }

        fun snapshot(): ByteArray? = pcm?.toByteArray()
    }

    internal data class TranslationCapture(
        val isEnabled: () -> Boolean,
        val worker: (deviceId: String, pcm: ByteArray, endedAt: Long) -> Unit,
        val manager: (deviceId: String, pcm: ByteArray, endedAt: Long) -> Unit
    )

    /** Capture mode is selected once per PTT press, in either direction. */
    @Volatile
    internal var translationCapture: TranslationCapture? = null

    /** Write every received utterance to `files/captures/` as a WAV, for diagnosis. */
    @Volatile
    var dumpUtterances: Boolean = false

    /** Whether the supervisor's microphone is capturing a turn. */
    private val _isTalking = MutableStateFlow(false)
    val isTalking = _isTalking.asStateFlow()
    private val _captureError = MutableStateFlow<String?>(null)
    val captureError = _captureError.asStateFlow()
    @Volatile private var submitSupervisorTurnAt: Long? = null
    private val senderMutex = Mutex()
    private val playback = PcmPlaybackQueue()
    private var playbackJob: Job? = null
    private var lastStatsMs = 0L

    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private fun wifiNetwork() = connectivity.allNetworks.firstOrNull {
        connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /** Bound to local Wi-Fi in [start]; both directions share this port. */
    @Volatile
    private var socket: DatagramSocket? = null

    private var receiveJob: Job? = null
    private var captureJob: Job? = null
    private var captureOwner: CoroutineScope? = null

    private var track: AudioTrack? = null

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
                    val wifi = wifiNetwork()
                    if (wifi == null) {
                        delay(500)
                        continue
                    }
                    DatagramSocket(AUDIO_PORT).use { bound ->
                        wifi.bindSocket(bound)
                        runCatching { bound.trafficClass = 46 shl 2 } // DSCP EF for real-time voice.
                        bound.receiveBufferSize = 64 * 1024
                        // Poll for finished turns even when the badge sends no more packets.
                        bound.soTimeout = RECEIVE_POLL_MS
                        socket = bound
                        Log.i(TAG, "audio channel listening on :$AUDIO_PORT")
                        receiveLoop(bound, wifi)
                    }
                } catch (e: IOException) {
                    // Losing the bind (Wi-Fi off, port taken) must not silently
                    // end audio for the rest of the process lifetime.
                    Log.e(TAG, "audio socket failed, rebinding in ${REBIND_DELAY_MS}ms", e)
                    delay(REBIND_DELAY_MS.milliseconds)
                } finally {
                    socket = null
                    if (isActive) utterances.forEach { (id, turn) -> submitUtterance(id, turn) }
                    utterances.clear()
                    playback.clear()
                    incomingVoice.clear()
                    voiceWifiLock.release()
                }
            }
        }
    }

    private fun CoroutineScope.receiveLoop(bound: DatagramSocket, wifi: android.net.Network) {
        val buffer = ByteArray(MAX_DATAGRAM)
        val packet = DatagramPacket(buffer, buffer.size)
        var checkedAt = 0L
        var lastHello = 0L
        while (isActive) {
            val now = SystemClock.elapsedRealtime()
            val turns = utterances.iterator()
            while (turns.hasNext()) {
                val (id, turn) = turns.next()
                if (now - turn.lastReceivedAt >= UTTERANCE_IDLE_MS) {
                    turns.remove()
                    submitUtterance(id, turn)
                }
            }
            incomingVoice.expire(now)
            voiceWifiLock.releaseIfIdle(_isTalking.value)
            if (now - checkedAt >= 1000) {
                checkedAt = now
                // A replaced/lost Wi-Fi network needs a fresh socket binding.
                if (wifiNetwork() != wifi) return
            }
            if (now - lastHello >= 3_000) {
                lastHello = now
                sendManagerHellos(bound, wifi)
            }
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
        val now = SystemClock.elapsedRealtime()
        badgeAddresses[deviceId] = packet.address
        if (incomingVoice.heard(deviceId, now)) Log.i(TAG, "Received first live audio for $deviceId")
        voiceWifiLock.touch()
        var utterance = utterances[deviceId]
        if (utterance?.startsNextTurn(sequence) == true) {
            submitUtterance(deviceId, utterance)
            utterances.remove(deviceId)
            utterance = null
        }
        if (utterance == null && deviceId in translationWorkerIds) {
            val submit = translationCapture?.takeIf { it.isEnabled() }?.worker
            utterance = BadgeUtterance(submit).also { utterances[deviceId] = it }
        }
        if (utterance?.submit == null && !_isTalking.value) {
            playback.offer(deviceId, sequence, data.copyOfRange(audioOffset, length), now)
        }

        if (now - lastStatsMs >= 5000) {
            lastStatsMs = now
            Log.i(TAG, "playback ${playback.stats()}")
        }
        utterance?.append(sequence, data, audioOffset, audioLength, now)
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

    private fun submitUtterance(deviceId: String, turn: BadgeUtterance) {
        val pcm = turn.snapshot()?.takeUnless { it.isEmpty() } ?: return
        Log.i(TAG, "Captured ${pcm.size / 2} samples from $deviceId")
        if (dumpUtterances) {
            Log.d(TAG, describe(pcm))
            dump(deviceId, pcm)
        }
        turn.submit?.invoke(deviceId, pcm, turn.lastReceivedAt)
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

    /** Check in at previously paired badges when a hotspot blocks mDNS discovery. */
    internal fun setSavedBadgeAddresses(addresses: Map<String, String>) {
        savedBadgeAddresses = addresses.mapNotNull { (id, address) ->
            Os.inet_pton(OsConstants.AF_INET, address)?.let { id to it }
        }.toMap()
    }

    private fun sendManagerHellos(bound: DatagramSocket, wifi: android.net.Network) {
        val onLink = connectivity.getLinkProperties(wifi)?.routes.orEmpty().filter {
            !it.hasGateway() && it.destination.prefixLength > 0
        }
        for ((id, address) in savedBadgeAddresses) {
            if (onLink.none { it.destination.contains(address) }) continue
            val json = JSONObject().put("type", "manager_hello").put("device_id", id)
                .toString().toByteArray(Charsets.UTF_8)
            val frame = ByteBuffer.allocate(2 + json.size).putShort(json.size.toShort()).put(json).array()
            try {
                bound.send(DatagramPacket(frame, frame.size, address, AUDIO_PORT))
            } catch (e: IOException) {
                Log.d(TAG, "Badge check-in could not be sent to $id at ${address.hostAddress}", e)
            }
        }
    }

    /**
     * Starts a direct call. Returns null on success or a message to show the manager.
     */
    fun startTalking(deviceId: String, scope: CoroutineScope): String? =
        startCapture(listOf(deviceId), scope, directCallId = deviceId)

    fun startChannelTalk(deviceIds: List<String>, scope: CoroutineScope): String? =
        startCapture(deviceIds, scope, directCallId = null)

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    private fun startCapture(deviceIds: List<String>, scope: CoroutineScope, directCallId: String?): String? {
        if (!scope.isActive) return "Call screen is closing"
        if (captureJob?.isCompleted == false) return "Microphone already active or stopping"
        _captureError.value = null
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
        val targets = deviceIds.distinct().mapNotNull { id -> badgeAddresses[id]?.let { id to it } }
        if (targets.isEmpty()) {
            return if (directCallId == null) "No reachable badges in this channel"
            else "Badge offline — nothing has been heard from it yet"
        }

        val record = newRecord() ?: return "Microphone unavailable — close other recording apps and retry"
        try {
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (_: Exception) {
            record.release()
            return "Could not start the microphone"
        }
        captureOwner = scope
        _isTalking.value = true
        voiceWifiLock.touch()
        submitSupervisorTurnAt = null
        // ATOMIC guarantees the finally block runs even if this screen closes
        // before the IO dispatcher starts the job (the recorder is already open).
        val submit = directCallId?.let { translationCapture?.takeIf { it.isEnabled() }?.manager }
        captureJob = scope.launch(Dispatchers.IO, start = CoroutineStart.ATOMIC) {
            val recorded = if (submit != null) ByteArrayOutputStream() else null
            try {
                var sequence = 0L
                val chunk = ByteArray(CHUNK_BYTES)
                var filled = 0
                while (isActive && _isTalking.value) {
                    val read = record.read(chunk, filled, CHUNK_BYTES - filled, AudioRecord.READ_BLOCKING)
                    if (read < 0) throw IOException("AudioRecord read failed: $read")
                    if (read == 0) { delay(1.milliseconds); continue }
                    filled += read
                    if (filled < CHUNK_BYTES) continue
                    if (recorded == null) sendLiveFrame(targets, sequence++, chunk, filled)
                    recorded?.let {
                        val keep = minOf(filled, MAX_UTTERANCE_BYTES - it.size())
                        if (keep > 0) it.write(chunk, 0, keep)
                    }
                    filled = 0
                }
                val tail = filled and -2
                if (tail > 0) {
                    if (recorded == null) sendLiveFrame(targets, sequence, chunk, tail)
                    else {
                        val keep = minOf(tail, MAX_UTTERANCE_BYTES - recorded.size())
                        if (keep > 0) recorded.write(chunk, 0, keep)
                    }
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
                val endedAt = submitSupervisorTurnAt
                if (endedAt != null && directCallId != null && recorded != null && recorded.size() > 0)
                    submit?.invoke(directCallId, recorded.toByteArray(), endedAt)
            }
        }
        return null
    }

    fun stopTalking(scope: CoroutineScope, submitTranslation: Boolean = false) {
        if (captureOwner !== scope) return
        // Let the current blocking read finish (20ms), then release before allowing
        // another capture. Never snapshot a buffer while its producer is writing.
        submitSupervisorTurnAt = if (submitTranslation) SystemClock.elapsedRealtime() else null
        _isTalking.value = false
    }

    suspend fun playLocalPcm(deviceId: String, pcm: ByteArray) = playLocalPcm(deviceId, flowOf(pcm))

    suspend fun playLocalPcm(deviceId: String, chunks: Flow<ByteArray>, firstFrame: () -> Unit = {}): Boolean =
        pacePcm(chunks, firstFrame) { frame, sequence ->
            playback.offer(deviceId, sequence, frame, SystemClock.elapsedRealtime())
        }

    suspend fun sendPcm(deviceId: String, pcm: ByteArray) = sendPcm(deviceId, flowOf(pcm))

    suspend fun sendPcm(deviceId: String, chunks: Flow<ByteArray>, firstFrame: () -> Unit = {}): Boolean {
        val target = badgeAddresses[deviceId] ?: run {
            Log.w(TAG, "cannot send to $deviceId: no address known")
            return false
        }
        if (socket == null) {
            Log.w(TAG, "cannot send to $deviceId: audio socket not bound")
            return false
        }
        return withContext(Dispatchers.IO) {
            senderMutex.withLock {
                pacePcm(chunks, firstFrame) { frame, sequence ->
                    sendTo(deviceId, target, sequence, frame, frame.size)
                    true
                }
            }
        }
    }

    private class ReplyInterrupted : CancellationException()

    /** Synthesised chunks may be larger than a datagram. Pace frames across chunk boundaries. */
    private suspend fun pacePcm(
        chunks: Flow<ByteArray>, firstFrame: () -> Unit, deliver: (ByteArray, Long) -> Boolean
    ): Boolean = supervisorScope {
        var sequence = 0L
        var nextFrameAt = 0L
        var delivered = false
        val stream = async(start = CoroutineStart.LAZY) {
            chunks.pcmFrames().collect { frame ->
                val remaining = nextFrameAt - System.nanoTime()
                if (remaining > 0) delay((remaining + 999_999) / 1_000_000)
                if (isTalking.value) throw ReplyInterrupted()
                val accepted = deliver(frame, sequence++)
                if (accepted && !delivered) firstFrame()
                delivered = delivered || accepted
                // Restart pacing after a slow inference chunk. Never burst packets to catch up.
                nextFrameAt = System.nanoTime() + CHUNK_NANOS
            }
        }
        val interrupt = launch(start = CoroutineStart.UNDISPATCHED) {
            isTalking.first { it }
            stream.cancel(ReplyInterrupted())
        }
        try {
            stream.await()
            delivered
        } catch (_: ReplyInterrupted) {
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "prepared audio stream stopped", e)
            // Preserve a partially delivered reply without replaying the original.
            delivered
        } finally {
            interrupt.cancel()
        }
    }

    private fun Flow<ByteArray>.pcmFrames(): Flow<ByteArray> = flow {
        var frame = ByteArray(CHUNK_BYTES)
        var filled = 0
        collect { chunk ->
            require(chunk.size % 2 == 0) { "PCM16 requires complete samples" }
            var offset = 0
            while (offset < chunk.size) {
                val count = minOf(CHUNK_BYTES - filled, chunk.size - offset)
                chunk.copyInto(frame, filled, offset, offset + count)
                filled += count
                offset += count
                if (filled == CHUNK_BYTES) {
                    emit(frame)
                    frame = ByteArray(CHUNK_BYTES)
                    filled = 0
                }
            }
        }
        if (filled > 0) emit(frame.copyOf(filled))
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

    private fun sendLiveFrame(targets: List<Pair<String, InetAddress>>, sequence: Long, audio: ByteArray, length: Int) {
        var delivered = false
        var lastError: IOException? = null
        for ((id, address) in targets) {
            try {
                sendTo(id, address, sequence, audio, length)
                delivered = true
            } catch (error: IOException) {
                lastError = error
            }
        }
        if (!delivered) lastError?.let { throw it }
    }

    /**
     * Sends one chunk to a badge in the badge's own frame format. `device_id` is
     * the *target* badge — its firmware drops any chunk not addressed to it,
     * since every badge listens on the same port.
     */
    private fun sendTo(deviceId: String, target: InetAddress, sequence: Long, audio: ByteArray, length: Int) {
        val bound = socket ?: throw IOException("Audio socket unavailable")
        voiceWifiLock.touch()
        val header = JSONObject().apply {
            put("type", "audio")
            put("device_id", deviceId)
            put("sequence", sequence and 0xffffffffL)
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
