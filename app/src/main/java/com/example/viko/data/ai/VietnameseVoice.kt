package com.example.viko.data.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Vietnamese text to 16kHz PCM16, for sending back to a badge.
 *
 * The mirror of [Speaker], and deliberately a separate class, because the two
 * directions are not symmetric. The supervisor's Korean is *played*: hand it to
 * [TextToSpeech.speak] and the platform does the rest. The worker's Vietnamese
 * has to become **bytes** — the badge is on the other end of a UDP socket and
 * expects raw PCM16 at 16kHz — and `synthesizeToFile` is the only route Android
 * offers to those bytes.
 *
 * So this writes a WAV to the cache, waits for it, parses it, resamples it, and
 * hands back samples. That is a lot of machinery for "say this in Vietnamese",
 * and there is no shorter path through the platform API.
 */
class VietnameseVoice(private val context: Context) : Closeable {

    companion object {
        private const val TAG = "VikoAI"

        private val VIETNAMESE = Locale.forLanguageTag("vi-VN")

        /** Must match SAMPLE_RATE in the firmware and [LogMel.SAMPLE_RATE]. */
        const val TARGET_RATE = 16_000

        /**
         * Long enough for a sentence of synthesis on a busy phone. Bounded because
         * `synthesizeToFile` can simply never call back if the engine dies, and a
         * coroutine blocked forever holds an utterance the supervisor thinks was
         * sent.
         */
        private const val SYNTHESIS_TIMEOUT_MS = 10_000L
    }

    sealed interface Status {
        data object Ready : Status
        data object Initialising : Status
        data class Unavailable(val reason: String) : Status
    }

    @Volatile
    var status: Status = Status.Initialising
        private set

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { code ->
            status = if (code != TextToSpeech.SUCCESS) {
                Status.Unavailable("text-to-speech engine failed to start")
            } else {
                when (engine?.setLanguage(VIETNAMESE)) {
                    TextToSpeech.LANG_MISSING_DATA ->
                        Status.Unavailable(noOfflineVoiceMessage("Vietnamese"))
                    TextToSpeech.LANG_NOT_SUPPORTED ->
                        Status.Unavailable("this text-to-speech engine has no Vietnamese voice")
                    null -> Status.Unavailable("engine went away during startup")
                    else -> {
                        // The engine reports vi-VN available on the strength of a
                        // SERVER voice alone. That works on Wi-Fi and fails on the
                        // factory floor, which is the only place it matters.
                        val tts = engine
                        if (tts == null || selectOfflineVoice(tts, VIETNAMESE) == null) {
                            Status.Unavailable(noOfflineVoiceMessage("Vietnamese"))
                        } else {
                            Status.Ready
                        }
                    }
                }
            }
            Log.i(TAG, "tts(vi): $status")
        }
    }

    /**
     * Synthesises [vietnamese] and returns it as 16kHz mono PCM16, or null if
     * anything failed.
     *
     * Blocking, on the order of a second. Call it off the main thread.
     */
    fun synthesise(vietnamese: String): ByteArray? {
        if (vietnamese.isBlank()) return null
        val tts = engine
        if (tts == null || status !is Status.Ready) {
            Log.w(TAG, "cannot synthesise, tts(vi) is $status")
            return null
        }

        val file = File(context.cacheDir, "viko-tts-${System.nanoTime()}.wav")
        return try {
            if (!writeWav(tts, vietnamese, file)) null else readAsPcm16(file)
        } catch (e: Exception) {
            Log.e(TAG, "vietnamese synthesis failed", e)
            null
        } finally {
            file.delete()
        }
    }

    /** Drives `synthesizeToFile` and waits for the callback, or gives up. */
    private fun writeWav(tts: TextToSpeech, text: String, file: File): Boolean {
        val id = "viko-${System.nanoTime()}"
        val done = CountDownLatch(1)
        var ok = false

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) { ok = true; done.countDown() }
            }
            @Deprecated("required by the base class", ReplaceWith(""))
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.countDown()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.w(TAG, "synthesis error $errorCode")
                if (utteranceId == id) done.countDown()
            }
        })

        if (tts.synthesizeToFile(text, null, file, id) != TextToSpeech.SUCCESS) {
            Log.w(TAG, "engine refused the synthesis request")
            return false
        }
        if (!done.await(SYNTHESIS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "synthesis timed out after ${SYNTHESIS_TIMEOUT_MS}ms")
            return false
        }
        return ok && file.length() > 0
    }

    /**
     * Reads the synthesised WAV and returns 16kHz mono PCM16.
     *
     * **Walks the RIFF chunk list rather than skipping a fixed 44 bytes.** The
     * canonical header is 44, but writers routinely insert `LIST`, `fact` or
     * padding chunks before `data` — one of the reference clips in this project
     * does exactly that — and a fixed skip then feeds header bytes in as leading
     * samples. It does not error, it just produces a click and a shifted stream.
     *
     * **The sample rate is read, never assumed.** Google's engine synthesises at
     * whatever rate its voice uses — commonly 22050 or 24000 — while the firmware
     * is pinned to 16000.
     */
    private fun readAsPcm16(file: File): ByteArray? {
        val bytes = file.readBytes()
        if (bytes.size < 12 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") {
            Log.e(TAG, "synthesised file is not RIFF/WAVE")
            return null
        }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        var rate = 0
        var channels = 1
        var bits = 16
        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4)
            val size = buf.getInt(i + 4)
            if (size < 0 || i + 8 + size > bytes.size && id != "data") break
            when (id) {
                "fmt " -> {
                    channels = buf.getShort(i + 10).toInt()
                    rate = buf.getInt(i + 12)
                    bits = buf.getShort(i + 22).toInt()
                }
                "data" -> {
                    val length = minOf(size, bytes.size - (i + 8))
                    if (rate == 0) { Log.e(TAG, "wav has data before fmt"); return null }
                    if (bits != 16) { Log.e(TAG, "wav is ${bits}-bit, expected 16"); return null }
                    Log.i(TAG, "synthesised ${length / 2} samples at ${rate}Hz, ${channels}ch")
                    return resample(toMono(bytes, i + 8, length, channels), rate)
                }
            }
            i += 8 + size + (size and 1)      // chunks are word-aligned
        }
        Log.e(TAG, "synthesised file has no data chunk")
        return null
    }

    /** Averages channels down to one, since the badge plays mono. */
    private fun toMono(bytes: ByteArray, offset: Int, length: Int, channels: Int): ShortArray {
        val buf = ByteBuffer.wrap(bytes, offset, length).order(ByteOrder.LITTLE_ENDIAN)
        val frames = length / 2 / channels.coerceAtLeast(1)
        val out = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += buf.short
            out[f] = (sum / channels).toShort()
        }
        return out
    }

    /**
     * Linear interpolation down to [TARGET_RATE].
     *
     * ponytail: no anti-alias filter, so downsampling folds anything above 8kHz
     * back into the band as a faint roughness. It survives because the badge plays
     * through a small speaker into an 80+ dBA factory and the content is speech,
     * whose energy is nearly all below 4kHz. If the badge ever gets a better
     * speaker, put a low-pass before this.
     */
    private fun resample(samples: ShortArray, sourceRate: Int): ByteArray {
        val out: ShortArray = if (sourceRate == TARGET_RATE) samples else {
            val ratio = sourceRate.toDouble() / TARGET_RATE
            val count = (samples.size / ratio).toInt()
            ShortArray(count) { i ->
                val at = i * ratio
                val lo = at.toInt()
                val hi = (lo + 1).coerceAtMost(samples.size - 1)
                val frac = at - lo
                (samples[lo] + (samples[hi] - samples[lo]) * frac).toInt().toShort()
            }
        }
        val bytes = ByteArray(out.size * 2)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        out.forEach(buf::putShort)
        return bytes
    }

    override fun close() {
        runCatching { engine?.stop() }
        runCatching { engine?.shutdown() }
        engine = null
    }
}
