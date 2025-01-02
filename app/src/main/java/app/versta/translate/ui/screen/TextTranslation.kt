package app.versta.translate.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.TextField
import app.versta.translate.ui.component.TextFieldDefaults
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.TarExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTranslation(
    navController: NavController,
    languageViewModel: LanguageViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val source by textTranslationViewModel.sourceText.collectAsStateWithLifecycle("")
    val sourceRomanizated by textTranslationViewModel.sourceTextRomanizated.collectAsStateWithLifecycle("")
    val target by textTranslationViewModel.targetText.collectAsStateWithLifecycle("")
    val targetRomanizated by textTranslationViewModel.targetTextRomanizated.collectAsStateWithLifecycle("")

    val translationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val drawerScope = rememberCoroutineScope()
    val drawerState =
        rememberStandardBottomSheetState(
            skipHiddenState = false,
            initialValue = SheetValue.Expanded,
            confirmValueChange = {
                it != SheetValue.Hidden
            }
        )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = drawerState
    )

    fun translate(input: String) {
        translationScope.launch {
            translationViewModel.translateAsFlow(input)
                .collect {
                    textTranslationViewModel.setTargetText(it)
                }
        }
    }

    fun hideDrawer() {
        drawerScope.launch {
            drawerState.hide()
        }
    }

    fun expandDrawer() {
        drawerScope.launch {
            focusManager.clearFocus()
            drawerState.expand()
        }
    }

    fun shareText(output: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, output)
        }
        val chooser = Intent.createChooser(shareIntent, "Share this translation")
        context.startActivity(chooser, null)
    }

    if (source.isNotEmpty()) {
        LaunchedEffect(Unit) {
            expandDrawer()

            translate(source)
        }
    }

    BottomSheetScaffold(
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
                        text = "Translator"
                    )
                },
            )
        },
        content = { innerPadding ->
            val bottomPadding by animateDpAsState(
                targetValue =
                if (drawerState.targetValue != SheetValue.Hidden) (innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium)
                else (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.medium),
                label = "drawerBottomPadding"
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = MaterialTheme.spacing.small)
                    .padding(bottom = bottomPadding)
                    .padding(horizontal = MaterialTheme.spacing.small)
            ) {
                LanguageSelector(
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.extraSmall
                        ),
                    languageViewModel = languageViewModel,
                    onLanguageSwap = {
                        textTranslationViewModel.setSourceText(target)
                        textTranslationViewModel.clearTargetText()

                        hideDrawer()
                    }
                )
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column {
                        TextField(
                            placeholder = "Type something",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = MaterialTheme.spacing.small)
                                .padding(horizontal = MaterialTheme.spacing.medium)
                                .defaultMinSize(minHeight = 192.dp),
                            value = source,
                            onValueChange = {
                                textTranslationViewModel.setSourceText(it)
                            },
                            onSubmit = {
                                translate(source)
                                expandDrawer()
                            },
                            colors = TextFieldDefaults.colorsTransparent()
                        )

                        if (sourceRomanizated.isNotEmpty()) {
                            Text(
                                text = sourceRomanizated,
                                modifier = Modifier
                                    .padding(MaterialTheme.spacing.medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.spacing.small)
                            .padding(bottom = MaterialTheme.spacing.small)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                        ) {
                            FilledIconButton(
                                onClick = {
                                    textTranslationViewModel.clearSourceText()
                                    textTranslationViewModel.clearTargetText()
                                    hideDrawer()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear"
                                )
                            }

                            FilledIconButton(
                                onClick = {},
                                enabled = false,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    disabledContentColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MicNone,
                                    contentDescription = "Clear"
                                )
                            }
                        }

                        FilledIconButton(
                            onClick = {
                                translate(source)
                                expandDrawer()
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Translate,
                                contentDescription = "Translate"
                            )
                        }
                    }
                }
            }
        },
        scaffoldState = scaffoldState,
        sheetPeekHeight = BottomSheetDefaults.SheetPeekHeight + MaterialTheme.spacing.large,
        sheetContent = {
            val availableHeightDp =
                1f - (WindowInsets.statusBars.asPaddingValues()
                    .calculateTopPadding() / LocalConfiguration.current.screenHeightDp.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(availableHeightDp)
                    .padding(MaterialTheme.spacing.small)
                    .padding(vertical = MaterialTheme.spacing.medium)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.spacing.small)
                        .padding(bottom = MaterialTheme.spacing.small)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = MaterialTheme.spacing.small,
                        alignment = Alignment.End
                    ),
                ) {
                    FilledIconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(target))
                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT)
                                .show()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy"
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            shareText(target)
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share"
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
//                    items(target.split("\n")) { line ->
                    item {
                        Text(
                            text = target,
                            modifier = Modifier
                                .padding(MaterialTheme.spacing.large),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    item {
                        Text(
                            text = targetRomanizated,
                            modifier = Modifier
                                .padding(MaterialTheme.spacing.large),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    )
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
            modelExtractor = TarExtractor(LocalContext.current),
            languageDatabaseRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        translationViewModel = TranslationViewModel(
            tokenizer = MockTokenizer(),
            model = MockInference(),
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        )
    )
}