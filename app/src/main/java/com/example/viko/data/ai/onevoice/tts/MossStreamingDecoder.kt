package com.example.viko.data.ai.onevoice.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.IntBuffer

/** The MOSS decode-step graph carries its attention state between groups of audio frames. */
internal class MossStreamingDecoder(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    metadata: File
) {
    private data class StateTensor(
        val input: String,
        val output: String,
        val shape: LongArray,
        val initial: Int = 0,
        val float: Boolean = false
    ) {
        fun tensor(env: OrtEnvironment): OnnxTensor {
            val size = shape.fold(1L, Long::times).toInt()
            return if (float) OnnxTensor.createTensor(env, FloatBuffer.wrap(FloatArray(size)), shape)
            else OnnxTensor.createTensor(env, IntBuffer.wrap(IntArray(size) { initial }), shape)
        }
    }

    private val tensors = buildList {
        val spec = JSONObject(metadata.readText()).getJSONObject("streaming_decode")
        val offsets = spec.getJSONArray("transformer_offsets")
        for (i in 0 until offsets.length()) {
            val row = offsets.getJSONObject(i)
            add(StateTensor(row.getString("input_name"), row.getString("output_name"), row.getJSONArray("shape").shape()))
        }
        val caches = spec.getJSONArray("attention_caches")
        for (i in 0 until caches.length()) {
            val row = caches.getJSONObject(i)
            fun addState(name: String, shape: String, initial: Int = 0, float: Boolean = false) {
                add(StateTensor(row.getString("${name}_input_name"), row.getString("${name}_output_name"),
                    row.getJSONArray(shape).shape(), initial, float))
            }
            addState("offset", "offset_shape")
            addState("cached_keys", "cache_shape", float = true)
            addState("cached_values", "cache_shape", float = true)
            addState("cached_positions", "positions_shape", initial = -1)
        }
    }

    fun stream() = Stream()

    inner class Stream : Closeable {
        private var previous: OrtSession.Result? = null

        fun decode(frames: List<IntArray>): FloatArray {
            val state = previous
            val initial = mutableListOf<OnnxTensor>()
            try {
                val feed = tensors.associate { spec ->
                    spec.input to if (state == null) spec.tensor(env).also(initial::add)
                    else state.get(spec.output).get() as OnnxTensor
                }
                val next = runCodec(env, session, frames, feed)
                previous = next
                state?.close()
                return readCodecAudio(next)
            } finally {
                initial.forEach { it.close() }
            }
        }

        override fun close() {
            previous?.close()
            previous = null
        }
    }

    private fun JSONArray.shape() = LongArray(length()) { getLong(it) }

    companion object {
        fun runCodec(
            env: OrtEnvironment, session: OrtSession, frames: List<IntArray>,
            state: Map<String, OnnxTensor> = emptyMap()
        ): OrtSession.Result {
            val codebooks = frames.first().size
            val codes = IntArray(frames.size * codebooks) { frames[it / codebooks][it % codebooks] }
            val shape = longArrayOf(1, frames.size.toLong(), codebooks.toLong())
            return OnnxTensor.createTensor(env, IntBuffer.wrap(codes), shape).use { tensor ->
                OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(frames.size)), longArrayOf(1)).use { lengths ->
                    session.run(state + mapOf("audio_codes" to tensor, "audio_code_lengths" to lengths))
                }
            }
        }

        fun readCodecAudio(out: OrtSession.Result): FloatArray {
            val tensor = out.get("audio").get() as OnnxTensor
            val stride = tensor.info.shape.last().toInt()
            val length = ((out.get("audio_lengths").get() as OnnxTensor).intBuffer[0]).coerceIn(0, stride)
            if (length == 0) return FloatArray(0)
            val buffer = tensor.floatBuffer
            val channels = buffer.remaining() / stride
            return FloatArray(length) { i ->
                var sample = 0f
                for (channel in 0 until channels) sample += buffer[channel * stride + i]
                sample / channels
            }
        }
    }
}
