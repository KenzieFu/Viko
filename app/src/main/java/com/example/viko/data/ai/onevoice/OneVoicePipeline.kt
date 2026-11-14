package com.example.viko.data.ai.onevoice

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.example.viko.data.ai.LogMel
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.abs

enum class VoiceDirection(val source: String, val target: String) {
    Worker("vi", "ko"), Manager("ko", "vi")
}

data class OneVoiceResult(
    val transcript: String,
    val tag: String,
    val translation: String,
    val timings: OneVoiceTimings
)

data class OneVoiceTimings(
    val recognitionMs: Long,
    val contextMs: Long = 0,
    val translationMs: Long = 0,
    val cacheHit: Boolean = false
)

/** Runs on the registry's single inference worker. All models are local files. */
class OneVoicePipeline(
    private val root: File,
    private val keepBothRecognizers: () -> Boolean = { false }
) : Closeable {
    private var vi: VietnameseAsr? = null
    private var ko: OfflineRecognizer? = null
    private var tagger: Tagger? = null
    private var translator: Translator? = null
    private val recentTranslations = LruCache<Pair<VoiceDirection, String>, OneVoiceResult>(16)

    fun process(pcm: ByteArray, direction: VoiceDirection): OneVoiceResult? {
        require(pcm.size % 2 == 0 && pcm.size <= 30 * 32_000)
        if (pcm.size < 3_200) return null
        val buf = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        val samples = FloatArray(pcm.size / 2) { buf.short / 32768f }
        if (samples.all { abs(it) < 0.001f }) return null

        val start = SystemClock.elapsedRealtime()
        val text = when (direction) {
            VoiceDirection.Worker -> vietnamese().transcribe(samples)
            VoiceDirection.Manager -> transcribeKorean(samples)
        }.trim()
        val afterAsr = SystemClock.elapsedRealtime()
        if (text.isBlank()) return null
        // A repeated instruction can skip tagging and decoding after ASR has
        // independently produced the same transcript in the same direction.
        val key = direction to text
        recentTranslations.get(key)?.let { cached ->
            Log.i("OneVoice", "${direction.source}->${direction.target} " +
                "asr=${afterAsr - start}ms reused translation")
            return cached.copy(timings = OneVoiceTimings(afterAsr - start, cacheHit = true))
        }
        val tag = tagger().classify(text)
        val afterTag = SystemClock.elapsedRealtime()
        val translated = translator().translate(text, tag, direction)
        val timings = OneVoiceTimings(afterAsr - start, afterTag - afterAsr,
            SystemClock.elapsedRealtime() - afterTag)
        Log.i("OneVoice", "${direction.source}->${direction.target} " +
            "asr=${timings.recognitionMs}ms tag=${timings.contextMs}ms mt=${timings.translationMs}ms")
        return OneVoiceResult(text, tag, translated, timings).also { result ->
            if (result.translation.isNotBlank()) recentTranslations.put(key, result)
        }
    }

    // Called by the same worker that runs inference. Separate steps let a turn
    // waiting for the models run before we start loading the other language.
    internal fun prepareTranslation() {
        translator()
        tagger()
    }

    internal fun prepareWorker() {
        if (ko == null || vi != null || keepBothRecognizers()) vietnamese()
    }

    internal fun prepareManager() {
        if (vi != null && ko == null && keepBothRecognizers()) korean()
    }

    private fun tagger() = tagger ?: Tagger(File(root, "tagger")).also { tagger = it }
    private fun translator() = translator ?: Translator(File(root, "mt")).also { translator = it }

    private fun vietnamese(): VietnameseAsr {
        vi?.let { return it }
        if (ko != null && !keepBothRecognizers()) {
            ko?.release()
            ko = null
        }
        return VietnameseAsr(File(root, "vi")).also { vi = it }
    }

    private fun korean(): OfflineRecognizer {
        ko?.let { return it }
        if (vi != null && !keepBothRecognizers()) {
            vi?.close()
            vi = null
        }
        val dir = File(root, "ko")
        return OfflineRecognizer(
            config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = File(dir, "encoder.onnx").path,
                        decoder = File(dir, "decoder.onnx").path,
                        joiner = File(dir, "joiner.onnx").path
                    ),
                    tokens = File(dir, "tokens.txt").path,
                    numThreads = 4
                ),
                decodingMethod = "modified_beam_search",
                maxActivePaths = 8
            )
        ).also { ko = it }
    }

    private fun transcribeKorean(samples: FloatArray): String {
        val recognizer = korean()
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples.copyOf(samples.size + 20_480), 16_000)
            recognizer.decode(stream)
            recognizer.getResult(stream).tokens.joinToString("").replace('▁', ' ')
        } finally {
            stream.release()
        }
    }

    override fun close() {
        vi?.close()
        ko?.release()
        tagger?.close()
        translator?.close()
        recentTranslations.evictAll()
        vi = null
        ko = null
        tagger = null
        translator = null
    }

    companion object {
        fun directory(context: Context): File =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "models/onevoice")

        // Written last by the exporter, with expected lengths for every file.
        fun installed(root: File): Boolean = runCatching {
            val manifest = JSONObject(File(root, "pack.json").readText())
            if (manifest.getInt("version") != 1) return@runCatching false
            val sizes = manifest.getJSONObject("files")
            REQUIRED.all { path ->
                val file = File(root, path)
                file.isFile && file.length() == sizes.getLong(path)
            }
        }.getOrDefault(false)

        fun canKeepBoth(context: Context): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java)
            if (manager.isLowRamDevice) return false
            val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            return !memory.lowMemory && memory.totalMem >= 7L * 1024 * 1024 * 1024 &&
                memory.availMem >= 1536L * 1024 * 1024
        }

        private val REQUIRED = listOf(
            "vi/encoder.onnx", "vi/decoder.onnx", "vi/shape.json", "vi/mel_filters.bin",
            "vi/generation_config.json", "vi/tokenizer.json",
            "ko/encoder.onnx", "ko/encoder.weights", "ko/decoder.onnx", "ko/joiner.onnx", "ko/tokens.txt",
            "tagger/tagger.onnx", "tagger/tokenizer.json", "tagger/embeddings.f32", "tagger/embeddings.json",
            "mt/encoder.onnx", "mt/decoder.onnx", "mt/shape.json", "mt/tokenizer.json", "mt/tokens.json"
        )
    }
}

private val env: OrtEnvironment get() = OrtEnvironment.getEnvironment()

private fun open(file: File): OrtSession = OrtSession.SessionOptions().use { options ->
    // Keep the MT quantizer and integer matmul separate. This path matches the
    // bundled reference clips with the tested Android ONNX Runtime version.
    if (file.parentFile?.name == "mt") {
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
    }
    options.setIntraOpNumThreads(4)
    options.setInterOpNumThreads(1)
    options.setCPUArenaAllocator(false)
    options.setMemoryPatternOptimization(false)
    options.addConfigEntry("session.intra_op.allow_spinning", "0")
    env.createSession(file.path, options)
}

private fun ids(vararg values: Long) =
    OnnxTensor.createTensor(env, LongBuffer.wrap(values), longArrayOf(1, values.size.toLong()))

private fun File.json(name: String) = JSONObject(File(this, name).readText())
private fun JSONArray.ints() = IntArray(length()) { getInt(it) }
private fun File.tokenizer() = HuggingFaceTokenizer.newInstance(File(this, "tokenizer.json").toPath())
private fun OrtSession.Result.tensor(name: String) = get(name).get() as OnnxTensor

/** Cross attention is calculated once; each decoder call computes only the newest token. */
private class CachedDecoder(dir: File) : Closeable {
    private val shape = dir.json("shape.json")
    private val cacheCount = shape.getInt("layers") * 2
    private val emptyShape = longArrayOf(1, shape.getLong("heads"), 0, shape.getLong("width"))
    private val encoder = open(File(dir, "encoder.onnx"))
    private val decoder = try {
        open(File(dir, "decoder.onnx"))
    } catch (e: Exception) {
        encoder.close()
        throw e
    }

    fun decode(
        inputs: OnnxTensor,
        prefix: List<Int>,
        eos: Int,
        maxNew: Int,
        positionOffset: Int = 0,
        suppress: Set<Int> = emptySet(),
        suppressFirst: Set<Int> = emptySet(),
        noRepeatTrigrams: Boolean = false
    ): LongArray {
        val history = prefix.toMutableList()
        val generated = ArrayList<Long>()
        encoder.run(mapOf("inputs" to inputs)).use { cross ->
            OnnxTensor.createTensor(env, FloatBuffer.allocate(0), emptyShape).use { empty ->
                var past: OrtSession.Result? = null
                try {
                    for (index in 0 until maxNew + prefix.size - 1) {
                        val next = ids(history[index].toLong()).use { token ->
                            ids((index + positionOffset).toLong()).use { position ->
                                decoder.run(buildMap {
                                    put("token", token)
                                    put("position", position)
                                    for (i in 0 until cacheCount) {
                                        put("past_$i", past?.tensor("present_$i") ?: empty)
                                        put("cross_$i", cross.tensor("cross_$i"))
                                    }
                                })
                            }
                        }
                        past?.close()
                        past = next
                        if (index < prefix.lastIndex) continue
                        val banned = if (noRepeatTrigrams) repeated(history) else emptySet()
                        val logits = next.tensor("logits").floatBuffer
                        var best = -1
                        var score = Float.NEGATIVE_INFINITY
                        for (i in 0 until logits.remaining()) {
                            val value = logits[i]
                            if (value <= score || !value.isFinite() || i in suppress || i in banned ||
                                (index == prefix.lastIndex && i in suppressFirst)) continue
                            best = i
                            score = value
                        }
                        check(best >= 0) { "No valid decoder token" }
                        if (best == eos) break
                        generated += best.toLong()
                        history += best
                    }
                } finally {
                    past?.close()
                }
            }
        }
        return generated.toLongArray()
    }

    private fun repeated(ids: List<Int>): Set<Int> = buildSet {
        for (i in 0 until ids.size - 2) {
            if (ids[i] == ids[ids.size - 2] && ids[i + 1] == ids.last()) add(ids[i + 2])
        }
    }

    override fun close() {
        encoder.close()
        decoder.close()
    }
}

private class VietnameseAsr(dir: File) : Closeable {
    private val mel = LogMel(File(dir, "mel_filters.bin"))
    private val generation = dir.json("generation_config.json")
    private val suppress = generation.getJSONArray("suppress_tokens").ints().toSet()
    private val suppressFirst = generation.getJSONArray("begin_suppress_tokens").ints().toSet()
    private val tokenizer = dir.tokenizer()
    private val decoder = CachedDecoder(dir)

    fun transcribe(samples: FloatArray): String {
        // Short PTT calls can use 3.2 seconds of padding. Keep the original
        // window sizing for longer recordings.
        val padding = if (samples.size <= 10 * LogMel.SAMPLE_RATE) 320 else LogMel.PAD_FRAMES
        val frames = mel.framesFor(samples.size, paddingFrames = padding)
        val (text, ended) = transcribe(samples, frames)
        // A shortened window must still let the model see trailing silence.
        // If it cannot finish, retry once with the reference 30-second window.
        return if (frames == LogMel.FRAMES || ended && text.isNotBlank()) text
        else transcribe(samples, LogMel.FRAMES).first
    }

    private fun transcribe(samples: FloatArray, frames: Int): Pair<String, Boolean> {
        val features = mel.extract(samples, frames)
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(features), longArrayOf(1, 80, frames.toLong()))
            .use { input ->
                val tokens = decoder.decode(
                    input, listOf(50258, 50278, 50359, 50363), eos = 50257, maxNew = 220,
                    suppress = suppress, suppressFirst = suppressFirst
                )
                tokenizer.decode(tokens, true) to (tokens.size < 220)
            }
    }

    override fun close() {
        decoder.close()
        tokenizer.close()
    }
}

private class Tagger(dir: File) : Closeable {
    private val tokenizer = dir.tokenizer()
    // The embedding matrix is most of the tagger. Read only the rows in
    // the current utterance, preserving float32 values without loading 380 MB.
    private val shape = dir.json("embeddings.json")
    private val width = shape.getInt("width")
    private val rows = shape.getInt("rows")
    private val table = RandomAccessFile(File(dir, "embeddings.f32"), "r")
    private val session = try {
        open(File(dir, "tagger.onnx"))
    } catch (e: Exception) {
        table.close()
        tokenizer.close()
        throw e
    }
    private val punctuation = Regex("""[!?.,;:~…"'()\[\]]+""")
    private val labels = listOf("SOS", "BUSY", "ERROR", "ROUTINE")

    fun classify(text: String): String {
        val normalized = text.replace(punctuation, " ").lowercase(Locale.ROOT).trim()
        val encoded = tokenizer.encode(normalized).ids
        val length = minOf(64, encoded.size)
        val tokens = LongArray(64) { if (it < length) encoded[it] else 1L }
        if (encoded.size > 64) tokens[63] = 2 // Preserve the closing token after truncation.
        val mask = LongArray(64) { if (it < length) 1L else 0L }
        val values = FloatArray(64 * width)
        val bytes = ByteBuffer.allocate(width * 4).order(ByteOrder.LITTLE_ENDIAN)
        val floats = bytes.asFloatBuffer()
        tokens.forEachIndexed { index, token ->
            require(token in 0 until rows.toLong())
            table.seek(token * width * 4)
            table.readFully(bytes.array())
            floats.rewind()
            floats.get(values, index * width, width)
        }
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(values), longArrayOf(1, 64, width.toLong())).use { input ->
            ids(*mask).use { attention ->
                session.run(mapOf("embeddings" to input, "mask" to attention)).use { output ->
                    val scores = output.tensor("logits").floatBuffer
                    labels[labels.indices.maxBy { scores[it] }]
                }
            }
        }
    }

    override fun close() {
        session.close()
        table.close()
        tokenizer.close()
    }
}

private class Translator(dir: File) : Closeable {
    private val meta = dir.json("tokens.json")
    private val toModel = meta.getJSONArray("to_model").ints()
    private val toPiece = meta.getJSONObject("to_piece")
    private val suppress = meta.getJSONArray("suppress").ints().toSet()
    private val langs = meta.getJSONObject("languages")
    private val tags = meta.getJSONObject("tags")
    private val tokenizer = dir.tokenizer()
    private val decoder = CachedDecoder(dir)

    fun translate(text: String, tag: String, direction: VoiceDirection): String {
        val pieces = tokenizer.encode(text).ids
        val tokens = longArrayOf(langs.getLong(direction.source), tags.getLong("[$tag]"),
            *pieces.map { toModel[it.toInt()].toLong() }.toLongArray(), 2)
        require(tokens.size <= 1024)
        return ids(*tokens).use { input ->
            val output = decoder.decode(
                input, listOf(2, langs.getInt(direction.target)), eos = 2,
                maxNew = minOf(256, 2 * tokens.size + 10) - 1, positionOffset = 2,
                suppress = suppress, noRepeatTrigrams = true
            )
            tokenizer.decode(output.asSequence().mapNotNull { id ->
                val key = id.toString()
                if (toPiece.has(key)) toPiece.getLong(key) else null
            }.toList().toLongArray(), true).trim()
        }
    }

    override fun close() {
        decoder.close()
        tokenizer.close()
    }
}
