package com.example.viko.data.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

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
 * Nothing is buffered to a file or waited for: a chunk that arrives is written
 * straight to the speaker, and a chunk captured from the phone mic goes straight
 * out. Latency is one chunk plus whatever [AudioTrack] is holding, not the
 * length of the utterance. That matters at both ends — the badge cannot hold a
 * whole utterance in RAM either.
 *
 * Badges are addressed by `device_id`, never by IP: DHCP leases change on every
 * reconnect (Detailed System.docx, "Topology"). The IP behind an id is learned
 * from whatever packet arrived most recently — a UDP chunk here, or a TCP
 * control message via [rememberBadge] — so the manager can talk to a badge that
 * has not spoken yet.
 *
 * **Where translation goes.** Received audio is still played as-is, so the
 * supervisor hears the worker live with no pipeline latency in the way. In
 * parallel it is accumulated per badge, and the silence that ends a push-to-talk
 * press hands the whole utterance to [onUtterance] — which
 * `com.example.viko.data.ai.VoicePipeline` answers, sending Korean back through
 * [sendUtterance]. Nothing here knows what a translation is; the transport just
 * grew an utterance boundary and a way to push a finished buffer.
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
         * Writes each received utterance to a WAV under the app's external files
         * dir, for diagnosing bad audio off the real phone path. Set false to
         * turn off; it costs one file write per utterance and nothing else.
         *
         * The point is that a log can only report what the manager *decided*
         * about a chunk. When speech arrives distorted, the bytes themselves are
         * the evidence, and this is the only place they exist unmodified —
         * [AudioTrack] is a sink, not something that can be read back.
         */
        private const val DEBUG_CAPTURE = true

        /** Cap on one captured utterance, so a stuck stream cannot eat storage. */
        private const val CAPTURE_MAX_SECONDS = 30
        private const val CAPTURE_MAX_BYTES = SAMPLE_RATE * 2 * CAPTURE_MAX_SECONDS
    }

    private val appContext = context.applicationContext

    /** Last known IP per `device_id`. Rewritten whenever a badge is heard from. */
    private val badgeAddresses = ConcurrentHashMap<String, InetAddress>()

    /**
     * Last `sequence` seen per badge, purely so [onChunkReceived] can report
     * gaps. Chunks are numbered consecutively from 0 at every `ptt_start`, so a
     * jump means the network ate 20ms of speech — audible as a click or a
     * clipped syllable, and impossible to diagnose from the badge, which has no
     * idea its datagram never arrived. If this stays quiet while audio still
     * sounds rough, the problem is capture-side, not transport.
     */
    private val lastSequence = ConcurrentHashMap<String, Int>()

    /** Bound in [start]; also used by [sendTo], so both directions share one port. */
    @Volatile
    private var socket: DatagramSocket? = null

    private var receiveJob: Job? = null
    private var captureJob: Job? = null

    private var track: AudioTrack? = null

    /**
     * Buffers the utterance currently arriving, when [DEBUG_CAPTURE] is on.
     * Buffered rather than streamed to disk purely so the WAV header can be
     * written once with the real length — 30s of 16kHz mono is under a megabyte,
     * which is a different scale of problem from the badge's ~400KB of RAM.
     */
    private var capture: ByteArrayOutputStream? = null

    /**
     * Sequence counter for outgoing audio, reset per utterance so the badge sees
     * a stream starting at 0 exactly as its own does.
     */
    private var outgoingSequence = 0

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
                    delay(REBIND_DELAY_MS)
                } finally {
                    socket = null
                    stopPlayback()
                }
            }
        }
    }

    private fun CoroutineScope.receiveLoop(bound: DatagramSocket) {
        val buffer = ByteArray(MAX_DATAGRAM)
        val packet = DatagramPacket(buffer, buffer.size)
        while (isActive) {
            try {
                bound.receive(packet)
            } catch (e: SocketTimeoutException) {
                // Nothing for IDLE_TIMEOUT_MS — the utterance is over. stop()
                // (not pause/flush) so the tail already queued still plays out.
                stopPlayback()
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

        // Learn where this badge is now. Its DHCP lease may have changed since
        // it registered, and this is the freshest evidence we have.
        badgeAddresses[deviceId] = packet.address

        val format = header.optString("audio_format", PCM16)
        if (format != PCM16) {
            // Opus is what the docx specifies and what firmware will send once
            // an encoder is integrated. Playing it as PCM would be loud noise.
            Log.w(TAG, "dropping $deviceId chunk: audio_format=$format, only $PCM16 is decodable")
            return
        }

        val sequence = header.optInt("sequence", -1)
        if (sequence >= 0) {
            val previous = lastSequence.put(deviceId, sequence)
            // sequence <= previous means a new utterance restarted the counter.
            if (previous != null && sequence > previous + 1) {
                Log.w(TAG, "$deviceId lost ${sequence - previous - 1} chunk(s) " +
                    "(${(sequence - previous - 1) * 20}ms) before seq $sequence")
            }
        }

        val audioLength = length - audioOffset
        if (audioLength <= 0) return

        if (DEBUG_CAPTURE) {
            val sink = capture ?: ByteArrayOutputStream().also { capture = it }
            if (sink.size() < CAPTURE_MAX_BYTES) sink.write(data, audioOffset, audioLength)
        }
        play(data, audioOffset, audioLength)
    }

    /**
     * ponytail: one shared [AudioTrack], so two badges talking at once are mixed
     * by interleaving rather than actually mixed — it would sound garbled. PTT
     * plus a supervisor watching one member makes that rare. Give each
     * `device_id` its own track, or sum them, if group channels ever go live.
     */
    private fun play(data: ByteArray, offset: Int, length: Int) {
        val active = track ?: newTrack().also { track = it }
        if (active.playState != AudioTrack.PLAYSTATE_PLAYING) active.play()

        // Non-blocking: if the buffer is full we are already behind, and
        // blocking here would stall the receive loop and drop datagrams the OS
        // is holding. Losing the tail of one chunk is the cheaper failure.
        val written = active.write(data, offset, length, AudioTrack.WRITE_NON_BLOCKING)
        if (written < length) Log.w(TAG, "playback behind: dropped ${length - written} bytes")
    }

    private fun newTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
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
            // 4x the minimum: enough slack to ride out Wi-Fi jitter without
            // adding audible delay (min is typically well under 100ms here).
            .setBufferSizeInBytes(maxOf(minBuffer, CHUNK_BYTES) * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun stopPlayback() {
        track?.takeIf { it.playState == AudioTrack.PLAYSTATE_PLAYING }?.stop()
        if (DEBUG_CAPTURE) writeCapture()
    }

    /**
     * Flushes the buffered utterance to `badge_audio.wav`, overwriting the
     * previous one. Called from the same idle timeout that stops playback, so
     * one press of PTT produces one file with no button to remember to press.
     *
     * Pull it with:
     *   adb pull /sdcard/Android/data/com.example.viko/files/badge_audio.wav
     */
    private fun writeCapture() {
        val pcm = capture?.takeIf { it.size() > 0 }?.toByteArray() ?: return
        capture = null
        val target = File(appContext.getExternalFilesDir(null), "badge_audio.wav")
        try {
            target.outputStream().use { out ->
                out.write(wavHeader(pcm.size))
                out.write(pcm)
            }
            Log.i(TAG, "captured ${"%.1f".format(pcm.size / 2f / SAMPLE_RATE)}s -> ${target.absolutePath}")
        } catch (e: IOException) {
            Log.w(TAG, "could not write capture", e)
        }
    }

    /** Canonical 44-byte mono PCM16 RIFF header. */
    private fun wavHeader(pcmBytes: Int): ByteArray {
        val byteRate = SAMPLE_RATE * 2
        val out = ByteArrayOutputStream(44)
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) = out.write(byteArrayOf(
            v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
        fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))

        ascii("RIFF"); le32(36 + pcmBytes); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)      // PCM, mono
        le32(SAMPLE_RATE); le32(byteRate); le16(2); le16(16)
        ascii("data"); le32(pcmBytes)
        return out.toByteArray()
    }

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
    fun startTalking(deviceId: String, scope: CoroutineScope): String? {
        if (captureJob != null) return null
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

        outgoingSequence = 0
        captureJob = scope.launch(Dispatchers.IO) {
            val record = newRecord() ?: return@launch
            try {
                record.startRecording()
                val chunk = ByteArray(CHUNK_BYTES)
                while (isActive) {
                    // Blocking read paces this loop at real time — one chunk per
                    // 20ms, no sleep needed and no drift.
                    val read = record.read(chunk, 0, CHUNK_BYTES)
                    if (read <= 0) continue
                    sendTo(deviceId, target, chunk, read)
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
        return null
    }

    fun stopTalking() {
        captureJob?.cancel()
        captureJob = null
    }

    private fun newRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = try {
            @Suppress("MissingPermission") // checked in startTalking
            AudioRecord(
                // VOICE_COMMUNICATION, not MIC: it enables the platform echo
                // canceller and noise suppressor. Without it the badge audio
                // coming out of this phone's speaker is picked straight back up
                // by its mic and sent to the badge, which howls.
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, CHUNK_BYTES * 4)
            )
        } catch (e: IllegalArgumentException) {
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
        val bound = socket ?: return
        val header = JSONObject().apply {
            put("type", "audio")
            put("device_id", deviceId)
            put("sequence", outgoingSequence++)
            put("audio_format", PCM16)
        }.toString().toByteArray(Charsets.UTF_8)

        val frame = ByteArray(2 + header.size + length)
        frame[0] = (header.size shr 8).toByte()
        frame[1] = (header.size and 0xFF).toByte()
        header.copyInto(frame, 2)
        audio.copyInto(frame, 2 + header.size, 0, length)

        try {
            bound.send(DatagramPacket(frame, frame.size, target, AUDIO_PORT))
        } catch (e: IOException) {
            // One lost chunk is 20ms of speech. Log and keep streaming rather
            // than tearing down the whole utterance.
            Log.w(TAG, "chunk to $deviceId dropped", e)
        }
    }
}
