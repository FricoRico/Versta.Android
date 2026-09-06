package app.versta.translate.core.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.compose.ui.graphics.toArgb
import app.versta.translate.core.entity.BakedOverlay
import app.versta.translate.core.entity.CameraTranslationResult
import app.versta.translate.core.entity.FontWeight
import app.versta.translate.core.entity.OcrBlockLayoutCache
import app.versta.translate.core.entity.OcrBlockRender
import app.versta.translate.core.entity.OCR_TEXT_HORIZONTAL_INSET
import app.versta.translate.core.entity.OcrLineQuad
import app.versta.translate.core.entity.lineQuadOf
import app.versta.translate.utils.mapPoints
import app.versta.translate.utils.mapToArray
import java.nio.ByteBuffer

/**
 * Rasters the whole live overlay (erased-background strips + translated
 * text) into ONE premultiplied-RGBA bitmap in the anchor's CANONICAL frame
 * space. The GL pass rewarps that single texture per frame by the tracker's
 * homography, so baking happens only when content changes (fresh strips,
 * new translations) — reference: translator-rs render_overlay_to_texture,
 * rebaked per content version.
 *
 * [inverseHomography] is this frame's pose inverted (frame→canonical): the
 * current line/strip quads are pulled back to their canonical placement —
 * the space the texture's texels are addressed in per frame.
 *
 * Called on the GL viewfinder thread only.
 */
class OcrOverlayBaker {

    private val _layoutCache = OcrBlockLayoutCache()
    private val _stripPaint = OcrBlockRender.stripPaint()

    fun bake(
        blocks: List<CameraTranslationResult>,
        translations: Map<String, String>,
        inverseHomography: FloatArray,
        frameWidth: Int,
        frameHeight: Int,
    ): BakedOverlay? {
        if (blocks.isEmpty() || frameWidth <= 0 || frameHeight <= 0) return null

        val toCanonical = Matrix().apply { setValues(inverseHomography) }
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val blockQuads = blocks.mapNotNull { block ->
            val quads = block.lines.mapNotNull { line ->
                lineQuadOf(toCanonical.mapPoints(line.points.asList()))?.let { line to it }
            }
            if (quads.isEmpty()) null else block to quads
        }

        // Background pass first, text pass second, across ALL blocks: strips
        // carry tall pads that overlap neighbouring blocks, and interleaving
        // strips+text per block would let a later block's background cover
        // an earlier block's glyphs.
        blockQuads.forEach { (_, quads) ->
            quads.forEach { (line, _) ->
                val strip = line.strip ?: return@forEach
                val dst = toCanonical.mapToArray(strip.points)
                canvas.drawBitmap(strip.bitmap, OcrBlockRender.stripMatrix(strip.bitmap, dst), _stripPaint)
            }
        }

        blockQuads.forEach { (block, quads) ->
            val translated = (translations[block.source] ?: "").trim()
            if (translated.isEmpty()) return@forEach

            val paint = OcrBlockRender.blockTextPaint(
                bold = quads.any { it.first.fontWeight == FontWeight.BOLD }
            )
            // Lazily shape-once: a layout-cache hit never measures at all.
            val measurer = lazy { OcrBlockRender.measureAtReference(paint, translated) }
            val layout = _layoutCache.layout(
                text = translated,
                lineWidths = OcrBlockRender.blockLineWidths(quads.map { it.second }),
                startSize = OcrBlockRender.blockStartSize(quads.map { it.second }),
                measure = { from, until, size -> measurer.value.measure(from, until, size) },
            ) ?: return@forEach

            paint.textSize = layout.textSize
            quads.forEachIndexed { index, (line, quad) ->
                val segment = layout.segments.getOrElse(index) { "" }
                if (segment.isEmpty()) return@forEachIndexed

                paint.color = line.colors.foreground.toArgb()
                canvas.save()
                canvas.concat(OcrBlockRender.textMatrix(quad))
                canvas.drawText(segment, OCR_TEXT_HORIZONTAL_INSET,
                    OcrBlockRender.centeredBaselineY(paint, quad.bandHeight), paint)
                canvas.restore()
            }
        }

        val out = ByteArray(frameWidth * frameHeight * 4)
        bitmap.copyPixelsToBuffer(ByteBuffer.wrap(out))
        bitmap.recycle()
        // Bitmap pixels are premultiplied in memory order R,G,B,A — the strip
        // packing contract and the GL pass's ONE, ONE_MINUS_SRC_ALPHA blend.
        return BakedOverlay(out, frameWidth, frameHeight)
    }
}
