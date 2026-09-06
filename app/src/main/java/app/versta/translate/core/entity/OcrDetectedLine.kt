package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.compose.ui.graphics.Color

/**
 * Raw OCR engine output for one text line. Constructed from JNI — the
 * constructor signature and parameter order are part of the JNI contract.
 */
class OcrDetectedLine(
    points: FloatArray,
    text: String,
    score: Float,
    fgColor: Int,
    bgColor: Int,
    bold: Boolean,
    blockId: Int,
    stripBytes: ByteArray?,
    stripWidth: Int,
    stripHeight: Int,
    stripCorners: FloatArray?
) {
    val box: OcrTextBox = OcrTextBox(
        points = Array(4) { PointF(points[it * 2], points[it * 2 + 1]) },
        score = score
    )
    val text: String = text
    val score: Float = score
    val colors: ObjectCharacterRecogniserColors = ObjectCharacterRecogniserColors(
        background = Color(bgColor),
        foreground = Color(fgColor)
    )
    val bold: Boolean = bold
    val blockId: Int = blockId

    /**
     * Erased-background patch: pixels cross JNI only on the acquire that built
     * them (null bytes on tracked frames); [stripCorners] always tracks.
     */
    val strip: OcrErasedStrip? = if (stripCorners != null && stripWidth > 0 && stripHeight > 0) {
        OcrErasedStrip(
            width = stripWidth,
            height = stripHeight,
            points = Array(4) { PointF(stripCorners[it * 2], stripCorners[it * 2 + 1]) },
            bytes = stripBytes,
        )
    } else {
        null
    }

    fun toLineResult(): OcrLineResult = OcrLineResult(
        box = box,
        text = text,
        score = score,
        colors = colors,
        bold = bold,
        blockId = blockId,
        strip = strip,
    )
}
