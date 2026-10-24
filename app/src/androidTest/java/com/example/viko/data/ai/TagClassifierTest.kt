package com.example.viko.data.ai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

/**
 * Checks the on-device tagger against the same four utterances the export script
 * verified in Python, in `Viko-Ai/onevoice_e2e/export_onnx.py::check_tagger`.
 *
 * These four are the whole point of this test. The classifier cannot fail
 * loudly — a wrong label index still returns a valid tag, and the only visible
 * consequence is Korean in the wrong politeness register several stages later.
 * Two of the four are Vietnamese and two Korean, so a mapping that is rotated,
 * reversed, or silently language-dependent shows up here rather than in a demo.
 *
 * Skips when no model is pushed. Run `scripts/push-models.sh tagger` first:
 *
 * ```
 * ./gradlew :app:connectedDebugAndroidTest --tests '*TagClassifierTest*'
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TagClassifierTest {

    private companion object {
        const val TAG = "VikoBench"

        /** (utterance, expected tag) — identical to the Python-side check. */
        val CASES = listOf(
            "Máy ép bị kẹt rồi! Dừng lại ngay!" to "SOS",
            "hàng đục này đang gấp em làm nhanh tay hơn một chút được không" to "BUSY",
            "불이야! 대피해!" to "SOS",
            "오늘 작업은 여기까지 하겠습니다." to "ROUTINE"
        )
    }

    private lateinit var classifier: TagClassifier

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        classifier = TagClassifier(context)
        assumeTrue(
            "no tagger pushed — run scripts/push-models.sh tagger",
            classifier.isReady
        )
    }

    @After
    fun tearDown() {
        if (this::classifier.isInitialized) classifier.close()
    }

    /**
     * The one that matters: same inputs, same tags as torch produced.
     *
     * The second case is the `vi_sample.wav` transcript, and `BUSY` is what both
     * the reference README and `fixtures/golden.json` record for it — so a pass
     * here means the Kotlin agrees with the Python end to end on the utterance
     * the rest of the pipeline is validated against.
     */
    @Test
    fun agreesWithTheReferenceOnEveryVerifiedUtterance() {
        val wrong = CASES.mapNotNull { (text, expected) ->
            val got = classifier.classify(text)
            Log.i(TAG, "tag: ${expected.padEnd(8)} got ${got?.padEnd(8)}  $text")
            if (got != expected) "$text -> expected $expected, got $got" else null
        }
        assertEquals("tagger disagrees with the reference", emptyList<String>(), wrong)
    }

    /**
     * Both languages must route through the same weights and the same label order.
     * Checked separately from the batch above so a language-dependent regression
     * names itself instead of hiding in a list.
     */
    @Test
    fun classifiesKoreanAndVietnameseWithOneModel() {
        assertEquals("SOS", classifier.classify("불이야! 대피해!"))
        assertEquals("SOS", classifier.classify("Máy ép bị kẹt rồi! Dừng lại ngay!"))
    }

    /**
     * Normalisation happens inside [TagClassifier.classify], matching the Python
     * reference's `predict()`. Punctuated and normalised input must therefore give
     * the same answer — if they differ, the caller is normalising too and the
     * tagger is seeing text it was not trained on.
     */
    @Test
    fun normalisesInternallySoPunctuationDoesNotChangeTheAnswer() {
        assertEquals(
            classifier.classify("불이야  대피해"),
            classifier.classify("불이야! 대피해!")
        )
    }

    @Test
    fun returnsNullForBlankInput() {
        assertNull(classifier.classify("   "))
    }

    /** The reference measures ~6ms on desktop CPU. Worth knowing what a phone does,
     *  since this runs on every utterance ahead of the expensive stage. */
    @Test
    fun reportsLatency() {
        classifier.classify(CASES[0].first)
        val timings = (1..5).map {
            measureTimeMillis { classifier.classify(CASES[it % CASES.size].first) }
        }
        Log.i(TAG, "tagger warm x5: ${timings.joinToString("ms, ")}ms")
    }
}
