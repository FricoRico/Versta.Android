package app.versta.translate.ui.screen

import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldModalBottomSheet
import app.versta.translate.ui.component.TextField
import app.versta.translate.ui.component.TextFieldDefaults
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTranslation(
    navController: NavController,
    languageViewModel: LanguageViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var translateOnChange by remember { mutableStateOf(true) }

    val input by textTranslationViewModel.input.collectAsStateWithLifecycle("")
    val inputTransliteration by textTranslationViewModel.inputTransliteration.collectAsStateWithLifecycle(
        ""
    )
    val translated by textTranslationViewModel.translated.collectAsStateWithLifecycle("")
    val translatedTransliteration by textTranslationViewModel.translatedTransliteration.collectAsStateWithLifecycle(
        ""
    )

    val languages by translationViewModel.languages.collectAsStateWithLifecycle(null)

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

    val translationInProgress by translationViewModel.translationInProgress.collectAsStateWithLifecycle(
        false
    )
    val translationScope = remember {
        CoroutineScope(Dispatchers.Default + SupervisorJob())
    }

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

            val startTimestamp = System.currentTimeMillis()
            translationViewModel.translateAsFlow(input, languages!!)
                .catch {
                    // TODO: Show error message for translation failure
                    Log.e("TextTranslation", "Translation failed", it)
                }
                .collect {
                    textTranslationViewModel.setTranslation(it)
                }

            Log.d(
                "TextTranslation",
                "Translation took ${System.currentTimeMillis() - startTimestamp}ms"
            )
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

    fun onCopy(output: String) {
        clipboardManager.setText(AnnotatedString(output))
    }

    fun onShare(output: String) {
        // TODO: Improve share intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, output)
        }
        val chooser = Intent.createChooser(shareIntent, "Share this translation")
        context.startActivity(chooser, null)
    }

    LaunchedEffect(languages) {
        clearTranslation()
    }

    LaunchedEffect(input) {
        if (!translateOnChange) {
            return@LaunchedEffect
        }

        if (input.isEmpty()) {
            return@LaunchedEffect
        }

        translateOnChange = false
        translate(input)
    }

    ScaffoldModalBottomSheet(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
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
                    .padding(bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + translationBottomPadding)
            ) {
                item {
                    TextTranslationOutput(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.spacing.large)
                            .padding(top = MaterialTheme.spacing.large),
                        translation = translated,
                        transliteration = translatedTransliteration,
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
                    onCopy(translated)
                },
                onShare = {
                    onShare(translated)
                },
            )
        }
    )
}

@Composable
fun TextTranslationInputField(
    input: String,
    transliteration: String,
    onValueChange: (String) -> Unit = {},
    onSubmit: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        modifier = modifier
    ) {
        TextField(
            placeholder = "Type something",
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 192.dp),
            value = input,
            onValueChange = onValueChange,
            onSubmit = { onSubmit(input) },
            colors = TextFieldDefaults.colorsTransparent()
        )

        if (transliteration.isNotEmpty()) {
            Text(
                text = transliteration,
                modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun TextTranslationInputButtonRow(
    onTranslate: () -> Unit,
    onClear: () -> Unit,
    onDictate: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
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

            FilledIconButton(
                onClick = {
                    onDictate?.invoke()
                },
                enabled = onDictate != null,
                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.MicNone,
                    contentDescription = stringResource(R.string.dictate)
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
    translation: String,
    transliteration: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(modifier),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.large, Alignment.Bottom)
    ) {
        Text(
            text = translation,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = transliteration,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun TextTranslationOutputButtonRow(
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
                        contentDescription = "Cancel"
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