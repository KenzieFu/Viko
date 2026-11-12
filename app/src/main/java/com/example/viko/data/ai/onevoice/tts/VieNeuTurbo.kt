package com.example.viko.data.ai.onevoice.tts

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.random.Random

/** VieNeu v3 Turbo CPU inference with the upstream int8 graphs and preset conditioning. */
internal class VieNeuTurbo(private val directory: File) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val config = JSONObject(File(directory, "config.json").readText())
    private val preset = JSONObject(File(directory, "preset.json").readText())
    private val hidden = config.getInt("hidden_size")
    private val codebooks = config.getInt("n_vq")
    private val textVocabulary = config.getInt("text_vocab_size")
    private val audioRows = preset.getInt("audioVocabularySize")
    private val audioVocabulary = config.getInt("audio_vocab_size")
    private val layers = config.getInt("num_hidden_layers")
    private val localLayers = config.getInt("local_num_hidden_layers")
    private val localHeads = config.getInt("local_num_attention_heads")
    private val startSpeech = config.getInt("speech_generation_start_token_id")
    private val endSpeech = config.getInt("speech_generation_end_token_id")
    private val referenceSlot = config.getInt("audio_ref_slot_token_id")
    private val reference = preset.getJSONArray("codes").let { rows ->
        List(rows.length()) { row ->
            val codes = rows.getJSONArray(row)
            IntArray(codebooks) { codes.getInt(it) }
        }
    }
    private val weights = RandomAccessFile(File(directory, "embeddings.f32"), "r").use { file ->
        require(file.length() == (textVocabulary + codebooks * audioRows + 1L) * hidden * 4)
        file.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
            .order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    }
    private val anchor = embedding(textVocabulary + codebooks * audioRows)
    private val resources = mutableListOf<AutoCloseable>()
    private val phonemizer: SeaG2p
    private val tokenizer: HuggingFaceTokenizer
    private val prefill: OrtSession
    private val backbone: OrtSession
    private val acoustic: OrtSession
    private val codec: OrtSession

    init {
        try {
            phonemizer = SeaG2p(File(directory, "sea_g2p.bin")).also(resources::add)
            tokenizer = HuggingFaceTokenizer.newInstance(File(directory, "tokenizer.json").toPath())
                .also(resources::add)
            prefill = session("vieneu_prefill.onnx")
            backbone = session("vieneu_decode_step.onnx")
            acoustic = session("vieneu_acoustic_cached.onnx")
            codec = session("moss_audio_tokenizer_decode_full.onnx")
        } catch (failure: Exception) {
            close()
            throw failure
        } catch (failure: LinkageError) {
            close()
            throw failure
        }
    }

    private fun session(name: String): OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(4)
        options.setInterOpNumThreads(1)
        options.addConfigEntry("session.intra_op.allow_spinning", "0")
        env.createSession(File(directory, name).path, options).also(resources::add)
    }

    fun synthesise(text: String): FloatArray {
        val phonemes = phonemizer.convert(text)
        val chunks = phonemes.split(Regex("\\s+")).filter(String::isNotEmpty)
            .fold(mutableListOf<StringBuilder>()) { result, word ->
                val last = result.lastOrNull()
                if (last == null || last.length + word.length + 1 > 180) result += StringBuilder(word)
                else last.append(' ').append(word)
                result
            }
        val rendered = chunks.map { generate(it.toString()) }
        if (rendered.isEmpty()) return FloatArray(0)
        val pause = 3_840
        val output = FloatArray(rendered.sumOf { it.size } + pause * (rendered.size - 1))
        var offset = 0
        for (samples in rendered) {
            samples.copyInto(output, offset)
            offset += samples.size + pause
        }
        return output
    }

    private fun generate(phonemes: String): FloatArray {
        val phones = tokenizer.encode(phonemes, false, false).ids.map { it.toInt() }
        val promptIds = listOf(config.getInt("default_style_token_id"), config.getInt("text_prompt_start_token_id")) +
            phones + config.getInt("text_prompt_end_token_id")
        val length = promptIds.size + reference.size
        val prompt = FloatArray(length * hidden)
        promptIds.forEachIndexed { i, id -> row(id).copyInto(prompt, i * hidden) }
        reference.forEachIndexed { i, codes -> row(referenceSlot, codes).copyInto(prompt, (promptIds.size + i) * hidden) }

        val frames = mutableListOf<IntArray>()
        val maxFrames = minOf(300, 24 + 2 * phonemes.length, config.getInt("max_position_embeddings") - length)
        require(maxFrames > 0) { "VieNeu text prompt is too long" }
        val history = ArrayDeque<IntArray>()
        var state = floats(prompt, 1, length.toLong(), hidden.toLong()).use {
            prefill.run(mapOf("inputs_embeds" to it))
        }
        try {
            for (i in 0 until maxFrames) {
                val (codes, finished) = frame(hiddenRow(state, if (i == 0) length - 1 else 0), history)
                frames.add(codes)
                if (finished || i == maxFrames - 1) break
                history.addLast(codes)
                if (history.size > 50) history.removeFirst()
                val next = step(backbone, "inputs_embeds", row(startSpeech, codes),
                    longArrayOf((length + i).toLong()), state, layers)
                state.close()
                state = next
            }
        } finally {
            state.close()
        }
        return decode(frames)
    }

    private fun frame(condition: FloatArray, history: List<IntArray>): Pair<IntArray, Boolean> {
        val first = condition + embedding(startSpeech)
        val shape = longArrayOf(1, localHeads.toLong(), 0, (hidden / localHeads).toLong())
        var state = floats(FloatArray(0), *shape).use { empty ->
            val past = mutableMapOf<String, OnnxTensor>()
            for (i in 0 until localLayers) {
                past["past_k_$i"] = empty
                past["past_v_$i"] = empty
            }
            runStep(acoustic, "token_emb", first, longArrayOf(0, 1), past)
        }
        try {
            val eos = argmax(phonemizer.project(weights, 0, textVocabulary, hiddenRow(state, 0))) == endSpeech
            val codes = IntArray(codebooks)
            for (channel in codes.indices) {
                val result = hiddenRow(state, if (channel == 0) 1 else 0)
                val logits = phonemizer.project(weights, audioRow(channel, 0) * hidden, audioVocabulary, result)
                codes[channel] = sample(logits, history.map { it[channel] }.toSet())
                if (channel == codes.lastIndex) break
                val next = step(acoustic, "token_emb", embedding(audioRow(channel, codes[channel])),
                    longArrayOf((channel + 2).toLong()), state, localLayers)
                state.close()
                state = next
            }
            return codes to eos
        } finally {
            state.close()
        }
    }

    private fun step(
        session: OrtSession, name: String, vector: FloatArray, positions: LongArray,
        previous: OrtSession.Result, count: Int
    ): OrtSession.Result {
        val past = mutableMapOf<String, OnnxTensor>()
        for (i in 0 until count) {
            past["past_k_$i"] = previous[1 + i] as OnnxTensor
            past["past_v_$i"] = previous[1 + count + i] as OnnxTensor
        }
        return runStep(session, name, vector, positions, past)
    }

    private fun runStep(
        session: OrtSession, name: String, vector: FloatArray, positions: LongArray, past: Map<String, OnnxTensor>
    ): OrtSession.Result {
        val length = positions.size.toLong()
        return floats(vector, 1, length, hidden.toLong()).use { input ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(positions), longArrayOf(1, length)).use { ids ->
                session.run(past + mapOf(name to input, "position_ids" to ids))
            }
        }
    }

    private fun decode(frames: List<IntArray>): FloatArray {
        val flattened = IntArray(frames.size * codebooks) { frames[it / codebooks][it % codebooks] }
        val codesShape = longArrayOf(1, frames.size.toLong(), codebooks.toLong())
        return OnnxTensor.createTensor(env, IntBuffer.wrap(flattened), codesShape).use { codes ->
            OnnxTensor.createTensor(env, IntBuffer.wrap(intArrayOf(frames.size)), longArrayOf(1)).use { lengths ->
                codec.run(mapOf("audio_codes" to codes, "audio_code_lengths" to lengths)).use { out ->
                    val tensor = out[0] as OnnxTensor
                    val buffer = tensor.floatBuffer
                    val length = tensor.info.shape.last().toInt()
                    val channels = buffer.remaining() / length
                    val audio = FloatArray(length)
                    repeat(channels) {
                        for (i in audio.indices) audio[i] += buffer.get() / channels
                    }
                    audio
                }
            }
        }
    }

    private fun floats(data: FloatArray, vararg shape: Long) =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun hiddenRow(result: OrtSession.Result, row: Int): FloatArray {
        val buffer = (result[0] as OnnxTensor).floatBuffer
        buffer.position(row * hidden)
        return FloatArray(hidden).also { buffer.get(it) }
    }

    private fun audioRow(channel: Int, code: Int) = textVocabulary + channel * audioRows + code

    private fun embedding(row: Int): FloatArray {
        val view = weights.duplicate()
        view.position(row * hidden)
        return FloatArray(hidden).also { view.get(it) }
    }

    private fun row(id: Int, codes: IntArray? = null): FloatArray {
        val values = embedding(id)
        codes?.forEachIndexed { channel, code ->
            val offset = audioRow(channel, code) * hidden
            for (i in values.indices) values[i] += weights[offset + i]
        }
        for (i in values.indices) values[i] += anchor[i]
        return values
    }

    private fun argmax(values: FloatArray): Int = values.indices.maxBy { values[it] }

    /** Top-k / nucleus sampling with the preset's repetition penalty. */
    private fun sample(logits: FloatArray, seen: Set<Int>): Int {
        for (index in seen) {
            logits[index] = if (logits[index] < 0) logits[index] * 1.2f else logits[index] / 1.2f
        }
        val top = IntArray(25) { -1 }
        for (index in logits.indices) {
            var slot = top.lastIndex
            if (top[slot] >= 0 && logits[index] <= logits[top[slot]]) continue
            while (slot > 0 && (top[slot - 1] < 0 || logits[index] > logits[top[slot - 1]])) {
                top[slot] = top[slot - 1]
                slot--
            }
            top[slot] = index
        }
        val mass = top.map { exp(((logits[it] - logits[top[0]]) / .8f).toDouble()) }
        val limit = mass.sum() * .95
        var total = 0.0
        var count = 0
        while (count < mass.size && total < limit) total += mass[count++]
        var choice = Random.nextDouble(total)
        for (i in 0 until count) {
            choice -= mass[i]
            if (choice <= 0) return top[i]
        }
        return top[count - 1]
    }

    override fun close() {
        resources.asReversed().forEach { runCatching { it.close() } }
        resources.clear()
    }
}
