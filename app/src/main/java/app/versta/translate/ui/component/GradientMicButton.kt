package app.versta.translate.ui.component

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.ui.theme.VerstaDarkColorScheme
import app.versta.translate.ui.theme.VerstaLightColorScheme
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.lightness

private val OrbRingSize = 88.dp
private val RingStrokeWidth = 2.5.dp
private val RingGlowRadiusScale = 0.78f
private const val RingRotationDurationMillis = 4200

/**
 * Primary voice-input action: a filled mic orb ringed by a slowly rotating
 * brand-gradient edge (indigo, purple bridge, magenta) with a soft radial glow
 * underneath while [listening]. Swaps mic to stop glyph when active and to a
 * progress indicator while [processing].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GradientMicButton(
    listening: Boolean,
    processing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
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

    val ringRotation = if (animationsEnabled && listening) {
        val transition = rememberInfiniteTransition(label = "micRingRotation")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(RingRotationDurationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "micRingAngle",
        ).value
    } else {
        0f
    }

    val glowAlpha by animateFloatAsState(
        targetValue = if (listening) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "micGlow",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "micPress",
    )

    val dark = MaterialTheme.colorScheme.background.lightness() < 0.5f
    val ringBrush = Brush.sweepGradient(
        colors = listOf(
            VoiceAccentIndigo,
            VoiceAccentBridge,
            VoiceAccentMagenta,
            VoiceAccentIndigo,
        )
    )
    val glowBrush = Brush.radialGradient(
        colors = listOf(
            VoiceAccentBridge.copy(alpha = 0.6f),
            VoiceAccentMagenta.copy(alpha = 0.3f),
            Color.Transparent,
        )
    )

    val containerColor by animateColorAsState(
        targetValue = if (listening) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "micContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (listening) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        label = "micContent",
    )

    Box(
        modifier = modifier
            .size(OrbRingSize)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val ringRadius = (OrbRingSize - RingStrokeWidth).toPx() / 2f
            if (dark && glowAlpha > 0f) {
                drawCircle(
                    brush = glowBrush,
                    radius = size.maxDimension * RingGlowRadiusScale,
                    alpha = glowAlpha,
                )
            }
            rotate(ringRotation) {
                drawCircle(
                    brush = ringBrush,
                    radius = ringRadius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = RingStrokeWidth.toPx(),
                    ),
                    alpha = if (enabled) 1f else 0.5f,
                )
            }
        }

        FilledIconButton(
            onClick = onClick,
            enabled = enabled && !processing,
            interactionSource = interactionSource,
            modifier = Modifier
                .size(MaterialTheme.spacing.extraExtraLarge)
                .clip(MaterialTheme.shapes.extraExtraLarge),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedVisibility(
                    visible = processing,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(MaterialTheme.spacing.extraLarge)
                    )
                }
                AnimatedVisibility(
                    visible = !processing && listening,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_stop_24),
                        contentDescription = stringResource(R.string.conversation_stop_listening),
                    )
                }
                AnimatedVisibility(
                    visible = !processing && !listening,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_mic_24),
                        contentDescription = stringResource(R.string.conversation_start_listening),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, backgroundColor = 0xFF0F1419)
@Composable
private fun GradientMicButtonDarkPreview() {
    MaterialExpressiveTheme(colorScheme = VerstaDarkColorScheme) {
        GradientMicButton(
            listening = true,
            processing = false,
            enabled = true,
            onClick = {},
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, backgroundColor = 0xFFF8F9FF)
@Composable
private fun GradientMicButtonLightPreview() {
    MaterialExpressiveTheme(colorScheme = VerstaLightColorScheme) {
        GradientMicButton(
            listening = false,
            processing = false,
            enabled = true,
            onClick = {},
        )
    }
}
