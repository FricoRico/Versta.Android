package app.versta.translate.core.model

import android.graphics.PointF
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import app.versta.translate.core.entity.OcrErasedStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class OcrStripStoreTest {

    private fun line(strip: OcrErasedStrip?) = ObjectCharacterRecogniserResult(
        text = "line",
        strip = strip,
    )

    private fun strip(x: Float, bytes: ByteArray? = null) = OcrErasedStrip(
        width = 2,
        height = 2,
        points = arrayOf(
            PointF(x, 0f), PointF(x + 2f, 0f), PointF(x + 2f, 2f), PointF(x, 2f)
        ),
        bytes = bytes,
    )

    @Test
    fun freshBytesDecodeAndAttach() {
        val store = OcrStripStore()
        val attached = store.accept(1, listOf(line(strip(0f, ByteArray(16) { 127 }))))

        assertEquals(2, attached[0]?.bitmap?.width)
    }

    @Test
    fun trackedCornersReuseTheCachedPatch() {
        val store = OcrStripStore()
        val acquired = store.accept(1, listOf(line(strip(0f, ByteArray(16) { 127 }))))

        val tracked = store.accept(1, listOf(line(strip(10f))))

        assertSame(acquired[0]?.bitmap, tracked[0]?.bitmap)
        assertEquals(10f, tracked[0]?.points?.get(0)?.x)
    }

    @Test
    fun resetClearsTheWholeStoreOncePerFrame() {
        val store = OcrStripStore()
        val first = store.accept(1, listOf(line(strip(0f, ByteArray(16) { 127 }))))

        // A new anchor delivers pixels again: the caller resets once per frame,
        // then every block re-attaches fresh.
        store.reset()
        val second = store.accept(1, listOf(line(strip(5f, ByteArray(16) { 200.toByte() }))))

        assertNotSame(first[0]?.bitmap, second[0]?.bitmap)
        assertEquals(5f, second[0]?.points?.get(0)?.x)
    }

    @Test
    fun twoBlocksInOneFrameKeepAllPatches() {
        // Regression: resetting per block inside a frame left only the last
        // block's lines patched.
        val store = OcrStripStore()
        store.reset()
        store.accept(1, listOf(line(strip(0f, ByteArray(16) { 127 }))))
        store.accept(2, listOf(line(strip(10f, ByteArray(16) { 200.toByte() }))))

        val tracked = store.accept(1, listOf(line(strip(1f)))) +
            store.accept(2, listOf(line(strip(11f))))

        assertEquals(1f, tracked[0]?.points?.get(0)?.x)
        assertEquals(11f, tracked[1]?.points?.get(0)?.x)
    }

    @Test
    fun lineWithoutStripDropsItsEntry() {
        val store = OcrStripStore()
        store.accept(1, listOf(line(strip(0f, ByteArray(16))), line(strip(20f, ByteArray(16)))))

        val next = store.accept(1, listOf(line(strip(1f)), line(null)))

        assertEquals(2, next.size)
        assertNull(next[1])
    }

    @Test
    fun trackedLineWithoutCachedEntryStaysEmpty() {
        val store = OcrStripStore()

        val attached = store.accept(3, listOf(line(strip(0f))))

        assertNull(attached[0])
    }
}
