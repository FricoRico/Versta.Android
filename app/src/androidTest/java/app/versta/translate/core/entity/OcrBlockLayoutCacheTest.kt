package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrBlockLayoutCacheTest {

    /** Monospace measure: every character takes half an em. */
    private val monospace: (Int, Int, Float) -> Float = { from, until, size ->
        (until - from) * 0.5f * size
    }

    @Test
    fun quadJitterReusesTheCachedWrap() {
        val cache = OcrBlockLayoutCache()
        val text = "the quick brown fox jumps"
        // width 45+75 = capacity 120: fits at size 20 split over two lines.
        val first = cache.layout(text, listOf(45f, 75f), 20f, monospace)!!

        // Tracker jitter: quads move 2% — the wrap must not move.
        val second = cache.layout(text, listOf(46f, 74f), 20f, monospace)!!

        assertEquals(first.segments, second.segments)
        assertEquals(first.textSize, second.textSize, 0.001f)
    }

    @Test
    fun changedTextRelayouts() {
        val cache = OcrBlockLayoutCache()
        cache.layout("the quick brown fox jumps", listOf(45f, 75f), 20f, monospace)

        val fresh = cache.layout("a different paragraph entirely now", listOf(45f, 75f), 20f, monospace)!!

        assertEquals("a different", fresh.segments.first())
    }

    @Test
    fun changedLineCountRelayouts() {
        val cache = OcrBlockLayoutCache()
        cache.layout("hello world", listOf(50f, 50f), 20f, monospace)

        val fresh = cache.layout("hello world", listOf(100f), 20f, monospace)!!

        assertEquals(listOf("hello world"), fresh.segments)
    }

    @Test
    fun quadsShrinkingPastToleranceRefitSmaller() {
        val cache = OcrBlockLayoutCache()
        val text = "the quick brown fox jumps"
        val first = cache.layout(text, listOf(45f, 75f), 20f, monospace)!!
        assertEquals(9.6f, first.textSize, 0.01f)

        // 10% narrower: above tolerance, refits — and must not grow above the
        // cached size, so a brief zoom-out can never bounce the text size up.
        val second = cache.layout(text, listOf(40f, 67f), 20f, monospace)!!
        assertTrue(second.textSize <= first.textSize)
    }

    @Test
    fun capacityGrowthPastTheJitterBandRefitsBigger() {
        val cache = OcrBlockLayoutCache()
        val text = "hello"
        // 5 chars x 0.5em x 20px = 50px vs 45px quad: shrink loop lands at 18.
        val first = cache.layout(text, listOf(45f), 20f, monospace)!!
        assertEquals(18f, first.textSize, 0.001f)

        // 8.9% wider: past the jitter tolerance, growth refits from the start
        // size — the symmetric ratchet lets zoom-back restore the size.
        val nudged = cache.layout(text, listOf(49f), 20f, monospace)!!
        assertTrue(nudged.textSize > first.textSize)

        // Over 2x wider: a real zoom reaches the start size.
        val zoomed = cache.layout(text, listOf(94f), 20f, monospace)!!
        assertEquals(20f, zoomed.textSize, 0.001f)
    }
}
