package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBlockLayoutTest {

    /** Monospace measure: every character takes half an em. */
    private val monospace: (Int, Int, Float) -> Float = { from, until, size ->
        (until - from) * 0.5f * size
    }

    @Test
    fun fittingTextKeepsStartSize() {
        val layout = OcrBlockLayout.layout(
            text = "hello",
            lineWidths = listOf(1000f),
            startSize = 20f,
            measure = monospace
        )

        assertNotNull(layout)
        assertEquals(listOf("hello"), layout!!.segments)
        assertEquals(20f, layout.textSize, 0.001f)
    }

    @Test
    fun wrapsOntoSecondLineAtSpaceRun() {
        val layout = OcrBlockLayout.layout(
            text = "hello world",
            lineWidths = listOf(50f, 50f),
            startSize = 20f,
            measure = monospace
        )

        assertNotNull(layout)
        assertEquals(listOf("hello", "world"), layout!!.segments)
        assertEquals(20f, layout.textSize, 0.001f)
    }

    @Test
    fun spaceRunsAreConsumedByTheBreak() {
        val layout = OcrBlockLayout.layout(
            text = "a  b",
            lineWidths = listOf(10f, 10f),
            startSize = 20f,
            measure = monospace
        )

        assertEquals(listOf("a", "b"), layout!!.segments)
    }

    @Test
    fun shrinksUntilTheParagraphFits() {
        // 0.5em monospace: 25 chars × 0.5em × 20px = 250px vs 45+75 capacity.
        val layout = OcrBlockLayout.layout(
            text = "the quick brown fox jumps",
            lineWidths = listOf(45f, 75f),
            startSize = 20f,
            measure = monospace
        )

        assertNotNull(layout)
        assertEquals(listOf("the quick", "brown fox jumps"), layout!!.segments)
        // Area-based estimate: 20 x (120/250) = 9.6, fitting on the first
        // correction instead of stepping 1px at a time.
        assertEquals(9.6f, layout.textSize, 0.01f)
    }

    @Test
    fun cjkBreaksBetweenCharactersUnderKinsoku() {
        // "。" may not open a line: it stays attached to the previous line.
        // 0.5em monospace: 5 chars are exactly 50px at size 20.
        val layout = OcrBlockLayout.layout(
            text = "そうです。今日",
            lineWidths = listOf(50f, 50f),
            startSize = 20f,
            measure = monospace
        )

        assertNotNull(layout)
        assertEquals(listOf("そうです。", "今日"), layout!!.segments)
    }

    @Test
    fun unsplittableTextIsCrammedAtFloorSize() {
        // One long token: no break opportunities exist, so the floor keeps the
        // text on the last line (overflowing beats dropping it).
        val layout = OcrBlockLayout.layout(
            text = "aaaaaaaaaa",
            lineWidths = listOf(10f, 20f),
            startSize = 20f,
            measure = monospace
        )

        assertNotNull(layout)
        assertEquals(2, layout!!.segments.size)
        assertEquals("aaaaaaaaaa", layout.segments.last())
        assertTrue(layout.textSize <= OcrBlockLayout.MIN_TEXT_SIZE)
    }

    @Test
    fun blankTextHasNoLayout() {
        assertEquals(null, OcrBlockLayout.layout("  ", listOf(100f), 20f, monospace))
        assertEquals(null, OcrBlockLayout.layout("text", emptyList(), 20f, monospace))
    }
}
