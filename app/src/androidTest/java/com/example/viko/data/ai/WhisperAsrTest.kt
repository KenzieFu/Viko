package com.example.viko.data.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.measureTimeMillis

/**
 * Transcribes the reference clip on device and compares against what the Python
 * cascade produced from the same audio.
 *
 * `golden.json` records the reference transcript for `vi_sample.wav`. Exact
 * equality is not asserted — int8 kernels differ across CPUs (see
 * [TranslationPipelineTest]) and ASR compounds that over ~30 decode steps rather
 * than ~14 — so this measures character error rate against the reference and
 * requires it to be small. A wrong forced-language token, a mishandled
 * `cache_position` or a broken mel does not produce a slightly-off transcript;
 * it produces the wrong language, empty output, or noise.
 */
@RunWith(AndroidJUnit4::class)
class WhisperAsrTest {

    private companion object {
        const val TAG = "VikoBench"

        /**
         * Character error rate the transcript must stay under.
         *
         * The reference's own numbers put this in context: it measures 4.14 CER on
         * clean domain audio and 21.54 at 0 dB. A quantisation difference should
         * move a character or two out of sixty; anything past 15% is a wiring
         * fault, not numerics.
         */
        const val MAX_CER = 0.15
    }

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var asr: WhisperAsr

    @Before
    fun setUp() {
        asr = WhisperAsr(target, "vi")
        assumeTrue(
            "no Vietnamese ASR pushed — run scripts/push-models.sh whisper-vi",
            asr.isReady
        )
    }

    @After
    fun tearDown() {
        if (this::asr.isInitialized) asr.close()
    }

    private fun goldenAsr(): String {
        val rows = JSONArray(context.assets.open("golden.json").bufferedReader().readText())
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (row.getString("audio") == "vi_sample.wav") return row.getString("asr")
        }
        error("golden.json has no vi_sample.wav row")
    }

    private fun samplePcm(): Pair<ByteArray, Int> {
        val bytes = context.assets.open("vi_sample.wav").readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4)
            val size = buf.getInt(i + 4)
            if (id == "data") return bytes.copyOfRange(i + 8, i + 8 + size) to size
            i += 8 + size + (size and 1)
        }
        error("no data chunk")
    }

    @Test
    fun transcribesTheReferenceClip() {
        val (pcm, size) = samplePcm()
        var text: String? = null
        val cold = measureTimeMillis { text = asr.transcribe(pcm, size) }

        val want = goldenAsr()
        Log.i(TAG, "asr cold: ${cold}ms")
        Log.i(TAG, "asr want: $want")
        Log.i(TAG, "asr got : $text")

        assertTrue("transcript was null or blank", !text.isNullOrBlank())
        val cer = characterErrorRate(want.lowercase(), text!!.lowercase())
        Log.i(TAG, "asr CER : ${"%.3f".format(cer)}")
        assertTrue(
            "CER $cer exceeds $MAX_CER — that is a wiring fault, not quantisation.\n" +
                "  want: $want\n  got : $text",
            cer <= MAX_CER
        )
    }

    /** Steady state, which is what a second badge utterance actually costs. */
    @Test
    fun reportsWarmLatency() {
        val (pcm, size) = samplePcm()
        asr.transcribe(pcm, size)
        val timings = (1..3).map { measureTimeMillis { asr.transcribe(pcm, size) } }
        Log.i(TAG, "asr warm x3: ${timings.joinToString("ms, ")}ms")
        Log.i(TAG, "asr warm median: ${timings.sorted()[1]}ms")
    }

    /** Silence must terminate rather than run to the token cap emitting noise. */
    @Test
    fun handlesSilenceWithoutRunningAway() {
        val silence = ByteArray(16_000 * 2)      // one second of zeros
        val text = asr.transcribe(silence)
        Log.i(TAG, "asr silence: '$text'")
        assertTrue("silence produced $text", (text?.length ?: 0) < 120)
    }

    /** Levenshtein distance over characters, normalised by reference length. */
    private fun characterErrorRate(reference: String, hypothesis: String): Double {
        val prev = IntArray(hypothesis.length + 1) { it }
        val cur = IntArray(hypothesis.length + 1)
        for (i in 1..reference.length) {
            cur[0] = i
            for (j in 1..hypothesis.length) {
                val sub = prev[j - 1] + if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                cur[j] = minOf(sub, prev[j] + 1, cur[j - 1] + 1)
            }
            System.arraycopy(cur, 0, prev, 0, cur.size)
        }
        return prev[hypothesis.length].toDouble() / reference.length.coerceAtLeast(1)
    }
}
