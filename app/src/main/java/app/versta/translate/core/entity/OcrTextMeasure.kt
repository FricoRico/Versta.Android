package app.versta.translate.core.entity

/**
 * Shape-once measurer for the overlay fit loop: tokenizes the text on the
 * wrap's break boundaries (space runs, single CJK chars — the same boundaries
 * [OcrBlockLayout] breaks on), measures each token once at [REFERENCE_SIZE],
 * and answers arbitrary boundary-aligned index ranges by scaling. Minikin
 * advances and kerning are font-unit based, so widths scale linearly with
 * text size; the per-retry cost of the fit loop drops to O(#breaks) lookups.
 *
 * Reference: translator-render/image_render.rs cum_em_at_byte — the whole
 * block is shaped once at em scale and px widths read off as em × size.
 */
class OcrTextMeasure(text: String, measureReference: (String) -> Float) {

    private val ends: IntArray      // token boundary indices, ascending; ends[0] = 0
    private val refWidths: FloatArray // refWidths[k] = cumulative ref width up to ends[k]

    init {
        val boundaries = ArrayList<Int>()
        val pieces = ArrayList<String>()
        var i = 0
        boundaries += 0
        while (i < text.length) {
            val start = i
            val cp = text.codePointAt(i)
            when {
                cp == ' '.code -> {
                    // One token per space run: the wrap consumes the whole run.
                    while (i < text.length && text[i] == ' ') i++
                }
                OcrBlockLayout.isCjkBreakable(cp) -> i += Character.charCount(cp)
                else -> {
                    while (i < text.length) {
                        val nextCp = text.codePointAt(i)
                        if (nextCp == ' '.code || OcrBlockLayout.isCjkBreakable(nextCp)) break
                        i += Character.charCount(nextCp)
                    }
                }
            }
            pieces += text.substring(start, i)
            boundaries += i
        }
        ends = boundaries.toIntArray()

        var cumulative = 0f
        refWidths = FloatArray(ends.size)
        for (k in pieces.indices) {
            cumulative += measureReference(pieces[k])
            refWidths[k + 1] = cumulative
        }
    }

    /**
     * Width of text.substring(from, until) at [size]. Both indices must be
     * token boundaries — guaranteed for the wrap's callers since breaks only
     * occur on boundaries; an unaligned index floors to the previous one
     * (slight underestimate, harmless for a fit estimate).
     */
    fun measure(from: Int, until: Int, size: Float): Float {
        if (until <= from) return 0f
        val ref = refWidths[bisect(until)] - refWidths[bisect(from)]
        return ref * size / REFERENCE_SIZE
    }

    private fun bisect(index: Int): Int {
        var at = ends.binarySearch(index)
        if (at < 0) at = -at - 2
        return at.coerceIn(0, ends.size - 1)
    }

    companion object {
        const val REFERENCE_SIZE = 100f
    }
}
