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
 * Vietnamese text to Korean text, on the phone, with no network.
 *
 * Runs the team's fine-tuned NLLB-200-distilled-600M, exported to ONNX and
 * dynamically quantised to int8. Three graphs, because that is how a seq2seq
 * model with a KV cache exports:
 *
 * - `encoder_model` — the Vietnamese sentence, once.
 * - `decoder_model` — the first Korean token, and the cache to carry forward.
 * - `decoder_with_past_model` — every token after that, one at a time.
 *
 * **The models are not in the APK.** They are ~1.8GB, which no store will carry
 * and no `assets/` should hold, so they live in external files and are pushed by
 * hand:
 *
 * ```
 * adb push nllb-onnx-int8/. \
 *   /sdcard/Android/data/com.example.viko/files/models/nllb/
 * ```
 *
 * [isReady] is false until they are there and [translate] returns null, which
 * every caller must handle: a phone with no models is still a working voice
 * link, it just relays speech instead of translating it. That is also the
 * shipping state of the app today — nothing here runs unless someone has pushed
 * a model.
 *
 * Not thread-safe, and deliberately so: one utterance is translated at a time,
 * on one background thread. Sharing an [OrtSession] across concurrent callers
 * would need locking that a push-to-talk channel never actually exercises.
 */
class TranslationPipeline(context: Context) : Closeable {

    private companion object {
        const val TAG = "VikoAI"

        /** `decoder_layers` in the model's config.json. */
        const val LAYERS = 12

        /** Long enough for a shouted instruction; a runaway decode is a bug. */
        const val MAX_NEW_TOKENS = 48

        /** `</s>`, which NLLB uses as both EOS and the decoder's start token. */
        const val EOS = 2L

        val REQUIRED = listOf(
            "encoder_model.onnx",
            "decoder_model.onnx",
            "decoder_with_past_model.onnx",
            "tokenizer.json",
            "tokenizer_config.json"
        )
    }

    /**
     * External files rather than `filesDir`: `adb push` can write here without
     * `run-as`, which matters while the models are hand-delivered.
     */
    private val modelDir = File(context.getExternalFilesDir(null), "models/nllb")

    val isReady: Boolean
        get() = REQUIRED.all { File(modelDir, it).exists() }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val options: OrtSession.SessionOptions by lazy {
        // Two threads: the phone is also playing audio and drawing a screen, and
        // a translation that starves the audio thread is worse than a slow one.
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
    }

    private val encoder: OrtSession by lazy { session("encoder_model.onnx") }
    private val decoder: OrtSession by lazy { session("decoder_model.onnx") }
    private val decoderWithPast: OrtSession by lazy { session("decoder_with_past_model.onnx") }

    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(File(modelDir, "tokenizer.json").toPath())
    }

    /**
     * `vie_Latn` and `kor_Hang` token ids, read from the checkpoint instead of
     * hard-coded. They are ~256k into the vocabulary and would shift the moment
     * anyone prunes it, which is exactly the change we expect to make next.
     */
    private val languageTokens: Map<String, Long> by lazy { readLanguageTokens() }

    private fun session(name: String): OrtSession =
        env.createSession(File(modelDir, name).path, options)

    /**
     * Translates one utterance. Returns null when no models are installed, or
     * when inference fails — callers fall back to relaying untranslated speech
     * rather than showing the supervisor nothing.
     *
     * Blocking and slow (hundreds of ms). Call it off the main thread.
     */
    fun translate(vietnamese: String): String? {
        if (vietnamese.isBlank()) return null
        if (!isReady) {
            Log.i(TAG, "no models in $modelDir — relaying speech untranslated")
            return null
        }
        return try {
            runCatching { decode(vietnamese) }.getOrElse {
                Log.e(TAG, "translation failed", it)
                null
            }
        } catch (e: UnsatisfiedLinkError) {
            // Missing native library for this ABI — worth naming loudly, since
            // it looks identical to "no models" from the outside.
            Log.e(TAG, "onnxruntime/tokenizer native libs missing", e)
            null
        }
    }

    private fun decode(vietnamese: String): String {
        // NLLB wants [src_lang] tokens </s>. Special tokens are added here rather
        // than by the tokenizer, because tokenizer.json bakes in whichever source
        // language was set when it was saved.
        val source = longArrayOf(
            languageTokens.getValue("vie_Latn")
        ) + tokenizer.encode(vietnamese, false, false).ids + EOS

        val target = languageTokens.getValue("kor_Hang")
        val mask = LongArray(source.size) { 1L }

        val tensors = mutableListOf<OnnxTensor>()
        fun tensor(data: LongArray, rows: Int = 1): OnnxTensor =
            OnnxTensor.createTensor(
                env, LongBuffer.wrap(data), longArrayOf(rows.toLong(), (data.size / rows).toLong())
            ).also { tensors += it }

        val encoderMask = tensor(mask)
        val generated = mutableListOf<Long>()

        // Results own their tensors, so a result stays open for as long as
        // anything downstream still reads from it.
        var first: OrtSession.Result? = null
        var past: OrtSession.Result? = null

        try {
            val hidden = encoder.run(
                mapOf("input_ids" to tensor(source), "attention_mask" to encoderMask)
            )
            first = decoder.run(
                mapOf(
                    "input_ids" to tensor(longArrayOf(EOS, target), rows = 1),
                    "encoder_attention_mask" to encoderMask,
                    "encoder_hidden_states" to hidden.tensor("last_hidden_state")
                )
            )
            hidden.close()

            var next = argmaxLastPosition(first.tensor("logits"))
            if (next != EOS) generated += next
            past = first

            while (generated.size < MAX_NEW_TOKENS && next != EOS) {
                val inputs = HashMap<String, OnnxTensor>(4 * LAYERS + 2)
                inputs["input_ids"] = tensor(longArrayOf(next))
                inputs["encoder_attention_mask"] = encoderMask
                repeat(LAYERS) { i ->
                    // Decoder cache grows every step; the encoder cache is
                    // computed once and reused, so it always comes from `first`.
                    inputs["past_key_values.$i.decoder.key"] = past!!.tensor("present.$i.decoder.key")
                    inputs["past_key_values.$i.decoder.value"] = past!!.tensor("present.$i.decoder.value")
                    inputs["past_key_values.$i.encoder.key"] = first.tensor("present.$i.encoder.key")
                    inputs["past_key_values.$i.encoder.value"] = first.tensor("present.$i.encoder.value")
                }
                val step = decoderWithPast.run(inputs)
                next = argmaxLastPosition(step.tensor("logits"))
                if (past !== first) past!!.close()
                past = step
                if (next != EOS) generated += next
            }
        } finally {
            if (past !== first) past?.close()
            first?.close()
            tensors.forEach { runCatching { it.close() } }
        }

        return tokenizer.decode(generated.toLongArray(), true).trim()
    }

    /** Greedy: the highest-scoring token at the final position. */
    private fun argmaxLastPosition(logits: OnnxTensor): Long {
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
     * Pulls language token ids out of `added_tokens_decoder` in
     * tokenizer_config.json — 39KB, unlike the 31MB tokenizer.json, so this is
     * cheap enough to parse whole.
     */
    private fun readLanguageTokens(): Map<String, Long> {
        val wanted = setOf("vie_Latn", "kor_Hang")
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

    /** Frees ~1.8GB of mapped graphs. Safe to call when nothing was loaded. */
    override fun close() {
        runCatching { if (isReady) { encoder.close(); decoder.close(); decoderWithPast.close() } }
    }
}
