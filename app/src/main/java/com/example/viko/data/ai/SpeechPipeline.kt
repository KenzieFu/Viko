package com.example.viko.data.ai

import android.content.Context
import android.util.Log
import java.io.Closeable

/**
 * One utterance of speech in, translated text out.
 *
 * The port of `Cascade.run()` from the reference repo, and the only place that
 * knows the stages go in this order:
 *
 * ```
 * audio -> ASR -> asr_normalize -> tagger -> "<TAG> text" -> MT -> text
 * ```
 *
 * Direction is a parameter rather than a fixture: a badge speaking Vietnamese and
 * a supervisor answering in Korean both come through here, sharing one tagger and
 * one set of MT weights.
 *
 * **Every stage is optional at runtime.** The models are pushed by hand and any of
 * them may be missing, so a stage that is not ready is skipped rather than fatal:
 * no ASR means no transcript at all, and no tagger only means MT sees untagged
 * text. The caller decides what a partial result is worth — for the badge channel
 * it means relaying speech untranslated, which is still a working voice link.
 *
 * **The ASR models are lazy**, as they are in the reference. A phone that only
 * ever hears badge audio never maps the Korean model's 451MB.
 */
class SpeechPipeline(
    private val context: Context,
    private val tagger: TagClassifier,
    private val translator: TranslationPipeline
) : Closeable {

    companion object {
        private const val TAG = "VikoAI"

        /**
         * Utterances to remember. Everything after ASR is a pure function of the
         * transcript, and a hit skips the tagger plus MT — the larger share of the
         * cost. Real factory speech repeats constantly ("dừng lại", "멈춰"), which
         * is the case this exists for.
         *
         * The reference notes its own hit rate is unmeasured, because the seed
         * corpora were built for diversity and contain no repeats. [hits] and
         * [misses] are exposed so it can be judged on real traffic rather than
         * assumed.
         */
        private const val CACHE_SIZE = 256

        /**
         * RMS below which an utterance is treated as silence and not transcribed.
         *
         * Whisper does not return an empty string for silence — it invents. Fed one
         * second of zeros this build produced
         * *"một tình yêu thích nhất là tình yêu thích nhất là tình yêu."*, which
         * would then be tagged, translated and spoken aloud to a supervisor as
         * though a worker had said it. Inventing words nobody said is the worst
         * failure this pipeline has, so it is cut off before ASR rather than
         * filtered after.
         *
         * Set low on purpose. This catches a dead mic, a badge that sent header-only
         * frames, or a button tapped in silence — **not** a quiet voice under
         * factory noise. On an 80+ dBA floor almost anything real clears it, and
         * missing genuine speech is far worse than transcribing noise.
         */
        private const val SILENCE_RMS = 200.0

        /** Root-mean-square amplitude of little-endian PCM16, 0..32767. */
        fun rms(pcm: ByteArray, length: Int = pcm.size): Double {
            val samples = length / 2
            if (samples == 0) return 0.0
            var sum = 0.0
            for (i in 0 until samples) {
                val v = ((pcm[i * 2 + 1].toInt() shl 8) or (pcm[i * 2].toInt() and 0xFF)).toShort().toInt()
                sum += v.toDouble() * v
            }
            return kotlin.math.sqrt(sum / samples)
        }
    }

    /** What one utterance produced. Any field may be null if its stage was skipped. */
    data class Result(
        val transcript: String?,
        val tag: String?,
        val translation: String?,
        val sourceLanguage: String,
        val targetLanguage: String,
        val fromCache: Boolean = false
    ) {
        /** True when there is something worth showing the supervisor. */
        val hasText: Boolean get() = !transcript.isNullOrBlank()
    }

    private val asr = mutableMapOf<String, WhisperAsr>()

    /**
     * LRU over the normalised transcript. `LinkedHashMap` in access order is the
     * whole implementation — the reference uses an `OrderedDict` with
     * `move_to_end`, which is the same thing.
     */
    private val cache = object : LinkedHashMap<String, Pair<String?, String?>>(
        CACHE_SIZE, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String?, String?>>) =
            size > CACHE_SIZE
    }

    var hits = 0
        private set
    var misses = 0
        private set

    /** Built on first use, so an unused direction costs nothing. */
    @Synchronized
    private fun asrFor(language: String): WhisperAsr =
        asr.getOrPut(language) { WhisperAsr(context, language) }

    /** Whether anything at all can be done for this direction. */
    fun isReady(sourceLanguage: String): Boolean = asrFor(sourceLanguage).isReady

    /**
     * Runs one utterance of 16kHz mono PCM16 through the cascade.
     *
     * Blocking and slow — roughly 3.3s on a mid-range phone. Call it from a
     * background coroutine, never the main thread and never the audio thread.
     */
    fun process(
        pcm: ByteArray,
        length: Int = pcm.size,
        sourceLanguage: String,
        targetLanguage: String
    ): Result {
        val empty = Result(null, null, null, sourceLanguage, targetLanguage)

        val level = rms(pcm, length)
        if (level < SILENCE_RMS) {
            Log.i(TAG, "utterance is silence (rms ${"%.0f".format(level)}), not transcribing")
            return empty
        }

        val transcript = asrFor(sourceLanguage).transcribe(pcm, length)
        if (transcript.isNullOrBlank()) return empty

        // Keyed on the normalised transcript, matching the reference: the tagger
        // normalises internally anyway, and two utterances differing only in
        // punctuation are the same utterance as far as everything downstream goes.
        val key = TagClassifier.asrNormalize(transcript)
        cache[key]?.let { (tag, translation) ->
            hits++
            return Result(transcript, tag, translation, sourceLanguage, targetLanguage, fromCache = true)
        }
        misses++

        // A missing tagger is survivable — MT was fine-tuned on tagged input and
        // does worse without it, but "worse" beats "nothing".
        val tag = tagger.classify(transcript)
        if (tag == null) Log.i(TAG, "no tag for this utterance — translating untagged")

        val translation = translator.translate(transcript, sourceLanguage, targetLanguage, tag)
        cache[key] = tag to translation
        return Result(transcript, tag, translation, sourceLanguage, targetLanguage)
    }

    /** Hits and misses, for judging whether the cache earns its place on real traffic. */
    fun cacheStats(): String {
        val total = hits + misses
        val rate = if (total == 0) 0 else 100 * hits / total
        return "cache ${cache.size}/$CACHE_SIZE, $hits hits / $misses misses ($rate%)"
    }

    override fun close() {
        asr.values.forEach { runCatching { it.close() } }
        asr.clear()
    }
}
