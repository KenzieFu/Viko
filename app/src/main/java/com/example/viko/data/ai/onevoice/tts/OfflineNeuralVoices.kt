package com.example.viko.data.ai.onevoice.tts

import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import com.example.viko.data.ai.onevoice.VoiceDirection
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.roundToInt

internal class OfflineNeuralVoices(
    private val directory: File,
    private val keepBoth: () -> Boolean
) {
    private val access = Mutex()
    private var korean: OfflineTts? = null
    private var vietnamese: VieNeuTurbo? = null
    private val recent = object : LruCache<Pair<VoiceDirection, String>, ByteArray>(2 * 1024 * 1024) {
        override fun sizeOf(key: Pair<VoiceDirection, String>, value: ByteArray) = value.size
    }

    fun installed(): Boolean = runCatching {
        val manifest = JSONObject(File(directory, "pack.json").readText())
        val files = manifest.getJSONObject("files")
        val embeddings = if (files.has("vieneu/embeddings.i8")) "vieneu/embeddings.i8" else "vieneu/embeddings.f32"
        manifest.getInt("version") == 1 && (required + embeddings).all { name ->
            val file = File(directory, name)
            val expected = files.getLong(name)
            expected > 0 && file.isFile && file.length() == expected
        }
    }.getOrDefault(false)

    fun hasStreamingCodec(): Boolean = runCatching {
        val files = JSONObject(File(directory, "pack.json").readText()).getJSONObject("files")
        listOf("codec_browser_onnx_meta.json", "moss_audio_tokenizer_decode_step.onnx").all { name ->
            val path = "vieneu/$name"
            val size = files.getLong(path)
            size > 0 && File(directory, path).length() == size
        }
    }.getOrDefault(false)

    suspend fun prepare() {
        if (!installed() || !keepBoth()) return
        access.withLock { koreanEngine() }
        yield()
        access.withLock { if (keepBoth()) vietnameseEngine() }
    }

    fun stream(text: String, direction: VoiceDirection) = flow {
        if (text.isBlank()) return@flow
        access.withLock {
            val key = direction to text
            val started = SystemClock.elapsedRealtime()
            recent.get(key)?.let {
                Log.i("VikoNeuralTts", "${direction.target} cached PCM")
                emit(it)
                return@withLock
            }

            val captured = ByteArrayOutputStream()
            var bytes = 0L
            suspend fun chunk(samples: FloatArray, rate: Int) {
                val pcm = pcm16(samples, rate)
                if (pcm.isEmpty()) return
                if (bytes == 0L) Log.i("VikoNeuralTts", "${direction.target} first PCM ${SystemClock.elapsedRealtime() - started}ms")
                bytes += pcm.size
                if (bytes <= CACHE_BYTES) captured.write(pcm)
                emit(pcm)
            }

            when (direction) {
                VoiceDirection.Worker -> {
                    val engine = koreanEngine()
                    val config = GenerationConfig(sid = 0, numSteps = 8, extra = mapOf("lang" to "ko"))
                    val sentences = BreakIterator.getSentenceInstance(Locale.KOREAN)
                    sentences.setText(text)
                    var start = sentences.first()
                    var end = sentences.next()
                    while (end != BreakIterator.DONE) {
                        val sentence = text.substring(start, end).trim()
                        if (sentence.isNotEmpty()) {
                            val audio = engine.generateWithConfig(sentence, config)
                            chunk(audio.samples, audio.sampleRate)
                        }
                        start = end
                        end = sentences.next()
                    }
                }
                VoiceDirection.Manager -> vietnameseEngine().stream(text) { chunk(it, 48_000) }
            }
            Log.i("VikoNeuralTts", "${direction.target} stream complete ${SystemClock.elapsedRealtime() - started}ms, ${bytes / 32_000f}s")
            if (bytes in 1..CACHE_BYTES.toLong()) recent.put(key, captured.toByteArray())
        }
    }.buffer(2).flowOn(Dispatchers.Default)

    private fun koreanEngine(): OfflineTts {
        if (vietnamese != null && !keepBoth()) {
            vietnamese?.close()
            vietnamese = null
        }
        return korean ?: openSupertonic().also { korean = it }
    }

    private fun vietnameseEngine(): VieNeuTurbo {
        if (korean != null && !keepBoth()) {
            korean?.release()
            korean = null
        }
        return vietnamese ?: VieNeuTurbo(File(directory, "vieneu"), hasStreamingCodec()).also { vietnamese = it }
    }

    private fun openSupertonic(): OfflineTts {
        val root = File(directory, "supertonic")
        return OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
            supertonic = OfflineTtsSupertonicModelConfig(
                durationPredictor = File(root, "duration_predictor.int8.onnx").path,
                textEncoder = File(root, "text_encoder.int8.onnx").path,
                vectorEstimator = File(root, "vector_estimator.int8.onnx").path,
                vocoder = File(root, "vocoder.int8.onnx").path,
                ttsJson = File(root, "tts.json").path,
                unicodeIndexer = File(root, "unicode_indexer.bin").path,
                voiceStyle = File(root, "voice.bin").path
            ), numThreads = 4
        )))
    }

    suspend fun close() = withContext(NonCancellable) {
        access.withLock {
            korean?.release()
            korean = null
            vietnamese?.close()
            vietnamese = null
            recent.evictAll()
        }
    }

    companion object {
        private const val CACHE_BYTES = 2 * 1024 * 1024
        private val required = listOf(
            "supertonic/duration_predictor.int8.onnx", "supertonic/text_encoder.int8.onnx",
            "supertonic/vector_estimator.int8.onnx", "supertonic/vocoder.int8.onnx",
            "supertonic/tts.json", "supertonic/unicode_indexer.bin", "supertonic/voice.bin",
            "vieneu/config.json", "vieneu/tokenizer.json", "vieneu/preset.json",
            "vieneu/sea_g2p.bin", "vieneu/vieneu_prefill.onnx",
            "vieneu/vieneu_decode_step.onnx", "vieneu/vieneu_acoustic_cached.onnx",
            "vieneu/vieneu_backbone_shared.data", "vieneu/moss_audio_tokenizer_decode_full.onnx",
            "vieneu/moss_audio_tokenizer_decode_shared.data"
        )

        private fun pcm16(samples: FloatArray, rate: Int): ByteArray {
            require(rate >= 16_000)
            val ratio = rate / 16_000.0
            val length = (samples.size / ratio).toInt()
            val bytes = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (index in 0 until length) {
                val start = index * ratio
                val end = (index + 1) * ratio
                var sum = 0.0
                for (source in start.toInt()..minOf(end.toInt(), samples.lastIndex)) {
                    val overlap = minOf(end, source + 1.0) - maxOf(start, source.toDouble())
                    sum += samples[source] * overlap
                }
                bytes.putShort(((sum / ratio).coerceIn(-1.0, 1.0) * 32767).roundToInt().toShort())
            }
            return bytes.array()
        }
    }
}
