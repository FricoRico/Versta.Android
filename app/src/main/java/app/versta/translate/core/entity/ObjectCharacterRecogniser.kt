package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.compose.ui.graphics.Color
import java.nio.ByteBuffer

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
    var lineHeight: Float = 0f,
    var fontWeight: FontWeight = FontWeight.REGULAR
) {
    fun estimatedLineHeight(): Float {
        val minY = points.minOfOrNull { it.y } ?: 0f
        val maxY = points.maxOfOrNull { it.y } ?: 0f
        return maxY - minY
    }

    init {
        if (lines.isEmpty() && text.isNotEmpty()) {
            lines = listOf(text)
        }
    }
}

data class DetectedRegion(
    val points: Array<PointF>
)

data class RecognizedRegion(
    val tokens: LongArray,
    val score: Float,
    val colors: ObjectCharacterRecogniserColors = ObjectCharacterRecogniserColors(
        background = Color.Black,
        foreground = Color.White
    ),
    val fontSize: Float = 0f,
    val lineHeight: Float = 0f,
    val fontWeight: Int = 0
)

data class DetectResult(
    val regions: List<DetectedRegion>,
    val resultBuffer: ByteBuffer
)

data class RecognizeResult(
    val regions: List<RecognizedRegion>
)

data class CombinedOcrResult(
    val regions: List<CombinedOcrRegion>
)

data class CombinedOcrRegion(
    val points: Array<PointF>,
    val tokens: LongArray,
    val score: Float,
    val colors: ObjectCharacterRecogniserColors,
    val fontSize: Float,
    val lineHeight: Float,
    val fontWeight: Int
)
