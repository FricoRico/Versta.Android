package app.versta.translate.ui.screen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.MockInference
import app.versta.translate.adapter.outbound.MockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LoadingProgress
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.MinimalLanguageSelector
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
fun MinimalTextTranslation(
    languageViewModel: LanguageViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    autoTranslate: Boolean = true,
) {
    val context = LocalContext.current

    val input by textTranslationViewModel.input.collectAsStateWithLifecycle("")

    val translated by textTranslationViewModel.translated.collectAsStateWithLifecycle("")
    val translatedTransliteration by textTranslationViewModel.translatedTransliteration.collectAsStateWithLifecycle(
        ""
    )

    val translationInProgress by translationViewModel.translationInProgress.collectAsStateWithLifecycle(
        false
    )

    val translationLoadingProgress by translationViewModel.loadingProgress.collectAsStateWithLifecycle(
        LoadingProgress.Idle
    )
    val transliterationLoadingProgress by textTranslationViewModel.loadingProgress.collectAsStateWithLifecycle(
        LoadingProgress.Idle
    )

    val languages by translationViewModel.languages.collectAsStateWithLifecycle(null)

    val translationScope = rememberCoroutineScope()

    fun translate(input: String) {
        if (input.isEmpty()) {
            return
        }

        translationScope.launch {
            if (languages == null) return@launch

            translationViewModel.translateAsFlow(input, languages!!)
                .collect {
                    textTranslationViewModel.setTranslation(it)
                }
        }
    }

    LaunchedEffect(input, translationLoadingProgress, transliterationLoadingProgress) {
        if (!autoTranslate) {
            return@LaunchedEffect
        }

        if (translationLoadingProgress != LoadingProgress.Completed || transliterationLoadingProgress != LoadingProgress.Completed) {
            return@LaunchedEffect
        }

        textTranslationViewModel.setTranslateOnInput(false)
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
            translation = translated,
            transliteration = translatedTransliteration,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = MaterialTheme.spacing.medium),
        )

        MinimalTextTranslationOutputButtonRow(
            translationInProgress = translationInProgress,
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
    translation: String,
    transliteration: String,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large)
    ) {
        item {
            Text(
                text = translation,
                color = MaterialTheme.colorScheme.onSurface
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
        Column {
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
    MinimalTextTranslation(
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        textTranslationViewModel = TextTranslationViewModel(
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        translationViewModel = TranslationViewModel(
            tokenizer = MockTokenizer(),
            model = MockInference(),
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            translationPreferenceRepository = TranslationPreferenceMemoryRepository()
        )
    )
}