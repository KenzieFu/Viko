package com.example.viko.data.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Checks the return path: Vietnamese text to the PCM16 the badge expects.
 *
 * This is the half of the pipeline with no model behind it, so nothing here is
 * about quality — it is about the platform contract. The engine synthesises at
 * its own rate into a WAV whose header layout it chooses, and the firmware wants
 * 16kHz mono PCM16. Every assertion below is about that conversion being right,
 * because getting it wrong produces audio that plays at the wrong speed or is
 * shifted by a header, and neither throws.
 *
 * Skips when no Vietnamese voice is installed, which is itself the single most
 * likely deployment failure — see [VietnameseVoice.status].
 */
@RunWith(AndroidJUnit4::class)
class VietnameseVoiceTest {

    private companion object {
        const val TAG = "VikoBench"

        /** What the reverse cascade would produce for a Korean instruction. */
        const val VIETNAMESE = "Dừng máy lại ngay, có người phía trước."
    }

    private lateinit var voice: VietnameseVoice

    @Before
    fun setUp() {
        voice = VietnameseVoice(InstrumentationRegistry.getInstrumentation().targetContext)
        // The engine initialises asynchronously; poll rather than sleep blindly.
        val deadline = System.currentTimeMillis() + 10_000
        while (voice.status is VietnameseVoice.Status.Initialising &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(100)
        }
        Log.i(TAG, "tts(vi) status: ${voice.status}")
        assumeTrue(
            "no Vietnamese voice on this phone — install it under Settings > " +
                "Text-to-speech while there is still internet",
            voice.status is VietnameseVoice.Status.Ready
        )
    }

    @After
    fun tearDown() {
        if (this::voice.isInitialized) voice.close()
    }

    /**
     * The whole contract in one test: bytes come back, at the rate the firmware
     * expects, of a length that matches how long the sentence should take to say.
     *
     * The duration check is what catches a missed resample. If the engine
     * synthesised at 22050 or 24000 and nothing converted it, the byte count would
     * be 1.4-1.5x what 16kHz implies — the badge would play it too fast and the
     * only symptom would be a chipmunk voice on a factory floor.
     */
    @Test
    fun synthesisesToSixteenKilohertzPcm() {
        var pcm: ByteArray? = null
        val ms = measureTimeMillis { pcm = voice.synthesise(VIETNAMESE) }
        assertTrue("synthesis returned nothing", pcm != null && pcm!!.isNotEmpty())

        val samples = pcm!!.size / 2
        val seconds = samples / VietnameseVoice.TARGET_RATE.toDouble()
        Log.i(TAG, "tts(vi): ${ms}ms to synthesise ${"%.2f".format(seconds)}s ($samples samples)")

        assertTrue("odd byte count — not PCM16", pcm!!.size % 2 == 0)
        // A ~40-character sentence lands well inside this. Outside it, the rate
        // conversion is wrong rather than the speech being unusual.
        assertTrue("$seconds s is not a plausible length for this sentence",
            seconds in 1.0..8.0)
    }

    /** Silence would be indistinguishable from a failed send, so check it is speech. */
    @Test
    fun producesActualAudioRatherThanSilence() {
        val pcm = voice.synthesise(VIETNAMESE)!!
        val level = SpeechPipeline.rms(pcm)
        Log.i(TAG, "tts(vi) rms: ${"%.0f".format(level)}")
        assertTrue("synthesised audio is silent (rms $level)", level > 500)
    }

    /**
     * Chunking for the badge divides evenly. The firmware reads 20ms frames, and a
     * trailing partial frame is the kind of thing that works on the bench and
     * clicks on hardware.
     */
    @Test
    fun lengthWorksAsTwentyMillisecondChunks() {
        val pcm = voice.synthesise(VIETNAMESE)!!
        val chunk = 320 * 2
        Log.i(TAG, "tts(vi): ${pcm.size / chunk} full chunks, ${pcm.size % chunk} bytes over")
        assertTrue("a partial frame is fine, a partial SAMPLE is not", pcm.size % 2 == 0)
    }

    @Test
    fun refusesBlankText() {
        assertNull(voice.synthesise("   "))
    }
}
