package com.example.viko.data.ai.onevoice

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.VikoApplication
import com.example.viko.data.ai.Speaker
import com.example.viko.data.ai.VietnameseVoice
import com.example.viko.data.ai.onevoice.tts.MossStreamingDecoder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class OneVoiceInstrumentedTest {
    @Test fun incrementalCodecPreservesAudioAcrossChunks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = OneVoicePipeline.directory(context).resolveSibling("voices-v1").resolve("vieneu")
        val stepFile = File(directory, "moss_audio_tokenizer_decode_step.onnx")
        assumeTrue("Install the voice pack with the incremental codec", stepFile.isFile)
        val codes = JSONObject(File(directory, "preset.json").readText()).getJSONArray("codes")
        val frames = List(12) { index ->
            val row = codes.getJSONArray(index)
            IntArray(row.length()) { row.getInt(it) }
        }
        val env = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            val full = env.createSession(File(directory, "moss_audio_tokenizer_decode_full.onnx").path, options).use {
                MossStreamingDecoder.runCodec(env, it, frames).use(MossStreamingDecoder::readCodecAudio)
            }
            val streamed = env.createSession(stepFile.path, options).use { session ->
                MossStreamingDecoder(env, session, File(directory, "codec_browser_onnx_meta.json")).stream().use { decoder ->
                    frames.chunked(4).fold(FloatArray(0)) { audio, chunk -> audio + decoder.decode(chunk) }
                }
            }
            assertEquals(full.size, streamed.size)
            assertTrue("Incremental audio differs at a chunk boundary",
                full.indices.maxOf { abs(full[it] - streamed[it]) } < .001f)
        }
    }

    @Test fun supertonicGeneratesKoreanPcm() =
        assertNeuralVoice(VoiceDirection.Worker, "안녕하세요. 작업을 시작해 주세요.")

    @Test fun vieneuTurboGeneratesVietnamesePcm() =
        assertNeuralVoice(VoiceDirection.Manager, "Xin chào, hãy kiểm tra khu vực số ba.")

    private fun assertNeuralVoice(direction: VoiceDirection, text: String) = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as VikoApplication
        val voices = app.container.neuralVoices
        if (InstrumentationRegistry.getArguments().getString("requireNeuralVoices") != "true") {
            assumeTrue("Install the neural voice pack first", voices.installed())
        }
        assertTrue("Neural voice pack is missing or incomplete", voices.installed())
        val chunks = voices.stream(text, direction).toList()
        val size = chunks.sumOf { it.size }
        assertTrue("Expected speech for ${direction.target}", size in 16_000..480_000 && size % 2 == 0)
        assertTrue("Expected audible speech", chunks.any { pcm -> pcm.any { it != 0.toByte() } })
        if (direction == VoiceDirection.Worker || voices.hasStreamingCodec()) {
            assertTrue("Expected multiple PCM chunks for ${direction.target}", chunks.size > 1)
        }
        if (direction == VoiceDirection.Manager && InstrumentationRegistry.getArguments()
                .getString("requireStreamingCodec") == "true") {
            assertTrue("Install the voice pack with the incremental codec", voices.hasStreamingCodec())
        }
        val repeated = voices.stream(text, direction).toList().single()
        assertTrue("Repeated speech should reuse the same PCM",
            chunks.fold(ByteArray(0)) { pcm, chunk -> pcm + chunk }.contentEquals(repeated))
    }

    @Test
    fun reproducesBothReferenceDirectionsOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val root = OneVoicePipeline.directory(instrumentation.targetContext)
        val fixtures = File(root.parentFile, "onevoice-test")
        val expectedFile = File(fixtures, "expected.json")
        if (InstrumentationRegistry.getArguments().getString("requireOneVoice") != "true") {
            assumeTrue("Install the OneVoice pack and fixtures first", expectedFile.isFile)
        }
        assertTrue("OneVoice pack is missing or incomplete", OneVoicePipeline.installed(root))
        val expected = JSONArray(expectedFile.readText())
        assertEquals(3, expected.length())

        OneVoicePipeline(root) { OneVoicePipeline.canKeepBoth(instrumentation.targetContext) }.use { pipeline ->
            if (InstrumentationRegistry.getArguments().getString("prepareOneVoice") == "true") {
                val start = SystemClock.elapsedRealtime()
                pipeline.prepareTranslation()
                pipeline.prepareWorker()
                pipeline.prepareManager()
                Log.i("OneVoiceTest", "Direct Call preparation elapsed=${SystemClock.elapsedRealtime() - start}ms")
            }
            // Repeat a phrase in each direction to exercise translation reuse.
            for (i in 0 until expected.length()) {
                val row = expected.getJSONObject(i)
                val direction = VoiceDirection.valueOf(row.getString("direction"))
                val pcm = File(fixtures, row.getString("file")).readBytes()
                repeat(if (row.getString("file") == "vi_manual.pcm") 1 else 2) { run ->
                    val start = SystemClock.elapsedRealtime()
                    val result = pipeline.process(pcm, direction)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    Log.i("OneVoiceTest", "$direction run=$run elapsed=${elapsed}ms")
                    assertNotNull(result)
                    checkNotNull(result)
                    assertEquals(direction.name, row.getString("transcript"), result.transcript)
                    assertEquals(direction.name, row.getString("tag"), result.tag)
                    assertEquals(direction.name, row.getString("translation"), result.translation)
                }
            }
        }
    }

    @Test
    fun bothOfflineVoicesCanSpeakTheReferenceTranslations() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("requireSpeech") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as VikoApplication
        val fixtures = File(OneVoicePipeline.directory(context).parentFile, "onevoice-test")
        val rows = JSONArray(File(fixtures, "expected.json").readText())
        val korean = app.container.speaker
        val vietnamese = app.container.vietnameseVoice
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while ((korean.status !is Speaker.Status.Ready ||
                vietnamese.status !is VietnameseVoice.Status.Ready) &&
            SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals(Speaker.Status.Ready, korean.status)
        assertEquals(VietnameseVoice.Status.Ready, vietnamese.status)
        assertTrue(korean.speak(rows.getJSONObject(0).getString("translation")))
        val started = SystemClock.elapsedRealtime()
        val pcm = vietnamese.synthesise(rows.getJSONObject(rows.length() - 1).getString("translation"))
        assertNotNull("Vietnamese voice did not produce wearable audio", pcm)
        checkNotNull(pcm)
        assertTrue("Expected non-empty PCM16", pcm.size >= 32_000 && pcm.size % 2 == 0)
        assertTrue("Synthesised PCM is silent", pcm.any { it != 0.toByte() })
        Log.i("OneVoiceTest", "Vietnamese TTS: ${SystemClock.elapsedRealtime() - started}ms to synthesise ${pcm.size / 32_000f}s")
        val repeatStart = SystemClock.elapsedRealtime()
        val repeated = vietnamese.synthesise(rows.getJSONObject(rows.length() - 1).getString("translation"))
        assertTrue("Reused speech should preserve the PCM", repeated != null && pcm.contentEquals(repeated))
        Log.i("OneVoiceTest", "Repeated Vietnamese TTS elapsed=${SystemClock.elapsedRealtime() - repeatStart}ms")
    }
}
