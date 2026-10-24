package com.example.viko.data.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [TagClassifier.asrNormalize] to the Python reference's `asr_normalize`.
 *
 * A JVM test, not an instrumented one: this is pure string handling with no model
 * behind it, so it runs on a bare checkout with nothing pushed. That matters,
 * because it is the only part of the tagger that can be verified without hardware.
 *
 * Worth pinning because drift here is invisible. The tagger was trained on text
 * put through this function, and `!` alone is very nearly a label proxy — 100% of
 * SOS training rows carry one, 0% of ROUTINE rows do, and ASR emits none. A
 * normalizer that diverges silently costs SOS recall (0.676 to 0.486 in the
 * reference) rather than throwing anything.
 */
class AsrNormalizeTest {

    /** Straight from `Viko-Ai/onevoice_e2e/fixtures/golden.json`, vi2ko row. */
    @Test
    fun matchesTheGoldenVietnameseTranscript() {
        assertEquals(
            "hàng đục này đang gấp em làm nhanh tay hơn một chút được không",
            TagClassifier.asrNormalize(
                "hàng đục này đang gấp em làm nhanh tay hơn một chút được không."
            )
        )
    }

    /** The ko2vi row, which the reference emits with no punctuation at all. */
    @Test
    fun leavesAlreadyCleanKoreanUnchanged() {
        val korean = "무낭이 급해서 그러는데 조금만 더 서술에 주실래요"
        assertEquals(korean, TagClassifier.asrNormalize(korean))
    }

    /**
     * Every character in the reference's class: `[!?.,;:~…"'()\[\]]+`.
     * A run of them collapses to ONE space, because the regex is greedy.
     */
    @Test
    fun stripsEveryPunctuationCharacterTheReferenceStrips() {
        assertEquals(
            "a b",
            TagClassifier.asrNormalize("a!?.,;:~…\"'()[]b")
        )
    }

    /**
     * The substitution is a space, not an empty string — so punctuation between
     * two words leaves them separated rather than fused. `"dừng!lại"` must not
     * become `"dừnglại"`, which would tokenise as a different word entirely.
     */
    @Test
    fun replacesPunctuationWithASpaceRatherThanDeletingIt() {
        assertEquals("dừng lại", TagClassifier.asrNormalize("dừng!lại"))
    }

    /**
     * Trailing punctuation becomes whitespace and is then trimmed — but interior
     * runs are NOT collapsed.
     *
     * Note the **two** spaces in the expected value: the `!` becomes a space and
     * the space that already followed it stays, so the reference emits
     * `'불이야  대피해'`. Verified against `pipeline.asr_normalize` directly. Anything
     * that tidies this to a single space has stopped matching what the tagger was
     * trained on.
     */
    @Test
    fun trimsTheEdgesWithoutCollapsingInteriorWhitespace() {
        assertEquals("불이야  대피해", TagClassifier.asrNormalize("불이야! 대피해!"))
    }

    /** Lowercasing applies to the Latin script Vietnamese uses, diacritics kept. */
    @Test
    fun lowercasesWithoutStrippingVietnameseDiacritics() {
        assertEquals("máy Ép bị kẹt".lowercase(), TagClassifier.asrNormalize("Máy Ép Bị Kẹt"))
        assertEquals("máy ép bị kẹt", TagClassifier.asrNormalize("Máy Ép Bị Kẹt"))
    }

    /** Hangul has no case, and must survive the lowercase step untouched. */
    @Test
    fun leavesHangulUntouched() {
        assertEquals("멈춰", TagClassifier.asrNormalize("멈춰"))
    }

    @Test
    fun handlesEmptyAndPunctuationOnlyInput() {
        assertEquals("", TagClassifier.asrNormalize(""))
        assertEquals("", TagClassifier.asrNormalize("!!!"))
        assertEquals("", TagClassifier.asrNormalize("   "))
    }
}
