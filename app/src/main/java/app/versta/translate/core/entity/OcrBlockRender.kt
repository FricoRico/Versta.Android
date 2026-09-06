package app.versta.translate.core.entity

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint

/** Horizontal inset applied to translated text inside its line band. */
const val OCR_TEXT_HORIZONTAL_INSET = 8f

/**
 * Shared overlay rendering helpers for the live GL baker and the stills
 * canvas — both raster the same concept (erased strips + wrapped translated
 * text over line quads), just in different output spaces.
 */
object OcrBlockRender {

    /** One bold decision shapes the whole block — bold is wider, so mixed
     *  runs never overflow (reference: layout_per_line's conservative bold
     *  pick). Weight resolution picks a real bold face where the family has
     *  one, not the synthetic stroke. */
    fun blockTextPaint(bold: Boolean): TextPaint = TextPaint().apply {
        isAntiAlias = true
        typeface = if (bold) Typeface.create(Typeface.DEFAULT, 700, false) else Typeface.DEFAULT
    }

    /** Start size: the tallest line's band (reference: clamp(max visual
     *  line height) — NOT the mean; a mixed-height block must not start
     *  undersized). The fit loop shares one size across the block. */
    fun blockStartSize(quads: List<OcrLineQuad>): Float = quads.maxOf { it.bandHeight }

    /** The translated text sits inside the paper sheet with a horizontal
     *  inset instead of flush to the quad. */
    fun blockLineWidths(quads: List<OcrLineQuad>): List<Float> = quads.map {
        (it.width - 2 * OCR_TEXT_HORIZONTAL_INSET).coerceAtLeast(1f)
    }

    /** TextPaint armed at the reference size for shape-once measuring —
     *  cache hits never measure at all. */
    fun measureAtReference(paint: TextPaint, text: String): OcrTextMeasure {
        paint.textSize = OcrTextMeasure.REFERENCE_SIZE
        return OcrTextMeasure(text) { paint.measureText(it) }
    }

    /** Erased-strip paint (sampled bitmap, premultiplied). */
    fun stripPaint(): Paint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Bitmap → placement-quad matrix. Content-verified against native
     *  dumps: the bitmap's top row maps onto the quad's top edge (natural
     *  source-corner order). */
    fun stripMatrix(bitmap: Bitmap, dst: FloatArray): Matrix = Matrix().apply {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
    }

    /** Local band rect → the quad's corners: projective text baking the
     *  page's perspective in. */
    fun textMatrix(quad: OcrLineQuad): Matrix {
        val pts = quad.points
        return Matrix().apply {
            setPolyToPoly(
                floatArrayOf(
                    0f, 0f,
                    quad.width, 0f,
                    quad.width, quad.bandHeight,
                    0f, quad.bandHeight,
                ), 0,
                floatArrayOf(
                    pts[0].x, pts[0].y,
                    pts[1].x, pts[1].y,
                    pts[2].x, pts[2].y,
                    pts[3].x, pts[3].y,
                ), 0, 4,
            )
        }
    }

    /** Vertically centered baseline for the current paint inside the band. */
    fun centeredBaselineY(paint: TextPaint, bandHeight: Float): Float {
        val metrics = paint.fontMetrics
        return bandHeight / 2f - (metrics.ascent + metrics.descent) / 2f
    }
}
