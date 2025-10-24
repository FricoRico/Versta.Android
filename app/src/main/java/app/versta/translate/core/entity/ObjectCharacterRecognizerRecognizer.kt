package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color
import java.nio.Buffer

data class ObjectCharacterRecognizerRecognizerInput(
    val imageProxy: ImageProxy,
    val detectResultBuffer: Buffer,
    val recognizeInputBuffer: Buffer,
    val recognizeOutputBuffer: Buffer,
    val recognizeWidth: Int,
    val recognizeHeight: Int,
    val cropWidth: Int,
    val maxBatchSize: Int
)

data class ObjectCharacterRecognizerRecognizerOutput(
    val results: List<ObjectCharacterRecognitionResult>
)

data class ObjectCharacterRecognitionResult(
    val points: Array<PointF>,
    var score: Float = 0f,
    var tokens: LongArray = longArrayOf(),
    var text: String = "",
    var translated: String = "",
    var colors: ObjectCharacterRecognitionColors = ObjectCharacterRecognitionColors(Color.Black, Color.White)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObjectCharacterRecognitionResult

        if (!points.contentEquals(other.points)) return false
        if (score != other.score) return false
        if (!tokens.contentEquals(other.tokens)) return false
        if (text != other.text) return false
        if (translated != other.translated) return false
        if (colors != other.colors) return false

        return true
    }

    override fun hashCode(): Int {
        var result = points.contentHashCode()
        result = 31 * result + score.hashCode()
        result = 31 * result + tokens.contentHashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + translated.hashCode()
        result = 31 * result + colors.hashCode()
        return result
    }
}

data class ObjectCharacterRecognitionColors(
    val background: Color,
    val foreground: Color
)
