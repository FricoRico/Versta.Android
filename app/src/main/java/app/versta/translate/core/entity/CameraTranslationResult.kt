package app.versta.translate.core.entity

import android.graphics.Bitmap
import android.graphics.PointF

/**
 * Erased-background patch bound to one overlay line: the decoded bitmap (the
 * line's own pixels with ink filled by the block-median field) and the padded
 * placement quad in frame pixel space.
 */
class OcrRenderStrip(
    val bitmap: Bitmap,
    val points: List<PointF>,
)

/**
 * One source line slot inside a translated block: the line's render quad plus
 * the style the glyph matte measured on its ink, and the erased-background
 * patch that replaces the slab sheet behind it.
 */
class CameraTranslationBlockLine(
    val points: Array<PointF>,
    val colors: ObjectCharacterRecogniserColors,
    val fontWeight: FontWeight = FontWeight.REGULAR,
    val strip: OcrRenderStrip? = null,
)

/**
 * One paragraph block: the source it was translated from (the translation
 * itself is delivered asynchronously through
 * `CameraTranslationViewModel.blockTranslations`, keyed by this string) and
 * the source line slots in reading order, which the renderer wraps the
 * translation back across.
 */
class CameraTranslationResult(
    val source: String,
    val lines: List<CameraTranslationBlockLine>,
)

/**
 * The overlay (erased strips + translated text) baked into one
 * premultiplied-RGBA image in the anchor's canonical frame space; the GL
 * pass rewarps it per frame by the tracker homography, so a bake only
 * crosses when content changes, not per frame.
 */
data class BakedOverlay(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
)

/**
 * What one GL viewfinder frame's tracking tick hands back to the render
 * loop: the canonical→current-frame homography for the overlay composite
 * (9 floats, row-major) plus a fresh bake when content moved.
 * Null homography = no anchor held — skip the overlay pass.
 * [epoch] marks the analysis session the tick belongs to: the render loop
 * drops a previous session's retained overlay texture the moment it sees a
 * new epoch, instead of trusting the UI to have cleared it in time.
 */
data class LiveOverlayTick(
    val homography: FloatArray?,
    val bake: BakedOverlay?,
    val epoch: Long,
)
