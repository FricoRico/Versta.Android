package app.versta.translate.core.entity

/**
 * Paragraph-block text wrapping: distributes a block's translated text across
 * its source lines' widths, shrinking the font one px at a time until it fits.
 * Port of translator-render/image_render.rs break_opportunities /
 * break_into_lines / layout_per_line: spaced scripts break at space runs (the
 * break consumes the spaces), CJK breaks between adjacent characters under
 * kinsoku (no_break_before/after tables below), and one bold decision shapes
 * the whole block (conservative for mixed runs).
 */
object OcrBlockLayout {

    class Result(val segments: List<String>, val textSize: Float)

    /**
     * Greedy wrap of [text] into the per-line [lineWidths] (screen px), one
     * segment per line. [measure] returns the width of [text].substring(from,
     * until) at the given text size — index-based (reference: image_render.rs
     * cum_em_at_byte) so a token-cached measurer ([OcrTextMeasure]) can answer
     * retries without re-shaping. Starts at [startSize], shrinks 1px per retry
     * down to MIN_TEXT_SIZE; text overflowing past the floor is crammed onto the
     * last line (drawing slightly wide beats dropping translated text).
     */
    fun layout(
        text: String,
        lineWidths: List<Float>,
        startSize: Float,
        measure: (from: Int, until: Int, size: Float) -> Float
    ): Result? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || lineWidths.isEmpty()) return null

        var size = startSize.coerceAtLeast(MIN_TEXT_SIZE)

        // Linear shrink-by-1 would iterate ~80 times on paragraph blocks (and
        // this runs per composited overlay frame). When the raw text width far
        // outruns the block's line capacity, one measure jumps straight to the
        // fit region. The raw total counts the spaces the wrap consumes, so
        // the estimate already lands conservative; the correction loop only
        // ever shrinks, so any further bias would stick as undersized text.
        val capacity = lineWidths.sum()
        if (capacity > 0f) {
            val total = measure(0, trimmed.length, size)
            if (total > capacity * JUMP_THRESHOLD) {
                size = (size * capacity / total).coerceIn(MIN_TEXT_SIZE, size)
            }
        }

        while (true) {
            breakIntoLines(trimmed, size, lineWidths, measure)?.let {
                return Result(it, size)
            }
            if (size <= MIN_TEXT_SIZE) {
                return Result(cramRemaining(trimmed, size, lineWidths, measure), size)
            }
            size -= 1f
        }
    }

    /**
     * Byte-offset-free port of break_into_lines: greedy fill of each target
     * width via the largest fitting break opportunity; null when the last
     * line can't hold the remainder.
     */
    internal fun breakIntoLines(
        text: String,
        size: Float,
        widths: List<Float>,
        measure: (from: Int, until: Int, size: Float) -> Float
    ): List<String>? {
        val opps = breakOpportunities(text)
        val out = ArrayList<String>(widths.size)
        var cursor = 0
        for ((idx, width) in widths.withIndex()) {
            val last = idx == widths.size - 1
            if (cursor >= text.length) {
                out += ""
                continue
            }
            if (measure(cursor, text.length, size) <= width) {
                out += text.substring(cursor)
                cursor = text.length
                continue
            }
            if (last) return null

            var chosen: BreakOpp? = null
            for (opp in opps) {
                if (opp.prefixEnd <= cursor) continue
                if (measure(cursor, opp.prefixEnd, size) <= width) {
                    chosen = opp
                } else {
                    break
                }
            }
            val opp = chosen ?: return null
            out += text.substring(cursor, opp.prefixEnd)
            cursor = opp.nextStart
        }
        return if (cursor < text.length) null else out
    }

    /// Same greedy pass but the last line takes whatever remains.
    private fun cramRemaining(
        text: String,
        size: Float,
        widths: List<Float>,
        measure: (from: Int, until: Int, size: Float) -> Float
    ): List<String> {
        breakIntoLines(text, size, widths.dropLast(1) + Float.MAX_VALUE, measure)?.let {
            return it
        }
        return List(widths.size - 1) { "" } + text
    }

    private data class BreakOpp(val prefixEnd: Int, val nextStart: Int)

    /**
     * UTF-16 indices at which [text] may wrap: at runs of spaces (the break
     * consumes them) and between adjacent characters where at least one side
     * is CJK and kinsoku permits it. Ascending by prefixEnd.
     */
    private fun breakOpportunities(text: String): List<BreakOpp> {
        val chars = mutableListOf<Pair<Int, Int>>() // (utf16 index, code point)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            chars += i to cp
            i += Character.charCount(cp)
        }

        val opps = mutableListOf<BreakOpp>()
        for ((index, pair) in chars.withIndex()) {
            val (at, cp) = pair
            if (cp == ' '.code) {
                if (index > 0 && chars[index - 1].second == ' '.code) continue
                var next = at + Character.charCount(cp)
                while (next < text.length && text[next] == ' ') next += 1
                opps += BreakOpp(prefixEnd = at, nextStart = next)
                continue
            }
            val next = chars.getOrNull(index + 1) ?: continue
            if (next.second == ' '.code) continue // the space itself is the opportunity
            if (cjkBreakAllowed(cp, next.second)) {
                opps += BreakOpp(prefixEnd = next.first, nextStart = next.first)
            }
        }
        return opps
    }

    internal fun isCjkBreakable(cp: Int): Boolean =
        cp in 0x3040..0x309F ||  // Hiragana
        cp in 0x30A0..0x30FF ||  // Katakana
        cp in 0x3400..0x4DBF ||  // CJK Unified Ext A
        cp in 0x4E00..0x9FFF ||  // CJK Unified
        cp in 0xF900..0xFAFF ||  // CJK Compatibility Ideographs
        cp in 0xFF66..0xFF9D     // Halfwidth Katakana

    /// Kinsoku: prohibited line-initial characters (closing brackets, trailing
    /// punctuation, small kana, sound/iteration marks).
    private val noBreakBefore = setOf(
        ')', ']', '}', '）', '］', '｝', '」', '』', '】', '〉', '》', '〕', '〗', '｣',
        '、', '。', '，', '．', '：', '；', '？', '！', '・', '…', '‥', '､', '｡',
        '”', '’', 'ー', 'ゝ', 'ゞ', '々', '〆',
        'ぁ', 'ぃ', 'ぅ', 'ぇ', 'ぉ', 'っ', 'ゃ', 'ゅ', 'ょ', 'ゎ',
        'ァ', 'ィ', 'ゥ', 'ェ', 'ォ', 'ッ', 'ャ', 'ュ', 'ョ', 'ヮ', 'ヵ', 'ヶ'
    ).map { it.code }.toSet()

    /// Kinsoku: prohibited line-final characters (opening brackets, quotes).
    private val noBreakAfter = setOf(
        '(', '[', '{', '（', '［', '｛', '「', '『', '【', '〈', '《', '〔', '〖', '｢',
        '“', '‘'
    ).map { it.code }.toSet()

    private fun cjkBreakAllowed(before: Int, after: Int): Boolean {
        if (!isCjkBreakable(before) && !isCjkBreakable(after)) return false
        return before !in noBreakAfter && after !in noBreakBefore
    }

    /// Raw-width/capacity ratio above which the shrink loop is skipped straight
    /// to the capacity estimate.
    private const val JUMP_THRESHOLD = 1.25f

    const val MIN_TEXT_SIZE = 8f
}
