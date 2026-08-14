package app.versta.translate.ui.component

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.ui.theme.VerstaDarkColorScheme
import app.versta.translate.ui.theme.VerstaLightColorScheme
import app.versta.translate.utils.SPECTRUM_BAND_COUNT
import app.versta.translate.utils.lightness
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

// Brand accent hues for the voice surface. Shared by the waveform and the
// gradient mic button so the whole scene reads as one light source.
internal val VoiceAccentIndigo = Color(0xFF533AFD)
internal val VoiceAccentBridge = Color(0xFF8D47F7)
internal val VoiceAccentCyan = Color(0xFF4FD8EB)
internal val VoiceAccentMagenta = Color(0xFFF96BEE)

// Crest hue: emerges where the ridge is tall (high amplitude), so the center
// of the band greens out at loud speech like the reference loop.
private val VoiceCrestGreen = Color(0xFF6CF2B4)

private const val BAND_ATTACK_MS = 15f
private const val BAND_RELEASE_MS = 90f
private const val SILENCE_EPSILON = 0.004f
private const val CATMULL_ROM_STEPS = 6

// The underside layer trails the main spectrum: it inhales late and exhales
// long, so the two faces diverge on every transient and merge back on
// sustained tones — a second sheet settling behind the first.
private const val UNDER_ATTACK_MS = 120f
private const val UNDER_RELEASE_MS = 180f
private const val UNDER_DAMP = 0.9f

// How far the horizontal gradient's middle stop drifts right as loudness
// rises — the "colors shift right slightly" cue from the reference.
private const val GRADIENT_DRIFT = 0.24f

// Slow sinusoidal wobble of the middle gradient stop that keeps the hue field
// breathing while listening, on top of the loudness-driven drift.
private const val GRADIENT_WOBBLE = 0.035f
private const val GRADIENT_WOBBLE_MS = 6000f

/**
 * One stratum of the stacked wave field (the layering trick from skydoves'
 * Wave Field example): each layer reads the spectrum through its own
 * horizontal slide and vertical squash, so strata parallax against each other
 * like swells behind ripples. [slideSpeed] rad/s and [slideBands] the slide
 * amplitude in band-widths; direction from the sign of [slideSpeed];
 * [mirrored] swaps low/high under the same brush so this layer's energy sits
 * at the opposite end of the axis.
 */
private data class VoiceStratum(
    val slideSpeed: Float,
    val slideBands: Float,
    val phaseOffset: Float,
    val xShiftBands: Float,
    val swingScale: Float,
    val alphaDark: Float,
    val alphaLight: Float,
    val mirrorY: Float,
    val yOffsetFraction: Float,
)

// Drawn back to front: the slow follower feeds the back layer (its spectrum
// already mirrored at the follower), mid and face use the fast follower.
// xShiftBands spreads the strata horizontally; slideBands is the slow
// oscillation around that base offset — the wave-field parallax.
private val VoiceStrata = listOf(
    // Back: slow, mirrored spectrum, sits right of the face.
    VoiceStratum(slideSpeed = 1.8f, slideBands = 0.3f, phaseOffset = 2.1f, xShiftBands = 0.3f, swingScale = 0.45f, alphaDark = 0.45f, alphaLight = 0.35f, mirrorY = 0.85f, yOffsetFraction = 0.0f),
    // Mid: counter-slides and sits left, partially covered by the face.
    VoiceStratum(slideSpeed = -1.3f, slideBands = 0.2f, phaseOffset = 4.2f, xShiftBands = -0.45f, swingScale = 0.75f, alphaDark = 0.7f, alphaLight = 0.5f, mirrorY = 0.75f, yOffsetFraction = 0.0f),
)

/**
 * Super-gaussian edge window: flat in the middle, pinched to ~0 at both band
 * edges so the ridge never touches the sides of the screen.
 */
private fun envelope(x: Float): Float {
    return exp(-x.pow(6) * 1.25f)
}

private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5f * (2f * p1 + (-p0 + p2) * t +
            (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
            (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
}

/**
 * Appends a closed ridge surface to [path]: the top edge is the Catmull-Rom
 * spline through [knotX]/[knotY] scaled by [topScale] above [centerY], the
 * bottom edge mirrors it squashed by [mirror]. Assumes the first and last
 * knots sit on the centerline so both ends flow into the hairline.
 */
private fun appendRidge(
    path: Path,
    knotX: FloatArray,
    knotY: FloatArray,
    centerY: Float,
    topScale: Float,
    mirror: Float,
) {
    val count = knotX.size
    path.rewind()
    for (i in 0 until count - 1) {
        val i0 = maxOf(i - 1, 0)
        val i3 = minOf(i + 2, count - 1)
        for (step in 0 until CATMULL_ROM_STEPS) {
            val t = step / CATMULL_ROM_STEPS.toFloat()
            val x = catmullRom(knotX[i0], knotX[i], knotX[i + 1], knotX[i3], t)
            val y = centerY - catmullRom(knotY[i0], knotY[i], knotY[i + 1], knotY[i3], t) * topScale
            if (i == 0 && step == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
    }
    path.lineTo(knotX[count - 1], centerY - knotY[count - 1] * topScale)
    for (i in count - 2 downTo 0) {
        val i0 = maxOf(i - 1, 0)
        val i3 = minOf(i + 2, count - 1)
        for (step in CATMULL_ROM_STEPS - 1 downTo 0) {
            val t = step / CATMULL_ROM_STEPS.toFloat()
            val x = catmullRom(knotX[i0], knotX[i], knotX[i + 1], knotX[i3], t)
            val y = centerY + catmullRom(knotY[i0], knotY[i], knotY[i + 1], knotY[i3], t) * mirror
            path.lineTo(x, y)
        }
    }
    path.lineTo(knotX[0], centerY + knotY[0] * mirror)
    path.close()
}

/**
 * Spectrum voice waveform: the mic spectrum (log-spaced vocal bands, [0, 1])
 * rendered as a single solid ridge — X is frequency, Y is amplitude, the
 * surface curling up and down with the audio like a 3D plane seen from the
 * side. Hue responds on both axes: the horizontal gradient runs
 * indigo-cyan-magenta across frequency while a vertical crest pass greens out
 * the tall regions, and the mid gradient stop drifts right as loudness rises.
 * Silence collapses the ridge to the hairline gradient line. Honors the system
 * animator scale — with animations disabled the waveform renders statically.
 *
 * All animation state is only read inside the draw pass, so animating does not
 * retrigger composition.
 */
@Composable
fun VoiceWaveform(
    spectrum: FloatArray,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }

    val latestSpectrum by rememberUpdatedState(spectrum)
    val latestActive by rememberUpdatedState(active)

    // Followed band levels for the face and (slower) underside, mutated per
    // frame; plain storage because Compose only needs to know WHEN to redraw,
    // which the followed counter carries.
    val bands = remember { FloatArray(SPECTRUM_BAND_COUNT) }
    val underBands = remember { FloatArray(SPECTRUM_BAND_COUNT) }
    var followed by remember { mutableLongStateOf(0L) }
    var phaseMs by remember { mutableFloatStateOf(0f) }

    if (animationsEnabled) {
        LaunchedEffect(Unit) {
            var lastNanos = 0L
            while (true) {
                withFrameNanos { now ->
                    if (lastNanos == 0L) {
                        lastNanos = now
                        return@withFrameNanos
                    }
                    val dtMs = (now - lastNanos) / 1_000_000f
                    lastNanos = now

                    var dirty = false
                    for (i in 0 until SPECTRUM_BAND_COUNT) {
                        val target = if (latestActive && i < latestSpectrum.size) latestSpectrum[i] else 0f
                        val rate = 1f - exp(
                            -dtMs / if (target > bands[i]) BAND_ATTACK_MS else BAND_RELEASE_MS
                        )
                        val next = bands[i] + (target - bands[i]) * rate
                        // Snap when converged so a steady spectrum (or silence)
                        // stops scheduling redraws instead of floating forever.
                        val settled = next < SILENCE_EPSILON && target == 0f
                        val snapped = if (settled) 0f else if (abs(target - next) < SILENCE_EPSILON) target else next
                        if (snapped != bands[i]) {
                            dirty = true
                        }
                        bands[i] = snapped

                        // Underside: the spectrum MIRRORED along X (this band
                        // follows the opposite end's energy), neighbor-softened
                        // and damped, through a slower envelope — related
                        // terrain, not a copy.
                        val spec = latestSpectrum
                        val m = SPECTRUM_BAND_COUNT - 1 - i
                        fun specAt(index: Int): Float =
                            if (!latestActive || index < 0 || index >= spec.size) 0f else spec[index]
                        val underTarget = (specAt(m) * 0.6f + (specAt(m - 1) + specAt(m + 1)) * 0.2f) * UNDER_DAMP
                        val underRate = 1f - exp(
                            -dtMs / if (underTarget > underBands[i]) UNDER_ATTACK_MS else UNDER_RELEASE_MS
                        )
                        val underNext = underBands[i] + (underTarget - underBands[i]) * underRate
                        val underSettled = underNext < SILENCE_EPSILON && underTarget == 0f
                        val underSnapped =
                            if (underSettled) 0f else if (abs(underTarget - underNext) < SILENCE_EPSILON) underTarget else underNext
                        if (underSnapped != underBands[i]) {
                            dirty = true
                        }
                        underBands[i] = underSnapped
                    }

                    // Slow phase for the gradient wobble; only ticks while a
                    // session runs so an idle hairline never repaints.
                    if (latestActive && dirty) {
                        phaseMs += dtMs
                    }

                    if (dirty) {
                        followed++
                    }
                }
            }
        }
    }

    val dark = MaterialTheme.colorScheme.background.lightness() < 0.5f
    val ridge = remember { Path() }
    val underside = remember { Path() }
    val midPath = remember { Path() }
    val hairline = remember { Path() }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) {
            return@Canvas
        }

        // Reading followed registers the redraw trigger; the values live in bands.
        @Suppress("UNUSED_EXPRESSION")
        followed

        val loudness = bands.average().toFloat()

        val centerY = height / 2f
        val swing = height * 0.5f

        // Knots at band centers plus terminal pins on the centerline at both
        // edges, so every stratum flows back into the hairline corners instead
        // of cutting off (a hard edge visible with deep voices). Each stratum
        // slides its knots horizontally with a slow per-layer sine — the
        // parallax cue from the wave field.
        val count = SPECTRUM_BAND_COUNT
        val knotCount = count + 2
        val bandWidth = width / count
        val phaseSec = phaseMs / 1000f

        val knotX = FloatArray(knotCount)
        val knotY = FloatArray(knotCount)
        knotX[0] = 0f
        knotX[knotCount - 1] = width
        for (i in 0 until count) {
            val x = (i + 0.5f) / count * width
            knotX[i + 1] = x
            knotY[i + 1] = bands[i] * envelope(x / width * 2f - 1f) * swing
        }
        appendRidge(ridge, knotX, knotY, centerY, topScale = 1f, mirror = 0.7f)

        // Strata: back reads the mirrored slow follower, mid the fast bands;
        // each slides its knots horizontally at its own rate — the wave-field
        // parallax over the spectrum terrain.
        val strataPaths = arrayOf(underside, midPath)
        VoiceStrata.forEachIndexed { index, stratum ->
            val levels = if (index == 0) underBands else bands
            val slide = sin(phaseSec * stratum.slideSpeed + stratum.phaseOffset) *
                    bandWidth * stratum.slideBands + bandWidth * stratum.xShiftBands
            val stratumX = FloatArray(knotCount)
            val stratumY = FloatArray(knotCount)
            stratumX[0] = 0f
            stratumX[knotCount - 1] = width
            for (i in 0 until count) {
                val base = (i + 0.5f) / count * width
                stratumX[i + 1] = (base + slide).coerceIn(1f, width - 1f)
                stratumY[i + 1] = levels[i] * envelope(base / width * 2f - 1f) * swing * stratum.swingScale
            }
            appendRidge(
                strataPaths[index], stratumX, stratumY,
                centerY + height * stratum.yOffsetFraction,
                topScale = 1f, mirror = stratum.mirrorY,
            )
        }

        // At silence the ridge collapses to zero area and would draw nothing —
        // keep a stroked hairline so the gradient line stays visible.
        hairline.rewind()
        hairline.moveTo(0f, centerY)
        hairline.lineTo(width, centerY)

        // Brushes are pinned to the band-center range, not the screen edges:
        // the terrain only lives between the first and last band centers
        // (~6%..94%), so pinning to the screen edge made speech (which parks
        // in the low bands) read as a nearly solid blue slab. Pinned to the
        // knot range, the full hue spectrum maps onto the raised body.
        val hueStart = knotX[1]
        val hueEnd = knotX[knotCount - 2]
        val wobble = GRADIENT_WOBBLE * sin(2f * Math.PI.toFloat() * phaseMs / GRADIENT_WOBBLE_MS)
        val midStop = (0.5f + GRADIENT_DRIFT * loudness.coerceIn(0f, 1f) + wobble)
            .coerceIn(0.05f, 0.95f)
        val surfaceBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to VoiceAccentIndigo,
                midStop to VoiceAccentCyan,
                1.0f to VoiceAccentMagenta,
            ),
            startX = hueStart,
            endX = hueEnd,
        )
        val undersideBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to VoiceAccentIndigo,
                midStop to VoiceAccentBridge,
                1.0f to VoiceAccentMagenta,
            ),
            startX = hueStart,
            endX = hueEnd,
        )
        val midBrush = Brush.horizontalGradient(
            colorStops = arrayOf(
                0.0f to VoiceAccentIndigo,
                midStop to VoiceAccentBridge,
                1.0f to VoiceAccentMagenta,
            ),
            startX = hueStart,
            endX = hueEnd,
        )
        val strataBrushes = listOf(undersideBrush, midBrush)
        // Hue by amplitude: magenta crest fading through green into
        // transparency at the base — every raised column then reads as a real
        // gradient (indigo base, cyan mid, green/magenta crest) instead of a
        // flat blue slab, regardless of where on the axis the energy sits.
        val crestBrush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to VoiceAccentMagenta,
                0.4f to VoiceCrestGreen,
                0.75f to Color.Transparent,
            ),
            startY = centerY - swing,
            endY = centerY + swing * 0.7f,
        )

        drawPath(
            path = hairline,
            brush = surfaceBrush,
            style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            alpha = if (dark) 0.9f else 0.75f,
        )
        if (dark && loudness > SILENCE_EPSILON) {
            drawPath(
                path = ridge,
                brush = surfaceBrush,
                alpha = 0.22f * loudness.coerceIn(0f, 1f),
                blendMode = BlendMode.Plus,
            )
        }
        VoiceStrata.forEachIndexed { index, stratum ->
            drawPath(
                path = strataPaths[index],
                brush = strataBrushes[index],
                alpha = if (dark) stratum.alphaDark else stratum.alphaLight,
            )
        }
        drawPath(
            path = ridge,
            brush = surfaceBrush,
            alpha = if (dark) 1f else 0.9f,
        )
        drawPath(
            path = ridge,
            brush = crestBrush,
            alpha = (if (dark) 1f else 0.5f) * loudness.coerceIn(0f, 1f),
            blendMode = if (dark) BlendMode.Plus else BlendMode.SrcOver,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(widthDp = 420, showBackground = true, backgroundColor = 0xFF0F1419)
@Composable
private fun VoiceWaveformDarkPreview() {
    // Synthetic speech-like spectrum: fundamental low, formant humps mid.
    val spectrum = FloatArray(SPECTRUM_BAND_COUNT) { i ->
        when {
            i in 1..3 -> 0.85f - (i - 2) * (i - 2) * 0.15f
            i in 4..6 -> 0.55f - (i - 5) * (i - 5) * 0.12f
            else -> 0.1f
        }
    }
    MaterialExpressiveTheme(colorScheme = VerstaDarkColorScheme) {
        Column {
            VoiceWaveform(
                spectrum = spectrum,
                active = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(widthDp = 420, showBackground = true, backgroundColor = 0xFFF8F9FF)
@Composable
private fun VoiceWaveformLightPreview() {
    val spectrum = FloatArray(SPECTRUM_BAND_COUNT) { i ->
        when {
            i in 1..3 -> 0.85f - (i - 2) * (i - 2) * 0.15f
            i in 4..6 -> 0.55f - (i - 5) * (i - 5) * 0.12f
            else -> 0.1f
        }
    }
    MaterialExpressiveTheme(colorScheme = VerstaLightColorScheme) {
        VoiceWaveform(
            spectrum = spectrum,
            active = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        )
    }
}
