package app.versta.translate.adapter.outbound

import android.graphics.PointF
import androidx.compose.ui.graphics.Color
import app.versta.translate.core.entity.FontWeight
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.OcrLineResult
import app.versta.translate.core.entity.OcrTextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectCharacterRecognitionAnalyzerMappingTest {

    private fun lineResult(
        points: Array<PointF>,
        bold: Boolean = false,
        colors: ObjectCharacterRecogniserColors? = null,
        blockId: Int = -1
    ) =
        OcrLineResult(
            box = OcrTextBox(points = points, score = 0.9f),
            text = "hello",
            score = 0.9f,
            bold = bold,
            colors = colors,
            blockId = blockId
        )

    @Test
    fun boldMapsToFontWeight() {
        val points = arrayOf(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )

        assertEquals(FontWeight.BOLD, mapOcrLineResult(lineResult(points, bold = true)).fontWeight)
        assertEquals(FontWeight.REGULAR, mapOcrLineResult(lineResult(points)).fontWeight)
    }

    @Test
    fun nullColorsFallBackToDefaults() {
        val points = arrayOf(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )

        val noColors = mapOcrLineResult(lineResult(points))
        assertEquals(Color.White, noColors.colors.background)
        assertEquals(Color.Black, noColors.colors.foreground)

        val tint = ObjectCharacterRecogniserColors(
            background = Color(0xFF112233),
            foreground = Color(0xFF44DDEE)
        )
        assertEquals(tint, mapOcrLineResult(lineResult(points, colors = tint)).colors)
    }

    @Test
    fun blockIdMapsThrough() {
        val points = arrayOf(
            PointF(0f, 0f), PointF(10f, 0f), PointF(10f, 10f), PointF(0f, 10f)
        )

        assertEquals(3, mapOcrLineResult(lineResult(points, blockId = 3)).blockId)
    }
}
