package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.compose.ui.graphics.Color

class ObjectCharacterRecogniserColors(
    val background: Color,
    val foreground: Color
)

class ObjectCharacterRecogniserResult(
    val points: Array<PointF> = arrayOf(),
    var score: Float = 0f,
    var tokens: LongArray = longArrayOf(),
    var text: String = "",
    var colors: ObjectCharacterRecogniserColors = ObjectCharacterRecogniserColors(Color.Black, Color.White),
    var lines: List<String> = emptyList(),
    var fontSize: Float = 0f,
    var lineHeight: Float = 0f,  // NEW: Actual line height from analysis
    var fontWeight: FontWeight = FontWeight.REGULAR
) {
    /**
     * Estimates the line height from the bounding box.
     * Computed as the difference between max and min Y coordinates.
     */
    fun estimatedLineHeight(): Float {
        val minY = points.minOfOrNull { it.y } ?: 0f
        val maxY = points.maxOfOrNull { it.y } ?: 0f
        return maxY - minY
    }

    init {
        // If lines is empty, default to single line with text
        if (lines.isEmpty() && text.isNotEmpty()) {
            lines = listOf(text)
        }
    }
}
