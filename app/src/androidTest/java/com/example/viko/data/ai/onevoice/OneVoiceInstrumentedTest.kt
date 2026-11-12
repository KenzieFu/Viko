package com.example.viko.data.ai.onevoice

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.viko.VikoApplication
import com.example.viko.data.ai.Speaker
import com.example.viko.data.ai.VietnameseVoice
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OneVoiceInstrumentedTest {
    @Test fun supertonicGeneratesKoreanPcm() =
        assertNeuralVoice(VoiceDirection.Worker, "안녕하세요. 작업을 시작해 주세요.")

    @Test fun vieneuTurboGeneratesVietnamesePcm() =
        assertNeuralVoice(VoiceDirection.Manager, "Xin chào, hãy kiểm tra khu vực số ba.")

    private fun assertNeuralVoice(direction: VoiceDirection, text: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as VikoApplication
        val voices = app.container.neuralVoices
        if (InstrumentationRegistry.getArguments().getString("requireNeuralVoices") != "true") {
            assumeTrue("Install the neural voice pack first", voices.installed())
        }
        assertTrue("Neural voice pack is missing or incomplete", voices.installed())
        val pcm = checkNotNull(voices.synthesise(text, direction))
        assertTrue("Expected speech for ${direction.target}", pcm.size in 16_000..480_000 && pcm.size % 2 == 0)
        assertTrue("Repeated speech should reuse the same PCM", pcm.contentEquals(voices.synthesise(text, direction)))
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
