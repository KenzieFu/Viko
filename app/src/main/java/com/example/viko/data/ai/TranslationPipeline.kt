package com.example.viko.data.ai

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer

/**
 * Vietnamese <-> Korean text, on the phone, with no network.
 *
 * Runs the team's fine-tuned NLLB-200-distilled-600M. One set of weights serves
 * both directions -- NLLB is multilingual and the direction is only a forced
 * first decoder token -- so a badge speaking Vietnamese and a supervisor
 * answering in Korean share this object.
 *
 * Three graphs, because that is how a seq2seq model with a KV cache exports:
 *
 * - `encoder_model` reads the source sentence, once.
 * - `decoder_model` produces the first token, plus the caches to carry forward.
 * - `decoder_with_past_model` produces every token after that, one at a time.
 *
 * **Input is `"<TAG> text"`**, e.g. `"<SOS> máy ép bị kẹt"`. That is the format
 * this checkpoint was fine-tuned on, and the tag conditions politeness and
 * register in the output. [translate] adds it; callers pass the tag separately
 * rather than pre-formatting, so there is one place that knows the convention.
 *
 * **The models are not in the APK.** They are ~1.8GB, so they live in external
 * files and are pushed by hand with `scripts/push-models.sh`. [isReady] is false
 * until they are there and [translate] returns null, which every caller must
 * handle: a phone with no models is still a working voice link, it just relays
 * speech instead of translating it. That is also the shipping state of the app
 * today.
 *
 * Not thread-safe, deliberately. One utterance is translated at a time on one
 * background thread; locking an [OrtSession] for concurrency a push-to-talk
 * channel never exercises would be cost with no buyer.
 */
class TranslationPipeline(context: Context) : Closeable {

    companion object {
        private const val TAG = "VikoAI"

        /** `decoder_layers` in the checkpoint's config.json. */
        private const val LAYERS = 12

        /**
         * Matches `max_new_tokens` in the Python reference. A shouted instruction
         * is far shorter; this is the runaway-decode guard, not a target.
         */
        private const val MAX_NEW_TOKENS = 200

        /** `max_length` in the reference's tokenizer call. */
        private const val MAX_SOURCE_TOKENS = 256

        /** `</s>` — NLLB uses it as EOS and as the decoder's start token. */
        private const val EOS = 2L

        /** NLLB language codes, keyed by the short codes used across this app. */
        private val LANGUAGE = mapOf("vi" to "vie_Latn", "ko" to "kor_Hang")

        private val REQUIRED = listOf(
            "encoder_model.onnx",
            "decoder_model.onnx",
            "decoder_with_past_model.onnx",
            "tokenizer.json",
            "tokenizer_config.json"
        )
    }

    /**
     * External files rather than `filesDir`: `adb push` writes here without
     * `run-as`, which matters while the models are hand-delivered.
     */
    private val modelDir = File(context.getExternalFilesDir(null), "models/nllb")

    val isReady: Boolean
        get() = REQUIRED.all { File(modelDir, it).exists() }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val options: OrtSession.SessionOptions by lazy {
        // Two threads: the phone is also playing audio and drawing a screen, and a
        // translation that starves the audio thread is worse than a slow one.
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    }

    private val encoder: OrtSession by lazy { session("encoder_model.onnx") }
    private val decoder: OrtSession by lazy { session("decoder_model.onnx") }
    private val decoderWithPast: OrtSession by lazy { session("decoder_with_past_model.onnx") }

    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(File(modelDir, "tokenizer.json").toPath())
    }

    /**
     * `vie_Latn` and `kor_Hang` ids, read from the checkpoint rather than
     * hard-coded. They sit ~256k into the vocabulary and would move the moment
     * anyone prunes it, which is the next size win we expect to take.
     *
     * Getting these wrong fails silently and expensively: the sentencepiece
     * fallback is off by one (`kor_Hang` 256099 against the correct 256098) and
     * translates into a different language entirely rather than erroring.
     */
    private val languageTokens: Map<String, Long> by lazy { readLanguageTokens() }

    private fun session(name: String): OrtSession =
        env.createSession(File(modelDir, name).path, options)

    /**
     * Translates one utterance from [source] to [target] (`"vi"` / `"ko"`).
     *
     * [tag] is the context tag from the classifier — `SOS`, `BUSY`, `ERROR`,
     * `ROUTINE`. Pass null only when no classifier ran; the model expects the
     * prefix and untagged input is off-distribution.
     *
     * Returns null when no models are installed or inference fails, so callers
     * fall back to relaying untranslated speech rather than showing the
     * supervisor nothing.
     *
     * Blocking and slow (hundreds of ms at best). Never call it on the main thread.
     */
    fun translate(text: String, source: String, target: String, tag: String? = null): String? {
        if (text.isBlank()) return null
        require(source != target) { "nothing to translate: $source to $target" }
        val sourceCode = LANGUAGE[source] ?: error("unsupported source language '$source'")
        val targetCode = LANGUAGE[target] ?: error("unsupported target language '$target'")

        if (!isReady) {
            Log.i(TAG, "no models in $modelDir — relaying speech untranslated")
            return null
        }
        val tagged = if (tag != null) "<$tag> $text" else text
        return try {
            decode(tagged, sourceCode, targetCode)
        } catch (e: UnsatisfiedLinkError) {
            // A missing native library for this ABI looks identical to "no models"
            // from the outside, so it is worth naming loudly.
            Log.e(TAG, "onnxruntime/tokenizer native libs missing for this ABI", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "translation failed", e)
            null
        }
    }

    private fun decode(text: String, sourceCode: String, targetCode: String): String {
        // NLLB wants [src_lang] tokens </s>. The special tokens are added here
        // rather than by the tokenizer, because tokenizer.json bakes in whichever
        // source language happened to be set when it was saved — and this object
        // translates in both directions.
        val body = tokenizer.encode(text, false, false).ids
            .take(MAX_SOURCE_TOKENS - 2)
            .toLongArray()
        val source = longArrayOf(languageTokens.getValue(sourceCode)) + body + EOS
        val first = longArrayOf(EOS, languageTokens.getValue(targetCode))
        Log.d(TAG, "src(${source.size}) ${source.take(12).joinToString(",")}")

        val scratch = mutableListOf<OnnxTensor>()
        fun tensor(data: LongArray): OnnxTensor = OnnxTensor
            .createTensor(env, LongBuffer.wrap(data), longArrayOf(1, data.size.toLong()))
            .also { scratch += it }

        val mask = tensor(LongArray(source.size) { 1L })
        val generated = mutableListOf<Long>()

        try {
            // A Result owns its tensors, so one stays open as long as anything
            // downstream still reads from it. The encoder's output is needed only
            // for this one call, so it is closed as soon as the decoder has read it.
            val primed = encoder.run(
                mapOf("input_ids" to tensor(source), "attention_mask" to mask)
            ).use { encoded ->
                decoder.run(
                    mapOf(
                        "input_ids" to tensor(first),
                        "encoder_attention_mask" to mask,
                        "encoder_hidden_states" to encoded.tensor("last_hidden_state")
                    )
                )
            }
            var past: OrtSession.Result = primed

            try {
                var next = argmaxAtLastPosition(primed.tensor("logits"))
                if (next != EOS) generated += next

                while (generated.size < MAX_NEW_TOKENS && next != EOS) {
                    val step = decoderWithPast.run(
                        HashMap<String, OnnxTensor>(4 * LAYERS + 2).apply {
                            put("input_ids", tensor(longArrayOf(next)))
                            put("encoder_attention_mask", mask)
                            for (i in 0 until LAYERS) {
                                // The decoder cache grows every step, so it comes from
                                // the previous step. The encoder cache depends only on
                                // the encoder output — fixed for the whole decode — so
                                // it always comes from the first call. That is not an
                                // optimisation we chose: the step graph consumes
                                // encoder KV but never re-emits it, so carrying it
                                // forward is the only thing that works at all.
                                put("past_key_values.$i.decoder.key", past.tensor("present.$i.decoder.key"))
                                put("past_key_values.$i.decoder.value", past.tensor("present.$i.decoder.value"))
                                put("past_key_values.$i.encoder.key", primed.tensor("present.$i.encoder.key"))
                                put("past_key_values.$i.encoder.value", primed.tensor("present.$i.encoder.value"))
                            }
                        }
                    )
                    next = argmaxAtLastPosition(step.tensor("logits"))
                    if (past !== primed) past.close()
                    past = step
                    if (next != EOS) generated += next
                }
                if (generated.size >= MAX_NEW_TOKENS) {
                    Log.w(TAG, "decode hit the $MAX_NEW_TOKENS token cap without an EOS")
                }
            } finally {
                if (past !== primed) past.close()
                primed.close()
            }
        } finally {
            scratch.forEach { runCatching { it.close() } }
        }

        Log.d(TAG, "gen(${generated.size}) ${generated.joinToString(",")}")
        return tokenizer.decode(generated.toLongArray(), true).trim()
    }

    /** Greedy: the highest-scoring token at the final position. */
    private fun argmaxAtLastPosition(logits: OnnxTensor): Long {
        val shape = logits.info.shape          // [batch, sequence, vocab]
        val vocab = shape[2].toInt()
        val buffer = logits.floatBuffer
        val offset = (shape[1].toInt() - 1) * vocab

        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until vocab) {
            val score = buffer.get(offset + i)
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best.toLong()
    }

    /**
     * Pulls the language ids out of `added_tokens_decoder` in
     * tokenizer_config.json — 39KB, against tokenizer.json's 31MB, so it is cheap
     * enough to parse whole.
     */
    private fun readLanguageTokens(): Map<String, Long> {
        val wanted = LANGUAGE.values.toSet()
        val added = JSONObject(File(modelDir, "tokenizer_config.json").readText())
            .getJSONObject("added_tokens_decoder")

        val found = buildMap {
            added.keys().forEach { id ->
                val content = added.getJSONObject(id).optString("content")
                if (content in wanted) put(content, id.toLong())
            }
        }
        require(found.keys == wanted) {
            "checkpoint is missing language tokens: ${wanted - found.keys}"
        }
        return found
    }

    private fun OrtSession.Result.tensor(name: String): OnnxTensor =
        get(name).orElseThrow { IllegalStateException("model has no output '$name'") } as OnnxTensor

    /** Frees the mapped graphs. Safe to call when nothing was ever loaded. */
    override fun close() {
        if (!isReady) return
        runCatching { encoder.close() }
        runCatching { decoder.close() }
        runCatching { decoderWithPast.close() }
    }
}
