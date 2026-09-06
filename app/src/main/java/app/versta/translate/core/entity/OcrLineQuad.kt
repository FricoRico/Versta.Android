package app.versta.translate.core.entity

import android.graphics.PointF
import kotlin.math.hypot

/**
 * One block line's 4-corner quad (reading-order TL, TR, BR, BL) with its
 * reading geometry: [width] is the start-edge→end-edge distance and
 * [bandHeight] the taller of the two side edges — used to wrap and size
 * translated text over the line.
 */
data class OcrLineQuad(
    val points: List<PointF>,
    val width: Float,
    val bandHeight: Float,
)

/** Reading geometry of one quad; null when the quad is degenerate. */
fun lineQuadOf(points: List<PointF>): OcrLineQuad? {
    if (points.size < 4) return null

    val tl = points[0]
    val tr = points[1]
    val br = points[2]
    val bl = points[3]

    val startX = (tl.x + bl.x) / 2f
    val startY = (tl.y + bl.y) / 2f
    val endX = (tr.x + br.x) / 2f
    val endY = (tr.y + br.y) / 2f

    val width = hypot(endX - startX, endY - startY)
    val bandH = maxOf(
        hypot(bl.x - tl.x, bl.y - tl.y),
        hypot(br.x - tr.x, br.y - tr.y)
    )
    if (width <= 1f || bandH <= 1f) return null

    return OcrLineQuad(points = points, width = width, bandHeight = bandH)
}
