package com.example.viko.data.ai

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File
import java.util.Locale

/**
 * Korean text to Korean speech — the TTS stage of [VoicePipeline].
 *
 * Like [SpeechToText], this ships no model. Every Android phone has a TTS
 * engine, and a Samsung has two (Samsung TTS and Google Speech Services); both
 * carry Korean and both run offline once the voice data is downloaded. That
 * replaces the ~300MB VITS checkpoint outright.
 *
 * The catch is the format. [TextToSpeech.synthesizeToFile] writes a WAV at
 * whatever rate the engine prefers — 24kHz on Google, and vendor engines vary —
 * while the badge only understands PCM16 mono at 16kHz. So every utterance is
 * parsed out of its WAV container and resampled ([wavToPcm16], [resamplePcm16]).
 *
 * **Check [isOffline] before deployment.** A Korean voice that needs the network
 * synthesises perfectly on a desk and produces silence in a factory. The voice
 * data is a one-time download, but it has to happen before the phone is
 * air-gapped.
 */
class PhoneTextToSpeech(private val context: Context) : Closeable {

    private companion object {
        const val TAG = "VikoAI"

        /** Must match the badge's SAMPLE_RATE — see BadgeAudioServer. */
        const val TARGET_RATE = 16_000

        /** Engine init is usually instant; a missing engine never answers at all. */
        const val INIT_TIMEOUT_MS = 5_000L

        /** A shouted instruction is a few seconds. Longer means the engine hung. */
        const val SYNTH_TIMEOUT_MS = 15_000L

        const val UTTERANCE_ID = "viko"
    }

    private val ready = CompletableDeferred<Boolean>()

    private val engine = TextToSpeech(context) { status ->
        ready.complete(status == TextToSpeech.SUCCESS && selectKorean())
    }

    /**
     * False when the chosen Korean voice would phone home. Only meaningful once
     * [synthesize] has been called at least once (the engine has to be up).
     */
    val isOffline: Boolean
        get() = runCatching { engine.voice?.isNetworkConnectionRequired == false }.getOrDefault(false)

    private fun selectKorean(): Boolean {
        val result = runCatching { engine.setLanguage(Locale.KOREAN) }.getOrNull()
        val ok = result == TextToSpeech.LANG_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        if (!ok) Log.e(TAG, "no Korean voice installed (setLanguage returned $result)")
        return ok
    }

    /**
     * Synthesises one Korean sentence and returns it as PCM16 mono at 16kHz,
     * ready for `BadgeAudioServer.sendUtterance`. Null when the engine is
     * missing, has no Korean, or fails — callers stay quiet rather than sending
     * the badge noise.
     *
     * ponytail: one WAV round-trip through the cache per utterance, rather than
     * streaming synthesis through a ParcelFileDescriptor pipe. A sentence-long
     * file is written and read in a few ms; swap to the pipe if the pause before
     * the badge speaks ever measures as the bottleneck.
     */
    suspend fun synthesize(korean: String): ByteArray? {
        if (korean.isBlank()) return null
        if (withTimeoutOrNull(INIT_TIMEOUT_MS) { ready.await() } != true) {
            Log.e(TAG, "TTS engine unavailable — badge gets no translated audio")
            return null
        }

        val wav = File(context.cacheDir, "tts.wav")
        val done = CompletableDeferred<Boolean>()
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) { done.complete(true) }

            @Deprecated("Superseded by onError(String, Int)", ReplaceWith(""))
            override fun onError(utteranceId: String?) { done.complete(false) }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "synthesis failed (code $errorCode)")
                done.complete(false)
            }
        })

        val queued = engine.synthesizeToFile(korean, null, wav, UTTERANCE_ID)
        if (queued != TextToSpeech.SUCCESS) {
            Log.e(TAG, "engine refused the utterance")
            return null
        }
        if (withTimeoutOrNull(SYNTH_TIMEOUT_MS) { done.await() } != true) return null

        return withContext(Dispatchers.IO) {
            val (pcm, rate) = runCatching { wavToPcm16(wav.readBytes()) }.getOrNull()
                ?: return@withContext null.also { Log.e(TAG, "engine wrote a WAV we cannot read") }
            resamplePcm16(pcm, rate, TARGET_RATE)
        }
    }

    override fun close() {
        runCatching { engine.stop() }
        runCatching { engine.shutdown() }
    }
}

/**
 * Pulls mono PCM16 and its sample rate out of a RIFF/WAVE file.
 *
 * Chunks are walked rather than assuming audio starts at byte 44: engines are
 * free to emit `LIST`, `fact` or padding chunks first, and a hard-coded offset
 * turns those extra bytes into a burst of static at the front of every
 * sentence. Stereo is downmixed; anything that is not 16-bit PCM returns null.
 */
internal fun wavToPcm16(wav: ByteArray): Pair<ByteArray, Int>? {
    if (wav.size < 12) return null
    if (wav.tag(0) != "RIFF" || wav.tag(8) != "WAVE") return null

    var rate = 0
    var channels = 1
    var bits = 16
    var offset = 12

    while (offset + 8 <= wav.size) {
        val id = wav.tag(offset)
        val size = wav.int32(offset + 4)
        val body = offset + 8
        if (size < 0 || body + size > wav.size) return null

        when (id) {
            "fmt " -> {
                if (size < 16) return null
                channels = wav.int16(body + 2)
                rate = wav.int32(body + 4)
                bits = wav.int16(body + 14)
            }
            "data" -> {
                if (rate <= 0 || bits != 16 || channels !in 1..2) return null
                val pcm = wav.copyOfRange(body, body + size)
                return (if (channels == 2) downmix(pcm) else pcm) to rate
            }
        }
        // Chunks are word-aligned: an odd size is followed by a pad byte.
        offset = body + size + (size and 1)
    }
    return null
}

/** Linear interpolation between neighbouring samples.
 *
 * ponytail: no anti-aliasing filter on the way down from 24kHz, so the top of
 * the band folds back as a faint hiss. Inaudible against 80dBA of factory floor
 * through a badge speaker. Add a proper low-pass if the audio ever has to sound
 * good rather than be understood.
 */
internal fun resamplePcm16(pcm: ByteArray, from: Int, to: Int): ByteArray {
    if (from == to || from <= 0) return pcm
    val inCount = pcm.size / 2
    if (inCount == 0) return ByteArray(0)

    val outCount = (inCount.toLong() * to / from).toInt()
    if (outCount == 0) return ByteArray(0)

    val out = ByteArray(outCount * 2)
    val step = inCount.toDouble() / outCount
    for (i in 0 until outCount) {
        val position = i * step
        val left = position.toInt().coerceAtMost(inCount - 1)
        val right = (left + 1).coerceAtMost(inCount - 1)
        val fraction = position - left
        val value = pcm.sample(left) * (1 - fraction) + pcm.sample(right) * fraction
        out.putSample(i, value.toInt())
    }
    return out
}

private fun downmix(stereo: ByteArray): ByteArray {
    val frames = stereo.size / 4
    val mono = ByteArray(frames * 2)
    for (i in 0 until frames) {
        mono.putSample(i, (stereo.sample(i * 2) + stereo.sample(i * 2 + 1)) / 2)
    }
    return mono
}

private fun ByteArray.tag(at: Int) = String(this, at, 4, Charsets.US_ASCII)

private fun ByteArray.int16(at: Int) =
    (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

private fun ByteArray.int32(at: Int) =
    int16(at) or (int16(at + 2) shl 16)

/** PCM16 is little-endian and signed. */
private fun ByteArray.sample(index: Int) = int16(index * 2).toShort().toInt()

private fun ByteArray.putSample(index: Int, value: Int) {
    val clipped = value.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
    this[index * 2] = (clipped and 0xFF).toByte()
    this[index * 2 + 1] = ((clipped shr 8) and 0xFF).toByte()
}
