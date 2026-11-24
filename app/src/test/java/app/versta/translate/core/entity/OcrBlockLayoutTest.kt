package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the paragraph-block wrap that replaced the deleted
 * ParagraphGroupingPostProcessor pipeline (OCR rebuild, e474f0b): spaced
 * scripts wrap at space runs, CJK wraps per character under kinsoku, and the
 * fit loop shrinks 1px at a time down to the floor before cramming.
 */
class OcrBlockLayoutTest {
    /**
     * One pixel of width per UTF-16 unit per text size: deterministic and
     * linear like the shape-once [OcrTextMeasure] contract.
     */
    private fun unitWidth(
        from: Int,
        until: Int,
        size: Float,
    ): Float = (until - from) * size

    @Test
    fun layout_blankTextReturnsNull() {
        assertNull(OcrBlockLayout.layout("", listOf(100f), 10f, ::unitWidth))
        assertNull(OcrBlockLayout.layout("   ", listOf(100f), 10f, ::unitWidth))
    }

    @Test
    fun layout_withoutLineWidthsReturnsNull() {
        assertNull(OcrBlockLayout.layout("text", emptyList(), 10f, ::unitWidth))
    }

    @Test
    fun layout_fittingTextStaysOnOneLineAtStartSize() {
        val result = OcrBlockLayout.layout("hi", listOf(100f), 10f, ::unitWidth)

        assertEquals(listOf("hi"), result?.segments)
        assertEquals(10f, result?.textSize)
    }

    @Test
    fun layout_trimsInputText() {
        val result = OcrBlockLayout.layout("  hi  ", listOf(100f), 10f, ::unitWidth)

        assertEquals(listOf("hi"), result?.segments)
    }

    @Test
    fun layout_wrapsAtSpaceRunsAndConsumesTheSpaces() {
        val result = OcrBlockLayout.layout("aa bb", listOf(25f, 25f), 10f, ::unitWidth)

        assertEquals(listOf("aa", "bb"), result?.segments)
        assertEquals(10f, result?.textSize)
    }

    @Test
    fun layout_collapsesASpaceRunIntoASingleBreak() {
        val result = OcrBlockLayout.layout("a   b", listOf(15f, 40f), 10f, ::unitWidth)

        assertEquals(listOf("a", "b"), result?.segments)
    }

    @Test
    fun layout_unusedTrailingLinesBecomeEmptySegments() {
        val result = OcrBlockLayout.layout("hi", listOf(100f, 100f, 100f), 10f, ::unitWidth)

        assertEquals(listOf("hi", "", ""), result?.segments)
    }

    @Test
    fun layout_shrinksUntilLinesFit() {
        val result = OcrBlockLayout.layout("aaaa bbbb", listOf(35f, 45f), 10f, ::unitWidth)

        assertEquals(listOf("aaaa", "bbbb"), result?.segments)
        assertEquals(8f, result?.textSize)
    }

    @Test
    fun layout_unfittableTextCramsOntoTheLastLineAtMinimumSize() {
        val result = OcrBlockLayout.layout("aa bb", listOf(22f), 10f, ::unitWidth)

        assertEquals(listOf("aa bb"), result?.segments)
        assertEquals(OcrBlockLayout.MIN_TEXT_SIZE, result?.textSize)
    }

    @Test
    fun layout_farOverflowingTextStillResolvesThroughTheJumpEstimate() {
        val text = "aa bb cc dd ee ff gg hh ii jj"

        val result = OcrBlockLayout.layout(text, listOf(50f, 50f), 10f, ::unitWidth)

        assertEquals(listOf("aa bb", "cc dd ee ff gg hh ii jj"), result?.segments)
        assertEquals(8f, result?.textSize)
    }

    @Test
    fun layout_breaksBetweenCjkCharactersWithoutSpaces() {
        val result = OcrBlockLayout.layout("あいうえお", listOf(25f, 25f), 10f, ::unitWidth)

        assertEquals(listOf("あいう", "えお"), result?.segments)
        assertEquals(8f, result?.textSize)
    }

    @Test
    fun layout_neverBreaksBeforeKinsokuClosingPunctuation() {
        val result = OcrBlockLayout.layout("ああ。う", listOf(25f, 40f), 10f, ::unitWidth)

        assertEquals(listOf("あ", "あ。う"), result?.segments)
    }

    @Test
    fun layout_neverBreaksAfterKinsokuOpeningBracket() {
        val result = OcrBlockLayout.layout("あ「いう", listOf(25f, 40f), 10f, ::unitWidth)

        assertEquals(listOf("あ", "「いう"), result?.segments)
    }

    @Test
    fun isCjkBreakable_coversCjkBlocksAndRejectsLatin() {
        assertTrue(OcrBlockLayout.isCjkBreakable('あ'.code))
        assertTrue(OcrBlockLayout.isCjkBreakable('一'.code))
        assertTrue(OcrBlockLayout.isCjkBreakable('ｱ'.code))
        assertFalse(OcrBlockLayout.isCjkBreakable('A'.code))
    }
}
