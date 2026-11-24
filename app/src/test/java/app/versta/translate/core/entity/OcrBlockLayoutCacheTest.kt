package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization of the sticky live-overlay wrap cache: wraps keyed by text
 * and line count survive per-frame quad jitter inside the tolerance band,
 * refit symmetrically past it, and the LRU retires the eldest entry once the
 * cache outgrows it.
 */
class OcrBlockLayoutCacheTest {
    private class MeasureCounter {
        var calls = 0

        val measure: (from: Int, until: Int, size: Float) -> Float = { from, until, size ->
            calls++
            (until - from) * size
        }
    }

    @Test
    fun layout_reusesCachedWrapWhileWidthsJitterWithinTolerance() {
        val cache = OcrBlockLayoutCache()
        val counter = MeasureCounter()

        val first = cache.layout("aa bb", listOf(25f, 25f), 10f, counter.measure)
        val afterFirst = counter.calls
        assertTrue(afterFirst > 0)

        val second = cache.layout("aa bb", listOf(25.5f, 25f), 10f, counter.measure)

        assertEquals(afterFirst, counter.calls)
        assertEquals(first?.segments, second?.segments)
        assertEquals(first?.textSize, second?.textSize)
    }

    @Test
    fun layout_refitsWhenWidthMovesPastToleranceAndCachesTheRefit() {
        val cache = OcrBlockLayoutCache()
        val counter = MeasureCounter()

        cache.layout("aa bb", listOf(25f, 25f), 10f, counter.measure)
        val afterFirst = counter.calls

        val refit = cache.layout("aa bb", listOf(30f, 25f), 10f, counter.measure)
        val afterRefit = counter.calls
        assertTrue(afterRefit > afterFirst)
        assertEquals(listOf("aa", "bb"), refit?.segments)

        cache.layout("aa bb", listOf(30f, 25f), 10f, counter.measure)
        assertEquals(afterRefit, counter.calls)
    }

    @Test
    fun layout_keysOnTextAndLineCount() {
        val cache = OcrBlockLayoutCache()
        val counter = MeasureCounter()

        val two = cache.layout("aa bb", listOf(25f, 25f), 10f, counter.measure)
        val afterTwo = counter.calls

        val three = cache.layout("aa bb", listOf(25f, 25f, 25f), 10f, counter.measure)
        assertTrue(counter.calls > afterTwo)
        assertEquals(listOf("aa", "bb", ""), three?.segments)
        val afterThree = counter.calls

        val twoAgain = cache.layout("aa bb", listOf(25f, 25f), 10f, counter.measure)
        assertEquals(afterThree, counter.calls)
        assertEquals(two?.segments, twoAgain?.segments)
    }

    @Test
    fun layout_evictsEldestEntryOnceCapacityIsExceeded() {
        val cache = OcrBlockLayoutCache()
        val counter = MeasureCounter()

        for (i in 0..32) {
            cache.layout("text$i", listOf(100f), 10f, counter.measure)
        }

        val before = counter.calls
        cache.layout("text0", listOf(100f), 10f, counter.measure)
        assertTrue(counter.calls > before)

        val afterReinsert = counter.calls
        cache.layout("text2", listOf(100f), 10f, counter.measure)
        assertEquals(afterReinsert, counter.calls)
    }

    @Test
    fun layout_unwrappableTextReturnsNullWithoutCaching() {
        val cache = OcrBlockLayoutCache()
        val counter = MeasureCounter()

        assertNull(cache.layout("", listOf(100f), 10f, counter.measure))
        assertEquals(0, counter.calls)
    }
}
