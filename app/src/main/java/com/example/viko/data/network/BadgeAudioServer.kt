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
 * **Where translation goes.** Received audio is played as-is, so the supervisor
 * hears the worker live. That is a working voice link, not a translated one:
 * the ASR/NMT/TTS pipeline does not exist yet. When it does, it belongs between
 * [onChunkReceived] and [play] — feed the incoming chunk to ASR, and send the
 * TTS output back with [sendTo] instead of pushing the phone mic. The transport
 * needs no changes for that; both directions already carry arbitrary PCM16.
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
    }

    private val appContext = context.applicationContext

    /** Last known IP per `device_id`. Rewritten whenever a badge is heard from. */
    private val badgeAddresses = ConcurrentHashMap<String, InetAddress>()

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

        val audioLength = length - audioOffset
        if (audioLength <= 0) return
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
