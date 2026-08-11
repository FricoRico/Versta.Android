package app.versta.translate.ui.component

import androidx.compose.ui.graphics.Color
import app.versta.translate.utils.lightness
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshGradientBackgroundTest {

    @Test
    fun meshGradientColors_anyBase_returnsThreeDistinctColors() {
        val colors = meshGradientColors(Color.White)

        assertEquals(3, colors.size)
        assertEquals(3, colors.distinct().size)
    }

    @Test
    fun meshGradientColors_lightBase_returnsTintsBetweenBaseAndRawHue() {
        val base = Color(0xFFF8F9FF)
        val rawHues = listOf(Color(0xFF533AFD), Color(0xFFF96BEE), Color(0xFF8D47F7))
        val colors = meshGradientColors(base)

        colors.zip(rawHues).forEach { (accent, raw) ->
            assertTrue(accent.lightness() in raw.lightness()..base.lightness())
            assertEquals(1f, accent.alpha)
        }
    }

    @Test
    fun meshGradientColors_darkBase_returnsMutedGlowColors() {
        val colors = meshGradientColors(Color(0xFF0F1419))

        assertTrue(colors.all { it.lightness() < 0.6f })
        assertTrue(colors.all { it.alpha < 1f && it.alpha > 0f })
    }

    @Test
    fun meshGradientColors_darkBase_staysVisibleAgainstBase() {
        val base = Color(0xFF0A0909)
        val colors = meshGradientColors(base)

        assertTrue(colors.all { it.lightness() > base.lightness() })
    }

    @Test
    fun meshVertexColors_upperArea_isExactlyBase() {
        val base = Color(0xFFF8F9FF)
        val vertices = meshVertexColors(base, meshGradientColors(base))

        for (row in 0 until vertices.lastIndex - 1) {
            for (column in vertices[row].indices) {
                assertEquals(base, vertices[row][column])
            }
        }
        assertEquals(base, vertices[vertices.lastIndex - 1][1])
        assertEquals(base, vertices[vertices.lastIndex - 1][2])
    }

    @Test
    fun meshVertexColors_chromaStrength_formsShallowU() {
        val base = Color(0xFFF8F9FF)
        val vertices = meshVertexColors(base, meshGradientColors(base))
        val bottom = vertices.lastIndex

        val corner = vertices[bottom][0].distanceTo(base)
        val midEdge = vertices[bottom][1].distanceTo(base)
        val wing = vertices[bottom - 1][0].distanceTo(base)

        assertTrue(corner > midEdge)
        assertTrue(corner > wing)
        assertTrue(midEdge > 0f)
        assertTrue(wing > 0f)
    }

    @Test
    fun meshVertexColors_bottomCorners_areBothChromatic() {
        val base = Color(0xFFF8F9FF)
        val vertices = meshVertexColors(base, meshGradientColors(base))
        val bottom = vertices.lastIndex

        assertNotEquals(base, vertices[bottom][0])
        assertNotEquals(base, vertices[bottom][3])
        assertNotEquals(vertices[bottom][0], vertices[bottom][3])
    }

    @Test
    fun meshVertexColors_darkBase_tierAlphasDecay() {
        val base = Color(0xFF0F1419)
        val vertices = meshVertexColors(base, meshGradientColors(base))
        val bottom = vertices.lastIndex

        assertTrue(vertices[bottom][0].alpha > vertices[bottom - 1][0].alpha)
        assertTrue(vertices[bottom][0].alpha > vertices[bottom][1].alpha)
    }

    private fun Color.distanceTo(other: Color): Float =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)
}