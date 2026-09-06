package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.compose.ui.graphics.Color

class ObjectCharacterRecogniserColors(
    val background: Color,
    val foreground: Color
) {
    companion object {
        /** Shared default when a line carries no matte-derived colors. */
        val DEFAULT = ObjectCharacterRecogniserColors(
            background = Color.White,
            foreground = Color.Black
        )
    }
}

class ObjectCharacterRecogniserResult(
    val points: Array<PointF> = arrayOf(),
    var score: Float = 0f,
    var text: String = "",
    var colors: ObjectCharacterRecogniserColors = ObjectCharacterRecogniserColors.DEFAULT,
    var fontWeight: FontWeight = FontWeight.REGULAR,
    var blockId: Int = -1,
    var strip: OcrErasedStrip? = null,
)

/**
 * A detected text box in image pixel space: quadrilateral corners (reading
 * order) and the detector score.
 */
data class OcrTextBox(
    val points: Array<PointF>,
    val score: Float = 0f
)

/**
 * Erased-background patch for one line: the (width x height) RGBA pixels ride
 * the JNI boundary only on the acquire that built them (bytes == null on
 * tracked frames); [points] is the padded render quad in frame pixel space,
 * re-posed per frame.
 */
class OcrErasedStrip(
    val width: Int,
    val height: Int,
    val points: Array<PointF>,
    val bytes: ByteArray?,
)

/**
 * One detected + recognized text line, in image pixel space. Typography
 * (colors, bold) comes from the glyph matte model when the bundle ships it.
 */
data class OcrLineResult(
    val box: OcrTextBox,
    val text: String,
    val score: Float,
    val colors: ObjectCharacterRecogniserColors? = null,
    val bold: Boolean = false,
    val blockId: Int = -1,
    val strip: OcrErasedStrip? = null,
)

/**
 * The output of a full OCR pass over one frame: detection + recognition of
 * every text line found, in the rotation-corrected frame's pixel space.
 */
data class OcrAnalysisResult(
    val lines: List<OcrLineResult>,
    val width: Int,
    val height: Int,
)
