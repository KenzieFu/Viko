package com.example.viko.data.ai

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Speech to text on the phone, for one language.
 *
 * Wraps a fine-tuned Whisper-small: `whisper-vi-all` for the badge's Vietnamese,
 * `whisper-ko-v8` for the supervisor's Korean. The two checkpoints are
 * architecturally identical — 768 d_model, 12+12 layers, vocab 51865 — so this
 * is one class instantiated twice, differing only in which directory it loads
 * and which language token it forces.
 *
 * **The language is always forced, never detected.** The direction is known from
 * pairing, and the reference is emphatic about this: letting Whisper auto-detect
 * makes it hallucinate on Vietnamese and Korean. The prefix is built here rather
 * than read from `generation_config.json`, because the fine-tuned checkpoints
 * ship one with `lang_to_id` missing.
 *
 * Like the other stages the model lives outside the APK and [isReady] is false
 * until it is pushed, in which case [transcribe] returns null and the caller
 * relays speech rather than transcribing it.
 *
 * Not thread-safe: one utterance at a time, on one background thread.
 */
class WhisperAsr(context: Context, private val language: String) : Closeable {

    companion object {
        private const val TAG = "VikoAI"

        /** Overridable so the bench can sweep it. */
        var THREADS = 4

        /** `decoder_layers` in the checkpoint's config.json. */
        private const val LAYERS = 12

        /**
         * Long enough for a shouted instruction — the reference uses 220 and the
         * fixture transcript is ~30 tokens. A decode that runs away is a bug.
         */
        private const val MAX_NEW_TOKENS = 220

        /** From the checkpoint's tokenizer.json. */
        private const val SOT = 50258L              // <|startoftranscript|>
        private const val TRANSCRIBE = 50359L       // <|transcribe|>
        private const val NO_TIMESTAMPS = 50363L    // <|notimestamps|>
        private const val EOT = 50257L              // <|endoftext|>

        private val LANGUAGE_TOKEN = mapOf("vi" to 50278L, "ko" to 50264L)

        /**
         * Timestamp tokens start here. With `<|notimestamps|>` forced the model
         * should never emit one, but a quantised model occasionally argmaxes into
         * this range and the decode then never terminates.
         */
        private const val TIMESTAMP_BEGIN = 50364L

        /** How many tokens must repeat back-to-back before a decode is called stuck. */
        private const val LOOP_WINDOW = 12

        private val REQUIRED = listOf(
            "encoder_model.onnx",
            "decoder_model.onnx",
            "decoder_with_past_model.onnx",
            "tokenizer.json",
            "mel_filters.bin"
        )

        /** Model directory per language, under the app's external files. */
        fun directory(context: Context, language: String): File =
            File(context.getExternalFilesDir(null), "models/whisper-$language")
    }

    private val modelDir = directory(context, language)

    private val languageToken = LANGUAGE_TOKEN[language]
        ?: error("unsupported ASR language '$language'")

    val isReady: Boolean
        get() = REQUIRED.all { File(modelDir, it).exists() }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val options: OrtSession.SessionOptions by lazy {
        // Four, not two. The encoder is 85% of ASR and it is pure matmul over a
        // fixed 1500-position sequence, so it parallelises well on this 8-core part.
        // Left short of the core count on purpose: the phone is also playing badge
        // audio, and starving that thread is worse than a slower transcript.
        OrtSession.SessionOptions().apply { setIntraOpNumThreads(THREADS) }
    }

    private val encoder: OrtSession by lazy { session("encoder_model.onnx") }
    private val decoder: OrtSession by lazy { session("decoder_model.onnx") }
    private val decoderWithPast: OrtSession by lazy { session("decoder_with_past_model.onnx") }

    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(File(modelDir, "tokenizer.json").toPath())
    }

    /** Public so callers can build features once and feed two models if they ever need to. */
    val mel: LogMel by lazy { LogMel(File(modelDir, "mel_filters.bin")) }

    private fun session(name: String): OrtSession =
        env.createSession(File(modelDir, name).path, options)

    /**
     * Transcribes one utterance of 16kHz mono PCM16, as the badge sends it.
     *
     * Returns null when the model is missing or inference fails. Blocking and
     * slow — never call it on the main thread.
     */
    fun transcribe(pcm: ByteArray, length: Int = pcm.size): String? {
        if (length < 2) return null
        if (!isReady) {
            Log.i(TAG, "no $language ASR in $modelDir — relaying speech untranscribed")
            return null
        }
        return try {
            // Only as much window as was actually spoken. The encoder is quadratic
            // in sequence length and 85% of ASR cost, so feeding it 30s of silence
            // for a 4s utterance is the most expensive thing available here.
            val frames = mel.framesFor(length / 2)
            decode(mel.fromPcm16(pcm, length, frames), frames)
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "onnxruntime/tokenizer native libs missing for this ABI", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "$language transcription failed", e)
            null
        }
    }

    /** For callers that already hold float samples — the bench harness, mostly. */
    fun transcribe(audio: FloatArray): String? {
        if (!isReady) return null
        val frames = mel.framesFor(audio.size)
        return runCatching { decode(mel.extract(audio, frames), frames) }
            .onFailure { Log.e(TAG, "$language transcription failed", it) }
            .getOrNull()
    }

    private fun decode(features: FloatArray, frames: Int): String {
        val prefix = longArrayOf(SOT, languageToken, TRANSCRIBE, NO_TIMESTAMPS)

        val scratch = mutableListOf<OnnxTensor>()
        fun ids(data: LongArray, shape: LongArray): OnnxTensor = OnnxTensor
            .createTensor(env, LongBuffer.wrap(data), shape)
            .also { scratch += it }

        val generated = mutableListOf<Long>()

        val startedAt = System.currentTimeMillis()
        var encodedAt = startedAt

        try {
            val hidden = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(features),
                longArrayOf(1, LogMel.MELS.toLong(), frames.toLong())
            ).use { input ->
                encoder.run(mapOf("input_features" to input))
            }
            encodedAt = System.currentTimeMillis()

            val primed = hidden.use { encoded ->
                decoder.run(
                    mapOf(
                        "input_ids" to ids(prefix, longArrayOf(1, prefix.size.toLong())),
                        "encoder_hidden_states" to encoded.tensor("last_hidden_state")
                    )
                )
            }
            var past: OrtSession.Result = primed

            try {
                var next = argmaxAtLastPosition(primed.tensor("logits"))
                if (next != EOT) generated += next

                while (generated.size < MAX_NEW_TOKENS && next != EOT) {
                    // Whisper's step graph wants `cache_position` — the index this
                    // token occupies in the cache — where NLLB's wanted an encoder
                    // attention mask. It is the prefix length plus how much has been
                    // generated, and getting it wrong corrupts the positional
                    // embedding rather than throwing.
                    val position = (prefix.size + generated.size - 1).toLong()
                    val step = decoderWithPast.run(
                        HashMap<String, OnnxTensor>(4 * LAYERS + 2).apply {
                            put("input_ids", ids(longArrayOf(next), longArrayOf(1, 1)))
                            put("cache_position", ids(longArrayOf(position), longArrayOf(1)))
                            for (i in 0 until LAYERS) {
                                // Decoder cache grows each step; encoder cache is fixed
                                // for the whole decode and comes from the first call,
                                // which the step graph consumes but never re-emits.
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

                    // With <|notimestamps|> forced these should not appear, but an
                    // int8 model can argmax into the timestamp range, and a decode
                    // that keeps emitting them never reaches EOT.
                    if (next >= TIMESTAMP_BEGIN) {
                        Log.w(TAG, "$language decode emitted a timestamp token ($next), stopping")
                        break
                    }
                    if (next != EOT) generated += next

                    // Defence in depth against the failure the padding rule exists
                    // to prevent: with too little trailing silence Whisper loops a
                    // sentence forever instead of emitting EOT. LogMel.PAD_FRAMES
                    // fixes the cause; this bounds the damage if some future audio
                    // trips it anyway, since a runaway costs ~9s and emits garbage.
                    if (repeats(generated)) {
                        Log.w(TAG, "$language decode looping, stopping at ${generated.size} tokens")
                        generated.subList(generated.size - LOOP_WINDOW, generated.size).clear()
                        break
                    }
                }
                if (generated.size >= MAX_NEW_TOKENS) {
                    Log.w(TAG, "$language decode hit the $MAX_NEW_TOKENS token cap without EOT")
                }
            } finally {
                if (past !== primed) past.close()
                primed.close()
            }
        } finally {
            scratch.forEach { runCatching { it.close() } }
        }

        // Split because the two scale differently: the encoder always chews the full
        // 30s window regardless of how long anyone actually spoke, while decoding
        // costs one pass per emitted token. Which one dominates decides whether a
        // shorter-window re-export is worth doing.
        val done = System.currentTimeMillis()
        Log.i(
            TAG,
            "$language asr[$frames frames]: encoder ${encodedAt - startedAt}ms, " +
                "decode ${done - encodedAt}ms over ${generated.size} tokens " +
                "(${(done - encodedAt) / generated.size.coerceAtLeast(1)}ms/token)"
        )
        Log.d(TAG, "$language asr gen(${generated.size}) ${generated.take(16).joinToString(",")}")
        return tokenizer.decode(generated.toLongArray(), true).trim()
    }

    /**
     * True when the last [LOOP_WINDOW] tokens exactly repeat the block before them.
     *
     * Exact repetition rather than a similarity score: real speech does repeat
     * ("dừng lại, dừng lại"), and a fuzzy test would truncate that. Twelve
     * identical tokens back to back is a decoder that has stopped listening.
     */
    private fun repeats(generated: List<Long>): Boolean {
        if (generated.size < LOOP_WINDOW * 2) return false
        val n = generated.size
        for (i in 0 until LOOP_WINDOW) {
            if (generated[n - 1 - i] != generated[n - 1 - i - LOOP_WINDOW]) return false
        }
        return true
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

    private fun OrtSession.Result.tensor(name: String): OnnxTensor =
        get(name).orElseThrow { IllegalStateException("model has no output '$name'") } as OnnxTensor

    override fun close() {
        if (!isReady) return
        runCatching { encoder.close() }
        runCatching { decoder.close() }
        runCatching { decoderWithPast.close() }
    }
}
