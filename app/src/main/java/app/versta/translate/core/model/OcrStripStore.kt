package app.versta.translate.core.model

import android.graphics.Bitmap
import android.graphics.PointF
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import app.versta.translate.core.entity.OcrRenderStrip
import java.nio.ByteBuffer

/**
 * Frame-consistent store of erased-background patches: an acquire delivers
 * the strip pixels, tracked frames re-pose the placement corners only. The
 * caller resets the store ONCE per frame that delivered pixels ([reset] on
 * any line with bytes) — block ids only have meaning within one anchor.
 * Lines that stop referencing a strip drop their cached bitmap so stale
 * patches never ghost across scenes.
 *
 * Reference: translator-rs live path — matted strips are content of the
 * anchor (built once, warped per frame), refresh ticks re-pose geometry.
 */
class OcrStripStore {

    private class Entry(val bitmap: Bitmap, var points: List<PointF>)

    private val entries = LinkedHashMap<String, Entry>()

    /** Drop every cached patch: a fresh acquire's pixels invalidate the pool. */
    fun reset() = entries.clear()

    /**
     * Attach patches to [lines] (one block's lines in reading order, keyed by
     * [blockId] + slot index). Returns one render payload per line, or null
     * where the line has no strip.
     */
    fun accept(blockId: Int, lines: List<ObjectCharacterRecogniserResult>): List<OcrRenderStrip?> {
        val out = ArrayList<OcrRenderStrip?>(lines.size)
        for ((index, line) in lines.withIndex()) {
            val key = "$blockId#$index"
            val strip = line.strip

            when {
                strip == null -> {
                    entries.remove(key)
                    out += null
                }
                strip.bytes != null -> {
                    val bitmap = Bitmap.createBitmap(strip.width, strip.height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(strip.bytes))
                    val entry = Entry(bitmap, strip.points.toList())
                    entries[key] = entry
                    out += OcrRenderStrip(entry.bitmap, entry.points)
                }
                else -> {
                    val entry = entries[key]
                    if (entry == null || entry.bitmap.width != strip.width || entry.bitmap.height != strip.height) {
                        entries.remove(key)
                        out += null
                    } else {
                        entry.points = strip.points.toList()
                        out += OcrRenderStrip(entry.bitmap, entry.points)
                    }
                }
            }
        }
        return out
    }
}
