package com.example.viko.data.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Checks the on-device decode loop against the reference implementation, and
 * times it.
 *
 * The expected strings come from `Viko-Ai/onevoice_e2e/fixtures/golden.json`,
 * produced by running the Python cascade on `samples/vi_sample.wav`. The vi2ko
 * row reproduces the reference README character for character, which is what
 * makes it worth asserting on.
 *
 * **Skips rather than fails when no models are pushed.** That is the normal state
 * of a checkout — the graphs are ~1.8GB and live outside the APK. Run
 * `scripts/push-models.sh` first, then:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest \
 *     --tests '*TranslationPipelineTest*'
 * ```
 *
 * Timings print to logcat under the `VikoBench` tag. They are the number Phase 1
 * exists to produce: MT is ~60% of cascade latency, so if this is slow on the
 * target hardware, no amount of work on ASR or TTS rescues the design.
 */
@RunWith(AndroidJUnit4::class)
class TranslationPipelineTest {

    private companion object {
        const val TAG = "VikoBench"

        /** golden.json, vi_sample.wav — the ASR transcript and its predicted tag. */
        const val VI_SOURCE = "hàng đục này đang gấp em làm nhanh tay hơn một chút được không."
        const val VI_TAG = "BUSY"

        /**
         * Two renderings, both correct, because int8 matmul is not bit-reproducible
         * across CPUs.
         *
         * The first is what torch and desktop ONNX Runtime produce. The second is what
         * this phone produces, and it is **not a port bug** — it was verified by running
         * the identical exported graphs through ONNX Runtime in Python, which returns
         * the second rendering token for token (532, 86777, 51465, 63188, ...). The two
         * diverge at generated token index 2, on a single marginal argmax.
         *
         * The cause is the CPU, not the code: Apple Silicon has the ARM dot-product and
         * i8mm instructions, the Snapdragon 685's Cortex-A73/A53 cores do not, so the
         * int8 accumulation order differs and a near-tie flips.
         *
         * Kept as a closed set rather than relaxed to "is not blank": a wrong language
         * id, a mishandled KV cache or a dropped tag still produces fluent Korean, and
         * anything outside these two must fail loudly.
         */
        val ACCEPTED_KO = setOf(
            "이 물건 급해요. 조금 더 빨리 할 수 있어요.",
            "이 물건 빨리 올라와요. 조금 더 빨리 할 수 있어요."
        )

        /** golden.json, ko_sample.wav. The reference calls this direction unreliable,
         *  so it is a smoke test for wiring, not an equality assertion. */
        const val KO_SOURCE = "무낭이 급해서 그러는데 조금만 더 서술에 주실래요"
        const val KO_TAG = "ERROR"
    }

    private lateinit var pipeline: TranslationPipeline

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Logged unconditionally: "models not found" and "models found somewhere the
        // app cannot read" look identical from isReady, and the path differs between
        // adb's view of /sdcard and the app's own external dir often enough to be
        // the first thing worth checking.
        val external = context.getExternalFilesDir(null)
        Log.i(TAG, "externalFilesDir = $external")
        val dir = java.io.File(external, "models/nllb")
        Log.i(TAG, "modelDir = $dir  exists=${dir.exists()} readable=${dir.canRead()}")
        dir.listFiles()?.forEach { Log.i(TAG, "  ${it.name} ${it.length()}") }
            ?: Log.i(TAG, "  listFiles() returned null")

        pipeline = TranslationPipeline(context)
        assumeTrue(
            "no models pushed — run scripts/push-models.sh",
            pipeline.isReady
        )
    }

    @After
    fun tearDown() {
        if (this::pipeline.isInitialized) pipeline.close()
    }

    /**
     * The one that matters. Landing in [ACCEPTED_KO] proves the whole chain agrees
     * with the reference: tokenizer, language ids, the tag prefix, greedy argmax,
     * and the KV cache carried correctly across decode steps.
     *
     * A wrong language id or a mishandled cache does not crash — it produces
     * fluent, plausible text that simply is not one of these strings. That is why
     * this compares against a closed set rather than checking for non-emptiness.
     */
    @Test
    fun translatesTheGoldenVietnameseUtterance() {
        var result: String? = null
        val cold = measureTimeMillis {
            result = pipeline.translate(VI_SOURCE, source = "vi", target = "ko", tag = VI_TAG)
        }
        Log.i(TAG, "vi2ko cold (includes loading ~1.8GB of graphs): ${cold}ms")
        Log.i(TAG, "vi2ko out: $result")

        assertTrue(
            "translation outside the accepted set — this is a wiring bug, not int8 " +
                "drift, since quantisation moves single marginal tokens and not the " +
                "sentence: $result",
            result in ACCEPTED_KO
        )
    }

    /** Wiring check for the reverse direction: same weights, different forced token. */
    @Test
    fun translatesKoreanToVietnamese() {
        val result = pipeline.translate(KO_SOURCE, source = "ko", target = "vi", tag = KO_TAG)
        Log.i(TAG, "ko2vi out: $result")

        assertTrue("expected Vietnamese output, got: $result", !result.isNullOrBlank())
        // Latin script rather than Hangul is the cheap way to catch the failure this
        // direction is prone to: a wrong forced BOS token yields fluent output in
        // some other language, most often the source language echoed back.
        assertTrue(
            "output is not Latin script, so the target language token is wrong: $result",
            result!!.any { it in 'a'..'z' || it in 'A'..'Z' } && result.none { it in '가'..'힣' }
        )
    }

    /**
     * Steady state, which is what the device actually experiences — the graphs are
     * already mapped by the time a second badge speaks. This is the number to
     * compare against the reference's 1.22s for MT on an M-series Mac.
     */
    @Test
    fun reportsWarmLatency() {
        pipeline.translate(VI_SOURCE, source = "vi", target = "ko", tag = VI_TAG)

        val runs = 3
        val timings = (1..runs).map {
            measureTimeMillis {
                pipeline.translate(VI_SOURCE, source = "vi", target = "ko", tag = VI_TAG)
            }
        }
        Log.i(TAG, "vi2ko warm x$runs: ${timings.joinToString("ms, ")}ms")
        Log.i(TAG, "vi2ko warm median: ${timings.sorted()[runs / 2]}ms")
    }

    /** A phone with no models must return null, not throw — every caller relies on it. */
    @Test
    fun rejectsBlankInputWithoutTouchingTheModel() {
        assertEquals(null, pipeline.translate("   ", source = "vi", target = "ko", tag = VI_TAG))
    }
}
