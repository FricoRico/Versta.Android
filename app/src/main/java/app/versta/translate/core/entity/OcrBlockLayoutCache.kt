package app.versta.translate.core.entity

import kotlin.math.abs

/**
 * Sticky paragraph wrap for live overlays: a block's wrap (segments + text
 * size) is reused while its quad widths jitter within [WIDTH_TOLERANCE] —
 * greedy wrap breakpoints flip on 1-2 px changes, which shows up as words
 * hopping between lines on every tracker tick. Past the tolerance both
 * directions refit symmetrically: a downward-only ratchet shrinks text
 * permanently on quad noise (grow-back never fired), so growth is allowed
 * once capacity moves past the same jitter band.
 * Keyed by the translated text: anchor re-acquisitions keep the same cache.
 */
class OcrBlockLayoutCache {

    private class Stored(
        val segments: List<String>,
        val textSize: Float,
        val widths: List<Float>,
    )

    private val entries = object : LinkedHashMap<String, Stored>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Stored>?) =
            size > MAX_ENTRIES
    }

    fun layout(
        text: String,
        lineWidths: List<Float>,
        startSize: Float,
        measure: (from: Int, until: Int, size: Float) -> Float,
    ): OcrBlockLayout.Result? {
        // lineWidths.size is part of the key, so a hit always matches the
        // current line count.
        val key = "$text#${lineWidths.size}"
        val stored = entries[key]

        if (stored != null) {
            val sameWidths = lineWidths.zip(stored.widths).all { (now, was) ->
                abs(now - was) <= was * WIDTH_TOLERANCE
            }
            if (sameWidths) return OcrBlockLayout.Result(stored.segments, stored.textSize)

            val capacityGrew = lineWidths.sum() >
                stored.widths.sum() * (1 + WIDTH_TOLERANCE)
            val start = if (capacityGrew) startSize else minOf(stored.textSize, startSize)
            val fresh = OcrBlockLayout.layout(text, lineWidths, start, measure)
            if (fresh != null) {
                entries[key] = Stored(fresh.segments, fresh.textSize, lineWidths)
            }
            return fresh
        }

        val fresh = OcrBlockLayout.layout(text, lineWidths, startSize, measure) ?: return null
        entries[key] = Stored(fresh.segments, fresh.textSize, lineWidths)
        return fresh
    }

    companion object {
        /** Per-width jitter band: below it the cached wrap is reused as-is;
         *  past it refits in both directions (symmetric — see class KDoc). */
        const val WIDTH_TOLERANCE = 0.04f

        private const val MAX_ENTRIES = 32
    }
}
