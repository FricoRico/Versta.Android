package app.versta.translate.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.versta.translate.R
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing

@Composable
fun TextToSpeechButton(
    enabled: Boolean = true,
    textToSpeechSynthesisState: TextToSpeechSynthesisState,
    textToSpeechVoiceAvailable: Boolean,
    onTextToSpeech: () -> Unit,
    onCancelTextToSpeech: () -> Unit,
) {
    val shieldSize = 20.dp
    val shieldOffset = 16.dp
    val shieldFontSize = 8.sp
    val shieldLineHeight = (shieldFontSize.value + (shieldSize.value / 2)).sp

    Box(
        contentAlignment = Alignment.Center,
    ) {
        FilledIconButton(
            enabled = enabled,
            colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            onClick = {
                when (textToSpeechSynthesisState) {
                    TextToSpeechSynthesisState.Idle -> onTextToSpeech()
                    TextToSpeechSynthesisState.Preparing -> onCancelTextToSpeech()
                    TextToSpeechSynthesisState.Synthesizing -> onCancelTextToSpeech()
                }
            }
        ) {
            Box {
                AnimatedVisibility(
                    visible = textToSpeechSynthesisState == TextToSpeechSynthesisState.Idle,
                    enter = fadeIn(animationSpec = tween(500)),
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    Icon(Icons.Outlined.GraphicEq, contentDescription = stringResource(R.string.text_to_speak_speak))
                }

                AnimatedVisibility(
                    visible = textToSpeechSynthesisState == TextToSpeechSynthesisState.Preparing,
                    enter = fadeIn(animationSpec = tween(500)),
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = MaterialTheme.spacing.hairline,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                AnimatedVisibility(
                    visible = textToSpeechSynthesisState == TextToSpeechSynthesisState.Synthesizing,
                    enter = fadeIn(animationSpec = tween(500)),
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.text_to_speech_stop))
                }
            }
        }

        if (textToSpeechVoiceAvailable) {
            Box(
                modifier = Modifier
                    .offset(x = shieldOffset, y = -shieldOffset)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .requiredSize(shieldSize)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { /* Do nothing */ }
                            )
                        },
                ) {
                    Text(
                        text = "HD",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        lineHeight = shieldLineHeight,
                        fontSize = shieldFontSize
                    )
                }
            }
        }
    }
}
