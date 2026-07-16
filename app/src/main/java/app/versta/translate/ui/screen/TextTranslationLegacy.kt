package app.versta.translate.ui.screen

import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.DataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalDataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.entity.WritingDirection
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ReadyState
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldModalBottomSheet
import app.versta.translate.ui.component.TextField
import app.versta.translate.ui.component.TextFieldDefaults
import app.versta.translate.ui.component.TextToSpeechButton
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTranslationLegacy(
    navigationViewModel: NavigationViewModel,
    languageViewModel: LanguageViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel,
) {
    LocalView.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val sourceLanguage by languageViewModel.sourceLanguage.collectAsStateWithLifecycle(null)

    val input by textTranslationViewModel.input.collectAsStateWithLifecycle("")
    val inputTransliteration by textTranslationViewModel.inputTransliteration.collectAsStateWithLifecycle(
        ""
    )

    val intermediate by textTranslationViewModel.intermediate.collectAsStateWithLifecycle("")

    val translated by textTranslationViewModel.translated.collectAsStateWithLifecycle("")
    val translatedTransliteration by textTranslationViewModel.translatedTransliteration.collectAsStateWithLifecycle(
        ""
    )

    val languageReadyState by translationViewModel.languageReadyState.collectAsStateWithLifecycle()
    val readyToTranslate =
        languageReadyState == ReadyState.Ready || sourceLanguage is AutoDetectLanguage

    val textToSpeechSynthesisState by textToSpeechViewModel.speechProgressState.collectAsStateWithLifecycle(
        TextToSpeechSynthesisState.Idle
    )

    val textToSpeechAvailable by textToSpeechViewModel.textToSpeechReady.collectAsStateWithLifecycle(false)
    val textToSpeechVoiceAvailable by textToSpeechViewModel.voiceAvailable.collectAsStateWithLifecycle(
        false
    )

    val translationInProgress by translationViewModel.translationInProgress.collectAsStateWithLifecycle(
        false
    )
    val translateOnInput by textTranslationViewModel.translateOnInput.collectAsStateWithLifecycle(
        false
    )

    val languageOptions by languageViewModel.languageOptions.collectAsStateWithLifecycle(null)

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

    val textToSpeechScope = rememberCoroutineScope()

    fun translate(input: String) {
        if (input.isEmpty()) {
            return
        }

        sheetScope.launch {
            if (!languageViewModel.modelAvailable()) {
                languageViewModel.setLanguageSuggestionState(true) {
                    translate(input)
                }
                return@launch
            }

            textTranslationViewModel.translate(input)

            scaffoldState.bottomSheetState.expand()

            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> {
                    focusManager.moveFocus(FocusDirection.Down)
                    keyboardController?.hide()
                }

                else -> {
                    focusManager.clearFocus()
                }
            }
        }
    }

    fun cancelTranslation() {
        translationViewModel.cancelTranslation()
    }

    fun setInput(text: String) {
        textTranslationViewModel.setInput(text)
    }

    fun clearInput() {
        textTranslationViewModel.clearInput()
    }

    fun cancelTextToSpeech() {
        textToSpeechViewModel.cancelSynthesis()
    }

    fun clearTranslation() {
        sheetScope.launch {
            scaffoldState.bottomSheetState.hide()
        }

        textTranslationViewModel.clearTranslation()
    }

    fun onTextToSpeech() {
        textToSpeechScope.launch {
            textToSpeechViewModel.synthesize(
                text = translated,
                language = targetLanguage!!
            )
        }
    }

    fun onCancelTextToSpeech() {
        cancelTextToSpeech()
    }

    fun onCancel() {
        cancelTranslation()
    }

    fun onClear() {
        clearInput()
        clearTranslation()
        cancelTextToSpeech()
    }

    fun onSubmit(input: String) {
        translate(input)
    }

    fun onSwapLanguages() {
        textTranslationViewModel.setInput(translated)

        clearTranslation()
        cancelTextToSpeech()
    }

    fun onCopy() {
        textTranslationViewModel.copyTranslatedText(context)
    }

    fun onShare() {
        textTranslationViewModel.shareTranslatedText(context)
    }

    LaunchedEffect(languageOptions) {
        clearTranslation()
    }

    LaunchedEffect(translateOnInput) {
        if (!translateOnInput) {
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
                        navigationViewModel.clearAndNavigate(Screens.TextTranslation)
                    }) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_arrow_back_24),
                            stringResource(R.string.back)
                        )
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
                            setInput(it)
                        },
                        onSubmit = {
                            onSubmit(input)
                        }
                    )

                    TextTranslationInputButtonRow(
                        readyToTranslate = readyToTranslate,
                        inputIsEmpty = input.isEmpty(),
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
        sheetPeekHeight = BottomSheetDefaults.SheetPeekHeight + WindowInsets.navigationBars.asPaddingValues()
            .calculateBottomPadding(),
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
                            .padding(
                                top = MaterialTheme.spacing.medium + WindowInsets.navigationBars.asPaddingValues()
                                    .calculateBottomPadding()
                            ),
                        intermediate = intermediate,
                        translation = translated,
                        transliteration = translatedTransliteration,
                        writingDirection = targetLanguage?.getWritingDirection()
                            ?: WritingDirection.LTR
                    )
                }
            }
        },
        sheetBottomBar = {
            TextTranslationOutputButtonRow(
                translationInProgress = translationInProgress,
                textToSpeechSynthesisState = textToSpeechSynthesisState,
                textToSpeechAvailable = textToSpeechAvailable,
                textToSpeechVoiceAvailable = textToSpeechVoiceAvailable,
                modifier = Modifier
                    .onGloballyPositioned {
                        bottomBarHeight = it.size.height
                    }
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .padding(bottom = MaterialTheme.spacing.medium),
                onTextToSpeech = {
                    onTextToSpeech()
                },
                onCancelTextToSpeech = {
                    onCancelTextToSpeech()
                },
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
    readyToTranslate: Boolean,
    inputIsEmpty: Boolean,
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
                enabled = !inputIsEmpty,
                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_close_small_24),
                    contentDescription = stringResource(R.string.clear)
                )
            }
        }

        FilledIconButton(
            onClick = onTranslate,
            enabled = !inputIsEmpty && readyToTranslate,
            colors = FilledIconButtonDefaults.primaryIconButtonColors(),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.rounded_translate_24),
                contentDescription = stringResource(R.string.translate)
            )
        }
    }
}

@Composable
fun TextTranslationOutput(
    modifier: Modifier = Modifier,
    intermediate: String,
    translation: String,
    transliteration: String,
    writingDirection: WritingDirection
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MaterialTheme.spacing.large)
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(
            MaterialTheme.spacing.large,
            Alignment.Bottom
        )
    ) {
        Text(
            text = annotatedString,
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
    textToSpeechSynthesisState: TextToSpeechSynthesisState,
    textToSpeechAvailable: Boolean,
    textToSpeechVoiceAvailable: Boolean,
    onTextToSpeech: () -> Unit,
    onCancelTextToSpeech: () -> Unit,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        ) {
            TextToSpeechButton(
                enabled = !translationInProgress,
                textToSpeechSynthesisState = textToSpeechSynthesisState,
                textToSpeechAvailable = textToSpeechAvailable,
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
                        imageVector = ImageVector.vectorResource(R.drawable.rounded_stop_24),
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
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_content_copy_24),
                    contentDescription = stringResource(R.string.copy)
                )
            }

            FilledIconButton(
                onClick = onShare,
                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.rounded_ios_share_24    ),
                    contentDescription = stringResource(R.string.share)
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun TextTranslationLegacyPreview() {
    val languageViewModel = LanguageViewModel(
        context = LocalContext.current,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )

    val translationMockInference = TranslationMockInference()

    val translationViewModel = TranslationViewModel(
        intermediateModel = translationMockInference,
        outputModel = translationMockInference,
        translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
        languageViewModel = languageViewModel
    )

    TextTranslationLegacy(
        navigationViewModel = NavigationViewModel(Screens.TextTranslation),
        textTranslationViewModel = TextTranslationViewModel(
            translationViewModel = translationViewModel,
            languageViewModel = languageViewModel
        ),
        languageViewModel = languageViewModel,
        translationViewModel = translationViewModel,
        textToSpeechViewModel = TextToSpeechViewModel(
            context = LocalContext.current,
            espeakNG = ESpeakNG(),
            openJTalk = OpenJTalk(),
            tokenizer = TextToSpeechMockTokenizer(),
            model = TextToSpeechMockInference(),
            audioPlayer = AudioMockPlayer(),
            dataRepository = DataMemoryRepository(),
            voiceRepository = VoiceMemoryRepository(),
            textToSpeechPreferenceRepository = TextToSpeechPreferenceMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalDataRepository = ExternalDataMemoryRepository(),
        )
    )
}