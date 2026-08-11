package app.versta.translate.ui.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.MeshGradientPainter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import app.versta.translate.ui.theme.VerstaDarkColorScheme
import app.versta.translate.ui.theme.VerstaLightColorScheme
import app.versta.translate.utils.darken
import app.versta.translate.utils.lightness
import app.versta.translate.utils.shift
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val MeshIndigo = Color(0xFF533AFD)
private val MeshMagenta = Color(0xFFF96BEE)
private val MeshPurpleBridge = Color(0xFF8D47F7)

private const val MeshRows = 4
private const val MeshColumns = 3
private const val MeshDriftDurationMillis = 6000

private data class MeshDriftVertex(
    val row: Int,
    val column: Int,
    val anchorX: Float,
    val anchorY: Float,
    val amplitudeX: Float,
    val amplitudeY: Float,
    val phaseOffset: Float,
)

private val MeshDriftVertices = listOf(
    MeshDriftVertex(4, 1, anchorX = 0.33f, anchorY = 1f, amplitudeX = 0.05f, amplitudeY = 0f, phaseOffset = 0f),
    MeshDriftVertex(4, 2, anchorX = 0.67f, anchorY = 1f, amplitudeX = 0.05f, amplitudeY = 0f, phaseOffset = 3.14f),
)

/**
 * Computes the three mesh accent colors for the given surface [base].
 *
 * Light theme washes the accents toward white into readable tints. Dark theme starts from those
 * same white-referenced tints and darkens them in HSL — lowering lightness while keeping hue and
 * saturation — so the band stays colorful instead of fading to grey.
 */
internal fun meshGradientColors(base: Color): List<Color> {
    val tints = listOf(
        lerp(MeshIndigo, base, 0.35f),
        lerp(MeshMagenta, base, 0.35f),
        lerp(MeshPurpleBridge, base, 0.3f),
    )


    return if (base.lightness() > 0.5f) {
        tints
    } else {
        listOf(
            tints[0],
            tints[1],
            tints[2].shift(saturationFactor = 2f, lightnessFactor = 0.6f),
        )
    }
}

private fun soften(accent: Color, base: Color, factor: Float, alphaScale: Float): Color =
    lerp(accent, base, factor).copy(alpha = accent.alpha * alphaScale)

/**
 * Maps the mesh to colors per vertex (top-left first). The chroma forms a shallow U along the
 * bottom edge: indigo at the bottom-left corner, magenta at the bottom-right, a purple-pink bridge
 * (attenuated) dipping lower between them, and faint wings one row above the corners so the
 * corners extend slightly higher than the middle. Everything above that is exactly [base],
 * keeping the screen's upper area on the pure system color.
 */
internal fun meshVertexColors(base: Color, accents: List<Color>): List<List<Color>> {
    require(accents.size == 3)
    val (indigo, magenta, bridge) = accents

    val light = base.lightness() > 0.5f
    val midSoften = if (light) 0.3f to 1f else 0f to 0.7f
    val wingSoften = if (light) 0.7f to 1.6f else 0.7f to 2f

    return List(MeshRows + 1) { row ->
        List(MeshColumns + 1) { column ->
            when (row) {
                MeshRows if column == 0 -> indigo
                MeshRows if column == MeshColumns -> magenta
                MeshRows -> soften(bridge, base, midSoften.first, midSoften.second)
                MeshRows - 1 if column == 0 -> soften(indigo, base, wingSoften.first, wingSoften.second)
                MeshRows - 1 if column == MeshColumns -> soften(magenta, base, wingSoften.first, wingSoften.second)
                else -> base
            }
        }
    }
}

/**
 * App-wide atmospheric backdrop: a corner-anchored color wash along the bottom edge that dissolves
 * upward into the theme background color. Corner vertices stay pinned to the physical corners;
 * only the bottom-edge interior vertices drift. Rendered with a bicubic [MeshGradientPainter] so
 * color transitions stay smooth with no hard lines.
 *
 * Requires androidx.compose.ui:ui >= 1.12.0-rc01.
 */
@Composable
fun MeshGradientBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val accents = remember(background) { meshGradientColors(background) }
    val vertexColors = remember(background, accents) { meshVertexColors(background, accents) }

    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    val phaseState: State<Float> = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "meshGradientDrift")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(MeshDriftDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "meshGradientPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val painter = remember(vertexColors) {
        MeshGradientPainter(
            rows = MeshRows,
            columns = MeshColumns,
            hasBicubicColor = true,
        ) {
            val phase = phaseState.value

            for (row in 0..MeshRows) {
                for (column in 0..MeshColumns) {
                    setVertex(
                        row = row,
                        column = column,
                        position = Offset(
                            column / MeshColumns.toFloat(),
                            row / MeshRows.toFloat(),
                        ),
                        color = vertexColors[row][column],
                    )
                }
            }

            MeshDriftVertices.forEach { vertex ->
                val angle = phase + vertex.phaseOffset
                setVertex(
                    row = vertex.row,
                    column = vertex.column,
                    position = Offset(
                        (vertex.anchorX + cos(angle) * vertex.amplitudeX).coerceIn(0f, 1f),
                        (vertex.anchorY + sin(angle) * vertex.amplitudeY).coerceIn(0f, 1f),
                    ),
                    color = vertexColors[vertex.row][vertex.column],
                )
            }
        }
    }

    Box(modifier = modifier.paint(painter, sizeToIntrinsics = false))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(widthDp = 420, heightDp = 800, showBackground = true)
@Composable
private fun MeshGradientBackgroundLightPreview() {
    MaterialExpressiveTheme(colorScheme = VerstaLightColorScheme) {
        MeshGradientBackground(modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(widthDp = 420, heightDp = 800, showBackground = true)
@Composable
private fun MeshGradientBackgroundDarkPreview() {
    MaterialExpressiveTheme(colorScheme = VerstaDarkColorScheme) {
        MeshGradientBackground(modifier = Modifier.fillMaxSize())
    }
}
