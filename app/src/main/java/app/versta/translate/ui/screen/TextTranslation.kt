package app.versta.translate.ui.screen

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.AnimationConstants.DefaultDurationMillis
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.MockInference
import app.versta.translate.adapter.outbound.MockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.entity.WritingDirection
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LoadingProgress
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldModalBottomSheet
import app.versta.translate.ui.component.TextField
import app.versta.translate.ui.component.TextFieldDefaults
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTranslation(
    navController: NavController,
    languageViewModel: LanguageViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val view = LocalView.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val input by textTranslationViewModel.input.collectAsStateWithLifecycle("")
    val inputTransliteration by textTranslationViewModel.inputTransliteration.collectAsStateWithLifecycle(
        ""
    )
    val translated by textTranslationViewModel.translated.collectAsStateWithLifecycle("")
    val translatedTransliteration by textTranslationViewModel.translatedTransliteration.collectAsStateWithLifecycle(
        ""
    )

    val translationLoadingProgress by translationViewModel.loadingProgress.collectAsStateWithLifecycle(
        LoadingProgress.Idle
    )
    val transliterationLoadingProgress by textTranslationViewModel.loadingProgress.collectAsStateWithLifecycle(
        LoadingProgress.Idle
    )

    val translationInProgress by translationViewModel.translationInProgress.collectAsStateWithLifecycle(
        false
    )
    val translateOnInput by textTranslationViewModel.translateOnInput.collectAsStateWithLifecycle(
        false
    )

    val languages by translationViewModel.languages.collectAsStateWithLifecycle(null)

    val targetLanguage by languageViewModel.targetLanguage.collectAsStateWithLifecycle(null)

    val sheetScope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberStandardBottomSheetState(
            skipHiddenState = false,
            initialValue = SheetValue.Hidden,
            confirmValueChange = {
                it != SheetValue.Hidden
            }
        )
    )

    var bottomBarHeight by remember { mutableIntStateOf(0) }
    val translationBottomPadding = with(LocalDensity.current) { bottomBarHeight.toDp() }

    val translationScope = rememberCoroutineScope()

    fun translate(input: String) {
        if (input.isEmpty()) {
            return
        }

        sheetScope.launch {
            focusManager.clearFocus()
            scaffoldState.bottomSheetState.expand()
        }

        translationScope.launch {
            if (languages == null) return@launch

            translationViewModel.translateAsFlow(input, languages!!)
                .collect {
                    textTranslationViewModel.setTranslation(it)
                    view.performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                }
        }
    }

    fun cancelTranslation() {
        translationViewModel.cancelTranslation()
    }

    fun clearInput() {
        textTranslationViewModel.clearInput()
    }

    fun clearTranslation() {
        sheetScope.launch {
            scaffoldState.bottomSheetState.hide()
        }

        textTranslationViewModel.clearTranslation()
    }

    fun onCancel() {
        cancelTranslation()
    }

    fun onClear() {
        clearInput()
        clearTranslation()
    }

    fun onSubmit(input: String) {
        translate(input)
    }

    fun onSwapLanguages() {
        textTranslationViewModel.setInput(translated)
        clearTranslation()
    }

    fun onCopy() {
        textTranslationViewModel.copyTranslatedText(context)
    }

    fun onShare() {
        textTranslationViewModel.shareTranslatedText(context)
    }

    LaunchedEffect(languages) {
        clearTranslation()
    }

    LaunchedEffect(input, translationLoadingProgress, transliterationLoadingProgress) {
        if (!translateOnInput) {
            return@LaunchedEffect
        }

        if (translationLoadingProgress != LoadingProgress.Completed || transliterationLoadingProgress != LoadingProgress.Completed) {
            return@LaunchedEffect
        }

        textTranslationViewModel.setTranslateOnInput(false)
        translate(input)
    }

    ScaffoldModalBottomSheet(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigate(Screens.Home()) {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.translator)
                    )
                },
            )
        },
        content = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .padding(
                        top = MaterialTheme.spacing.small,
                        bottom = MaterialTheme.spacing.medium
                    )
            ) {
                LanguageSelector(
                    languageViewModel = languageViewModel,
                    onLanguageSwap = {
                        onSwapLanguages()
                    },
                )
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    TextTranslationInputField(
                        input = input,
                        transliteration = inputTransliteration,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = MaterialTheme.spacing.small)
                            .padding(start = MaterialTheme.spacing.medium),
                        onValueChange = {
                            textTranslationViewModel.setInput(it)
                        },
                        onSubmit = {
                            onSubmit(input)
                        }
                    )

                    TextTranslationInputButtonRow(
                        onTranslate = {
                            onSubmit(input)
                        },
                        onClear = {
                            onClear()
                        },
                    )
                }
            }
        },
        scaffoldState = scaffoldState,
        sheetPeekHeight = BottomSheetDefaults.SheetPeekHeight + MaterialTheme.spacing.medium,
        sheetContent = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                contentPadding = PaddingValues(
                    start = MaterialTheme.spacing.small,
                    end = MaterialTheme.spacing.small,
                ),
                modifier = Modifier
                    .padding(
                        bottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding() + translationBottomPadding
                    )
            ) {
                item {
                    TextTranslationOutput(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.spacing.large)
                            .padding(top = MaterialTheme.spacing.large),
                        translation = translated,
                        transliteration = translatedTransliteration,
                        writingDirection = targetLanguage?.getWritingDirection() ?: WritingDirection.LTR
                    )
                }
            }
        },
        sheetBottomBar = {
            TextTranslationOutputButtonRow(
                translationInProgress = translationInProgress,
                modifier = Modifier
                    .onGloballyPositioned {
                        bottomBarHeight = it.size.height
                    }
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .padding(bottom = MaterialTheme.spacing.medium),
                onCancel = {
                    onCancel()
                },
                onCopy = {
                    onCopy()
                },
                onShare = {
                    onShare()
                },
            )
        }
    )
}

@Composable
fun TextTranslationInputField(
    modifier: Modifier = Modifier,
    input: String,
    transliteration: String,
    onValueChange: (String) -> Unit = {},
    onSubmit: (String) -> Unit = {}
) {
    val textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.ContentOrLtr)

    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        modifier = modifier
    ) {
        TextField(
            placeholder = stringResource(R.string.text_translation_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 192.dp),
            value = input,
            textStyle = textStyle,
            onValueChange = onValueChange,
            onSubmit = { onSubmit(input) },
            colors = TextFieldDefaults.colorsTransparent()
        )

        if (transliteration.isNotEmpty()) {
            Text(
                text = transliteration,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun TextTranslationInputButtonRow(
    modifier: Modifier = Modifier,
    onTranslate: () -> Unit,
    onClear: () -> Unit,
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
            FilledIconButton(
                onClick = onClear,
                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.clear)
                )
            }
        }

        FilledIconButton(
            onClick = onTranslate,
            colors = FilledIconButtonDefaults.primaryIconButtonColors(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Translate,
                contentDescription = stringResource(R.string.translate)
            )
        }
    }
}

@Composable
fun TextTranslationOutput(
    modifier: Modifier = Modifier,
    translation: String,
    transliteration: String,
    writingDirection: WritingDirection
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.spacing.large)
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large, Alignment.Bottom)
    ) {
        Text(
            text = translation,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = if (writingDirection == WritingDirection.RTL) {
                TextAlign.End
            } else {
                TextAlign.Start
            },
        )

        if (transliteration.isNotEmpty()) {
            Text(
                text = transliteration,
                modifier = Modifier
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun TextTranslationOutputButtonRow(
    modifier: Modifier = Modifier,
    translationInProgress: Boolean,
    onCancel: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
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
@Preview
fun TextTranslationPreview() {
    TextTranslation(
        navController = rememberNavController(),
        textTranslationViewModel = TextTranslationViewModel(
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
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