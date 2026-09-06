package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextMeasureTest {

    /** Monospace reference measure: every character takes half an em. */
    private fun measurer(text: String) = OcrTextMeasure(text) { token ->
        token.length * 0.5f * OcrTextMeasure.REFERENCE_SIZE
    }

    @Test
    fun wholeTextMeasuresLikeSummedTokens() {
        val measure = measurer("hello world")

        assertEquals(
            5.5f * OcrTextMeasure.REFERENCE_SIZE,
            measure.measure(0, "hello world".length, OcrTextMeasure.REFERENCE_SIZE),
            0.001f
        )
    }

    @Test
    fun widthsScaleLinearlyWithSize() {
        val measure = measurer("hello world")

        val at100 = measure.measure(0, 11, 100f)
        val at50 = measure.measure(0, 11, 50f)

        assertEquals(at100 / 2f, at50, 0.001f)
    }

    @Test
    fun wordBoundariesSumTokenWidths() {
        val measure = measurer("the quick fox")

        // "the" = 0..3, space = 3..4, "quick" = 4..9
        assertEquals(1.5f * 100f, measure.measure(0, 3, 100f), 0.001f)
        assertEquals(2.5f * 100f, measure.measure(4, 9, 100f), 0.001f)
    }

    @Test
    fun cjkCharactersAreSingleTokenBoundaries() {
        val text = "そうです。今"
        val measure = measurer(text)

        // Every character is a token boundary: 2 chars at 50 each.
        assertEquals(2f * 0.5f * 100f, measure.measure(1, 3, 100f), 0.001f)
    }

    @Test
    fun spaceRunsAreOneToken() {
        val measure = measurer("a  b")

        // The wrap consumes the run: "a" is 0..1, "b" starts at 3.
        assertEquals(0.5f * 100f, measure.measure(0, 1, 100f), 0.001f)
        assertEquals(0.5f * 100f, measure.measure(3, 4, 100f), 0.001f)
        assertEquals(1.5f * 100f, measure.measure(0, 3, 100f), 0.001f)
    }

    @Test
    fun layoutWrapsThroughTheTokenCache() {
        val text = "the quick brown fox jumps"
        val measure = measurer(text)

        val layout = OcrBlockLayout.layout(
            text = text,
            lineWidths = listOf(45f, 75f),
            startSize = 20f,
            measure = { from, until, size -> measure.measure(from, until, size) },
        )

        assertEquals(listOf("the quick", "brown fox jumps"), layout!!.segments)
        assertEquals(9.6f, layout.textSize, 0.01f)
    }
}
