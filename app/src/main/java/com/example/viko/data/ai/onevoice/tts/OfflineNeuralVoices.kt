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
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal class OfflineNeuralVoices(private val directory: File) : Closeable {
    private var korean: OfflineTts? = null
    private var vietnamese: VieNeuTurbo? = null
    private val recent = object : LruCache<Pair<VoiceDirection, String>, ByteArray>(2 * 1024 * 1024) {
        override fun sizeOf(key: Pair<VoiceDirection, String>, value: ByteArray) = value.size
    }

    fun installed(): Boolean = runCatching {
        val manifest = JSONObject(File(directory, "pack.json").readText())
        val files = manifest.getJSONObject("files")
        manifest.getInt("version") == 1 && required.all { name ->
            val file = File(directory, name)
            val expected = files.getLong(name)
            expected > 0 && file.isFile && file.length() == expected
        }
    }.getOrDefault(false)

    @Synchronized
    fun synthesise(text: String, direction: VoiceDirection): ByteArray? {
        if (text.isBlank()) return null
        val key = direction to text
        recent.get(key)?.let { return it }
        val start = SystemClock.elapsedRealtime()
        // Keep only one voice loaded. Recognition and translation also need native memory.
        val pcm = when (direction) {
            VoiceDirection.Worker -> {
                vietnamese?.close()
                vietnamese = null
                val engine = korean ?: openSupertonic().also { korean = it }
                val generated = engine.generateWithConfig(
                    text, GenerationConfig(sid = 0, numSteps = 8, extra = mapOf("lang" to "ko"))
                )
                pcm16(generated.samples, generated.sampleRate)
            }
            VoiceDirection.Manager -> {
                korean?.release()
                korean = null
                val engine = vietnamese ?: VieNeuTurbo(File(directory, "vieneu")).also { vietnamese = it }
                pcm16(engine.synthesise(text), 48_000)
            }
        }
        Log.i("VikoNeuralTts", "${direction.target} ${SystemClock.elapsedRealtime() - start}ms, ${pcm.size / 32_000f}s")
        return pcm.takeIf { it.any { sample -> sample != 0.toByte() } }?.also { recent.put(key, it) }
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

    @Synchronized
    override fun close() {
        korean?.release()
        korean = null
        vietnamese?.close()
        vietnamese = null
        recent.evictAll()
    }

    companion object {
        private val required = listOf(
            "supertonic/duration_predictor.int8.onnx", "supertonic/text_encoder.int8.onnx",
            "supertonic/vector_estimator.int8.onnx", "supertonic/vocoder.int8.onnx",
            "supertonic/tts.json", "supertonic/unicode_indexer.bin", "supertonic/voice.bin",
            "vieneu/config.json", "vieneu/tokenizer.json", "vieneu/preset.json",
            "vieneu/embeddings.f32", "vieneu/sea_g2p.bin", "vieneu/vieneu_prefill.onnx",
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
