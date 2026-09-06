package app.versta.translate.core.model

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.ui.graphics.Color
import app.versta.translate.core.entity.CameraTranslationBlockLine
import app.versta.translate.core.entity.CameraTranslationResult
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.OcrRenderStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrOverlayBakerTest {

    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun quad(l: Float, t: Float, r: Float, b: Float) = arrayOf(
        PointF(l, t), PointF(r, t), PointF(r, b), PointF(l, b)
    )

    private fun pixel(b: ByteArray, w: Int, x: Int, y: Int): IntArray {
        val i = (y * w + x) * 4
        return intArrayOf(
            b[i].toInt() and 0xFF, b[i + 1].toInt() and 0xFF,
            b[i + 2].toInt() and 0xFF, b[i + 3].toInt() and 0xFF
        )
    }

    private fun opaquePixels(b: ByteArray, w: Int, h: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until h) for (x in 0 until w) {
            if (b[(y * w + x) * 4 + 3] != 0.toByte()) out += x to y
        }
        return out
    }

    @Test
    fun textPaintsInsideTheQuadWithForegroundColor() {
        val line = CameraTranslationBlockLine(
            points = quad(10f, 10f, 150f, 34f),
            colors = ObjectCharacterRecogniserColors(
                background = Color.White,
                foreground = Color.Black
            ),
        )
        val bake = OcrOverlayBaker().bake(
            blocks = listOf(CameraTranslationResult("Hello world", listOf(line))),
            translations = mapOf("Hello world" to "Bonjour monde"),
            inverseHomography = identity,
            frameWidth = 200, frameHeight = 60,
        )
        assertNotNull(bake)
        bake!!

        val opaque = opaquePixels(bake.bytes, bake.width, bake.height)
        assertTrue("text painted something", opaque.isNotEmpty())
        assertTrue(
            "text stays inside the line band",
            opaque.all { (x, y) -> x in 5..170 && y in 5..40 }
        )
        // Black text premultiplies to dark RGB at full coverage.
        val solid = opaque.first { (x, y) -> pixel(bake.bytes, bake.width, x, y)[3] > 200 }
        val rgb = pixel(bake.bytes, bake.width, solid.first, solid.second)
        assertTrue(rgb[0] < 0x30 && rgb[1] < 0x30 && rgb[2] < 0x30)
    }

    @Test
    fun stripPixelsLandOnTheStripQuadOnly() {
        val stripBitmap = Bitmap.createBitmap(20, 8, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFCC2200.toInt())
        }
        val line = CameraTranslationBlockLine(
            points = quad(30f, 10f, 70f, 18f),
            colors = ObjectCharacterRecogniserColors(Color.White, Color.Black),
            strip = OcrRenderStrip(
                bitmap = stripBitmap,
                points = quad(30f, 10f, 70f, 18f).toList(),
            ),
        )
        val bake = OcrOverlayBaker().bake(
            blocks = listOf(CameraTranslationResult("ab", listOf(line))),
            translations = emptyMap(),
            inverseHomography = identity,
            frameWidth = 100, frameHeight = 40,
        )!!

        val mid = pixel(bake.bytes, bake.width, 50, 14)
        assertEquals(0xCC, mid[0]); assertEquals(0x22, mid[1]); assertEquals(0x00, mid[2])
        assertEquals(0xFF, mid[3])
        assertEquals(0, pixel(bake.bytes, bake.width, 90, 30)[3])
    }

    @Test
    fun inverseHomographyPullsContentBackToCanonical() {
        // Short text that always fits unshrunk and unclipped, so the two
        // bakes differ by a pure translation of the same pixels.
        fun centroid(homography: FloatArray): Double {
            val line = CameraTranslationBlockLine(
                points = quad(60f, 5f, 160f, 29f),
                colors = ObjectCharacterRecogniserColors(Color.White, Color.Black),
            )
            val bake = OcrOverlayBaker().bake(
                blocks = listOf(CameraTranslationResult("Hello", listOf(line))),
                translations = mapOf("Hello" to "Yo"),
                inverseHomography = homography,
                frameWidth = 200, frameHeight = 60,
            )!!
            val xs = opaquePixels(bake.bytes, bake.width, bake.height).map { it.first }
            return xs.average()
        }

        // A frame→canonical pullback of −40 px (the scene moved +40 px right)
        // bakes the content ~40 px left of the identity bake.
        val shift = floatArrayOf(1f, 0f, -40f, 0f, 1f, 0f, 0f, 0f, 1f)
        val base = centroid(identity)
        val moved = centroid(shift)
        assertEquals(base - 40, moved, 6.0)
    }

    @Test
    fun aLaterBlocksBackgroundNeverCoversAnEarlierBlocksText() {
        // Block B is background-only (no translation): its solid red strip
        // covers the whole of A's text band (minus a thin margin), so any
        // B-after-A draw order buries A's glyphs under it.
        val stripBitmap = Bitmap.createBitmap(100, 28, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFC03000.toInt())
        }
        val blockA = CameraTranslationResult(
            "Hello world",
            listOf(
                CameraTranslationBlockLine(
                    points = quad(20f, 10f, 140f, 34f),
                    colors = ObjectCharacterRecogniserColors(Color.White, Color.Black),
                )
            ),
        )
        val blockB = CameraTranslationResult(
            "Below",
            listOf(
                CameraTranslationBlockLine(
                    points = quad(24f, 12f, 136f, 40f),
                    colors = ObjectCharacterRecogniserColors(Color.White, Color.Black),
                    strip = OcrRenderStrip(
                        bitmap = stripBitmap,
                        points = quad(24f, 12f, 136f, 40f).toList(),
                    ),
                )
            ),
        )
        val bake = OcrOverlayBaker().bake(
            blocks = listOf(blockA, blockB),
            translations = mapOf("Hello world" to "Bonjour le monde"),
            inverseHomography = identity,
            frameWidth = 200, frameHeight = 60,
        )!!

        var glyphPx = 0
        var redPx = 0
        for (y in 12 until 34) for (x in 30 until 130) {
            val p = pixel(bake.bytes, bake.width, x, y)
            if (p[3] == 0) continue
            if (p[0] < 0x30 && p[1] < 0x30 && p[2] < 0x30) glyphPx++
            else if (p[0] > 0xA0 && p[1] < 0x70 && p[2] < 0x40) redPx++
        }
        val allOpaque = opaquePixels(bake.bytes, bake.width, bake.height).size
        assertTrue(
            "text must stay on top of the neighbouring background (glyphPx=$glyphPx, redPx=$redPx, opaque=$allOpaque)",
            glyphPx > 0
        )
        assertTrue("neighbouring background must still fill around glyphs", redPx > 0)
    }

    @Test
    fun noContentProducesNoBake() {
        assertNull(
            OcrOverlayBaker().bake(
                blocks = emptyList(),
                translations = emptyMap(),
                inverseHomography = identity,
                frameWidth = 100, frameHeight = 40,
            )
        )
    }
}
