package com.example.viko.data.ai

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.Closeable
import java.io.File
import java.nio.LongBuffer

/**
 * Reads an utterance and says what kind of utterance it is: `SOS`, `BUSY`,
 * `ERROR` or `ROUTINE`.
 *
 * This is not a feature on its own — it exists because [TranslationPipeline]'s
 * checkpoint was fine-tuned on sources shaped `"<TAG> text"`, and the tag is what
 * conditions politeness and register in the Korean it produces. Nobody hands us a
 * tag at inference, so it has to be predicted from the transcript first.
 *
 * Multilingual-MiniLM-L12-H384, one model for both languages: a Korean source and
 * a Vietnamese source go through the same weights.
 *
 * **The output is not safety-grade.** The reference measures 26 of 105 true
 * emergencies missed on the honest split, and `BUSY` rests on 7 test examples.
 * A `SOS` here must never drive an alarm, an e-stop, or anything actuating — it
 * selects a translation register, nothing more.
 *
 * Like the translator, the model lives outside the APK and [isReady] is false
 * until it is pushed. Callers translate untagged rather than not at all.
 */
class TagClassifier(context: Context) : Closeable {

    companion object {
        private const val TAG = "VikoAI"

        /**
         * `max_length` in the reference's tokenizer call, padded rather than
         * truncated to length — the exported graph was traced at this shape.
         */
        private const val SEQUENCE_LENGTH = 64

        /** XLM-Roberta's `<pad>`, from the checkpoint's tokenizer_config.json. */
        private const val PAD = 1L

        /** The tag the app falls back to when nothing can be predicted. It is the
         *  majority class, and the one whose register is the least surprising if
         *  wrong — an emergency mistranslated as routine is bad, but a routine
         *  instruction shouted as an emergency is worse in a factory. */
        const val DEFAULT_TAG = "ROUTINE"

        private val REQUIRED = listOf("model.onnx", "tokenizer.json", "labels.json")

        /**
         * Lowercase and strip punctuation, exactly as the Python reference does
         * before tokenising.
         *
         * **Mandatory, not cosmetic.** The tagger was trained on text put through
         * this same function, because the seed corpus is punctuated and ASR output
         * is not: `!` appears in 100% of SOS training rows and 0% of ROUTINE ones,
         * so it is very nearly a label proxy — and it is absent at inference.
         * Skipping this drops SOS recall from 0.676 to 0.486, and turned
         * "불이야! 대피해!" ("Fire! Evacuate!") into ROUTINE in the reference's
         * own testing.
         *
         * Note it substitutes a space, not an empty string.
         */
        fun asrNormalize(text: String): String =
            text.replace(Regex("[!?.,;:~…\"'()\\[\\]]+"), " ").lowercase().trim()
    }

    private val modelDir = File(context.getExternalFilesDir(null), "models/tagger")

    val isReady: Boolean
        get() = REQUIRED.all { File(modelDir, it).exists() }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val session: OrtSession by lazy {
        env.createSession(
            File(modelDir, "model.onnx").path,
            OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        )
    }

    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(File(modelDir, "tokenizer.json").toPath())
    }

    /**
     * Read from `labels.json` beside the model rather than written as a constant
     * here.
     *
     * The checkpoint's own config.json names its classes `LABEL_0`..`LABEL_3` — it
     * carries no mapping at all — so the order exists only in the export script
     * that produced this file. A constant in Kotlin could drift from it silently,
     * and the symptom would be fluent Korean in the wrong register rather than
     * anything that looks like a bug.
     */
    private val labels: List<String> by lazy {
        val json = JSONArray(File(modelDir, "labels.json").readText())
        List(json.length()) { json.getString(it) }.also {
            require(it.isNotEmpty()) { "labels.json is empty" }
        }
    }

    /**
     * Classifies one utterance. Returns null when the model is missing or
     * inference fails — callers should fall back to [DEFAULT_TAG] or to
     * translating untagged.
     *
     * Blocking, though cheap next to translation (~6ms on CPU in the reference).
     * Call it off the main thread anyway, since it shares a thread with the
     * translator in practice.
     */
    fun classify(text: String): String? {
        if (text.isBlank()) return null
        if (!isReady) {
            Log.i(TAG, "no tagger in $modelDir — translating untagged")
            return null
        }
        return try {
            predict(asrNormalize(text))
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "onnxruntime/tokenizer native libs missing for this ABI", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "tagging failed", e)
            null
        }
    }

    private fun predict(normalized: String): String {
        val encoded = tokenizer.encode(normalized, true, false).ids
        val ids = LongArray(SEQUENCE_LENGTH) { PAD }
        val mask = LongArray(SEQUENCE_LENGTH)
        val used = minOf(encoded.size, SEQUENCE_LENGTH)
        for (i in 0 until used) {
            ids[i] = encoded[i]
            mask[i] = 1L
        }

        val scratch = mutableListOf<OnnxTensor>()
        fun tensor(data: LongArray): OnnxTensor = OnnxTensor
            .createTensor(env, LongBuffer.wrap(data), longArrayOf(1, SEQUENCE_LENGTH.toLong()))
            .also { scratch += it }

        return try {
            session.run(
                mapOf(
                    "input_ids" to tensor(ids),
                    "attention_mask" to tensor(mask),
                    // Required by the graph, and never produced by the tokenizer:
                    // this checkpoint is a BertForSequenceClassification, which takes
                    // segment ids, paired with an XLMRobertaTokenizer, which has no
                    // segment concept. Torch quietly defaults them to zeros; the
                    // exported graph makes them a mandatory input, so omitting this
                    // throws rather than misbehaving.
                    "token_type_ids" to tensor(LongArray(SEQUENCE_LENGTH))
                )
            ).use { result ->
                val logits = result.get(0) as OnnxTensor
                labels[argmax(logits)]
            }
        } finally {
            scratch.forEach { runCatching { it.close() } }
        }
    }

    private fun argmax(logits: OnnxTensor): Int {
        val buffer = logits.floatBuffer
        var best = 0
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in 0 until labels.size) {
            val score = buffer.get(i)
            if (score > bestScore) {
                bestScore = score
                best = i
            }
        }
        return best
    }

    override fun close() {
        if (!isReady) return
        runCatching { session.close() }
    }
}
