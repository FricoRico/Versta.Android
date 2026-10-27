package app.versta.translate.adapter.outbound

import app.versta.translate.adapter.outbound.JapaneseTransliterator.TransliterationPart
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden input→output tests for the pure text-combination and whitespace logic
 * of [JapaneseTransliterator]. Expectations pin the exact current behavior,
 * including quirks such as the space inserted after a full-width period and
 * NBSP/ideographic-space passthrough in whitespace normalization.
 */
class JapaneseTransliteratorTest {
    private fun part(
        surface: String,
        reading: String,
        isKnown: Boolean = true,
    ) = TransliterationPart(surface, reading, isKnown)

    @Test
    fun knownTokensAreJoinedWithSingleSpaces() {
        val parts = listOf(part("私", "ワタシ"), part("は", "ハ"), part("寿司", "スシ"))

        assertEquals("ワタシ ハ スシ", JapaneseTransliterator.combineTransliterationParts(parts))
    }

    @Test
    fun fullWidthPeriodIsAppendedBareAndFollowingTokenKeepsLeadingSpace() {
        // The next-token lookbehind pattern does not include 。, so a space is
        // still inserted after it; pinned as-is.
        val parts = listOf(part("私", "ワタシ"), part("。", "。"), part("次", "ツギ"))

        assertEquals("ワタシ 。 ツギ", JapaneseTransliterator.combineTransliterationParts(parts))
    }

    @Test
    fun bracketsAndSymbolPredecessorsSuppressSpacing() {
        assertEquals(
            "【ワタシ】",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("【", "【"), part("私", "ワタシ"), part("】", "】")),
            ),
        )
        assertEquals(
            "・ナド",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("・", "・"), part("など", "ナド")),
            ),
        )
        assertEquals(
            "…ア 、 イ",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("…", "…"), part("あ", "ア"), part("、", "、"), part("い", "イ")),
            ),
        )
    }

    @Test
    fun unknownTokensAreAppendedVerbatim() {
        assertEquals(
            "hello セカイ",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("hello", "hello", isKnown = false), part("世界", "セカイ")),
            ),
        )
        assertEquals(
            "セカイx3 カエル",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("世界", "セカイ"), part("x3", "x3", isKnown = false), part("帰る", "カエル")),
            ),
        )
        assertEquals(
            "foobar",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("foo", "foo", isKnown = false), part("bar", "bar", isKnown = false)),
            ),
        )
    }

    @Test
    fun unknownWhitespaceTokensInteractWithSpacingRules() {
        // Unknown ideographic space token is appended verbatim; the following
        // known token still gets its leading space from the lookbehind.
        assertEquals(
            "パン　 ピザ",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("パン", "パン"), part("　", "　", isKnown = false), part("ピザ", "ピザ")),
            ),
        )

        assertEquals(
            "ウエ\n シタ",
            JapaneseTransliterator.combineTransliterationParts(
                listOf(part("上", "ウエ"), part("\n", "\n", isKnown = false), part("下", "シタ")),
            ),
        )
    }

    @Test
    fun whitespaceNormalizationPinsPassthroughAndCollapses() {
        // NBSP and ideographic space are not matched by [^\S \n] and pass through.
        assertEquals("a\u00A0b", JapaneseTransliterator.normalizeWhitespaces("a\u00A0b"))
        assertEquals("a　b", JapaneseTransliterator.normalizeWhitespaces("a　b"))
        // Tabs and carriage returns become plain spaces; runs collapse.
        assertEquals("a b", JapaneseTransliterator.normalizeWhitespaces("a\tb"))
        assertEquals("a b", JapaneseTransliterator.normalizeWhitespaces("a\rb"))
        assertEquals("a b", JapaneseTransliterator.normalizeWhitespaces("a  b"))
        assertEquals("a b", JapaneseTransliterator.normalizeWhitespaces("a   b"))
        // Spaces sandwiched between newlines are stripped.
        assertEquals("\n\n", JapaneseTransliterator.normalizeWhitespaces("\n \n"))
        assertEquals("x\n\ny", JapaneseTransliterator.normalizeWhitespaces("x\n  \ny"))
        // Leading/trailing runs collapse but are not trimmed.
        assertEquals(" leading", JapaneseTransliterator.normalizeWhitespaces("  leading"))
        assertEquals("trailing ", JapaneseTransliterator.normalizeWhitespaces("trailing   "))
    }
}
