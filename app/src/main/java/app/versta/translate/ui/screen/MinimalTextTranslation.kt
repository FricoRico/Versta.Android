package app.versta.translate.ui.screen

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationMockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LoadingProgress
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.MinimalLanguageSelector
import app.versta.translate.ui.component.TextToSpeechButton
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun MinimalTextTranslation(
    languageViewModel: LanguageViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel,
    autoTranslate: Boolean = true,
) {
    val view = LocalView.current
    val context = LocalContext.current

    val input by textTranslationViewModel.input.collectAsStateWithLifecycle("")

    val intermediate by textTranslationViewModel.intermediate.collectAsStateWithLifecycle("")

    val translated by textTranslationViewModel.translated.collectAsStateWithLifecycle("")
    val translatedTransliteration by textTranslationViewModel.translatedTransliteration.collectAsStateWithLifecycle(
        ""
    )

    val translationInProgress by translationViewModel.translationInProgress.collectAsStateWithLifecycle(
        false
    )

    val textToSpeechSynthesisState by textToSpeechViewModel.speechProgressState.collectAsStateWithLifecycle(
        TextToSpeechSynthesisState.Idle
    )

    val textToSpeechVoiceAvailable by textToSpeechViewModel.voiceAvailable.collectAsStateWithLifecycle(
        false
    )

    val targetLanguage by languageViewModel.targetLanguage.collectAsStateWithLifecycle(null)

    val textToSpeechScope = rememberCoroutineScope()

    fun onTextToSpeech() {
        textToSpeechScope.launch {
            textToSpeechViewModel.synthesize(
                text = translated,
                language = targetLanguage!!
            )
        }
    }

    fun onCancelTextToSpeech() {
        textToSpeechViewModel.cancelSynthesis()
    }

    fun translate(input: String) {
        if (input.isEmpty()) {
            return
        }

        textTranslationViewModel.translate(input)
    }

    LaunchedEffect(input, targetLanguage) {
        if (!autoTranslate) {
            return@LaunchedEffect
        }

        translate(input)
    }

    Column(
        modifier = Modifier
            .padding(
                vertical = MaterialTheme.spacing.large,
                horizontal = MaterialTheme.spacing.medium
            )
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
    ) {
        MinimalLanguageSelector(
            languageViewModel = languageViewModel,
        )

        MinimalTextTranslationOutput(
            intermediate = intermediate,
            translation = translated,
            transliteration = translatedTransliteration,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.spacing.medium),
        )

        MinimalTextTranslationOutputButtonRow(
            translationInProgress = translationInProgress,
            textToSpeechSynthesisState = textToSpeechSynthesisState,
            textToSpeechVoiceAvailable = textToSpeechVoiceAvailable,
            onTextToSpeech = {
                onTextToSpeech()
            },
            onCancelTextToSpeech = {
                onCancelTextToSpeech()
            },
            onCancel = {
                translationViewModel.cancelTranslation()
            },
            onCopy = {
                textTranslationViewModel.copyTranslatedText(context)
            },
            onShare = {
                textTranslationViewModel.shareTranslatedText(context)
            },
        )
    }
}

@Composable
fun MinimalTextTranslationOutput(
    intermediate: String,
    translation: String,
    transliteration: String,
    modifier: Modifier = Modifier
) {
    var displayedText by remember { mutableStateOf(intermediate) }

    val translatedStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurface)
    val intermediateStyle = SpanStyle(color = MaterialTheme.colorScheme.surfaceContainerHighest)

    LaunchedEffect(translation, intermediate) {
        val placeholder = intermediate
            .reversed()
            .take(max(0, intermediate.length - translation.length))
            .reversed()

        displayedText = translation + placeholder
    }

    val annotatedString = buildAnnotatedString {
        displayedText.forEachIndexed { index, char ->
            if (index <= translation.length - 1) {
                withStyle(translatedStyle) {
                    append(char)
                }

                return@forEachIndexed
            }

            withStyle(intermediateStyle) {
                append(char)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
    ) {
        item {
            Text(
                text = annotatedString
            )
        }

        if (transliteration.isNotEmpty()) {
            item {
                Text(
                    text = transliteration,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun MinimalTextTranslationOutputButtonRow(
    translationInProgress: Boolean,
    textToSpeechSynthesisState: TextToSpeechSynthesisState,
    textToSpeechVoiceAvailable: Boolean,
    onTextToSpeech: () -> Unit,
    onCancelTextToSpeech: () -> Unit,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            TextToSpeechButton(
                enabled = !translationInProgress,
                textToSpeechSynthesisState = textToSpeechSynthesisState,
                textToSpeechVoiceAvailable = textToSpeechVoiceAvailable,
                onTextToSpeech = onTextToSpeech,
                onCancelTextToSpeech = onCancelTextToSpeech,
            )

            AnimatedVisibility(
                visible = translationInProgress,
                enter = fadeIn(),
                exit = fadeOut(
                    animationSpec = tween(
                        durationMillis = DefaultDurationMillis * 2,
                        delayMillis = DefaultDurationMillis
                    )
                ),
            ) {
                FilledIconButton(
                    onClick = onCancel,
                    colors = FilledIconButtonDefaults.primaryIconButtonColors(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cancel)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            FilledIconButton(
                onClick = onCopy,
                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.copy)
                )
            }

            FilledIconButton(
                onClick = onShare,
                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.share)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun MinimalTextTranslationPreview() {
    val languageViewModel = LanguageViewModel(
        context = LocalContext.current,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )

    MinimalTextTranslation(
        languageViewModel = languageViewModel,
        textTranslationViewModel = TextTranslationViewModel(
            languageViewModel = languageViewModel,
            translationViewModel = TranslationViewModel(
                intermediateTokenizer = TranslationMockTokenizer(),
                intermediateModel = TranslationMockInference(),
                outputTokenizer = TranslationMockTokenizer(),
                outputModel = TranslationMockInference(),
                translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
                languageViewModel = languageViewModel
            )
        ),
        translationViewModel = TranslationViewModel(
            intermediateTokenizer = TranslationMockTokenizer(),
            intermediateModel = TranslationMockInference(),
            outputTokenizer = TranslationMockTokenizer(),
            outputModel = TranslationMockInference(),
            translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
            languageViewModel = LanguageViewModel(
                context = LocalContext.current,
                languageRepository = LanguageMemoryRepository(),
                languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
                externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
            )
        ),
        textToSpeechViewModel = TextToSpeechViewModel(
            tokenizer = TextToSpeechMockTokenizer(),
            model = TextToSpeechMockInference(),
            audioPlayer = AudioMockPlayer(),
            voiceRepository = VoiceMemoryRepository(),
            textToSpeechPreferenceRepository = TextToSpeechPreferenceMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        )
    )
}