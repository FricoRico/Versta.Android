package app.versta.translate.ui.screen

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
    val input by textTranslationViewModel.sourceText.collectAsStateWithLifecycle("")
    val target by textTranslationViewModel.targetText.collectAsStateWithLifecycle("")

    val translationScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val drawerScope = rememberCoroutineScope()
    val drawerState =
        rememberStandardBottomSheetState(
            confirmValueChange = {
                it != SheetValue.Hidden
            }
        )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = drawerState
    )

    fun translate() {
        val startTime = System.currentTimeMillis()
        translationScope.launch {
            translationViewModel.translateAsFlow(input)
                .collect {
                    textTranslationViewModel.setTargetText(it)
                }
            val elapsedTime = System.currentTimeMillis() - startTime

            Log.d("TranslationTextFieldMinimal", "Translated in ${elapsedTime}ms. Text: $target")
        }

        drawerScope.launch {
            drawerState.expand()
        }
    }

    if (input.isNotEmpty()) {
        LaunchedEffect(Unit) {
            translate()
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
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(
                        bottom = MaterialTheme.spacing.large
                    )
            ) {
                LanguageSelector(
                    modifier = Modifier
                        .padding(
                            horizontal = MaterialTheme.spacing.extraSmall
                        ),
                    languageViewModel = languageViewModel,
                )
                TextField(
                    placeholder = "Type something",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MaterialTheme.spacing.small)
                        .padding(horizontal = MaterialTheme.spacing.small)
                        .defaultMinSize(minHeight = 192.dp),
                    value = input,
                    onValueChange = {
                        textTranslationViewModel.setSourceText(it)
                    },
                    onSubmit = {
                        translate()
                    },
                    colors = TextFieldDefaults.colorsTransparent()
                )
            }
        },
        scaffoldState = scaffoldState,
        sheetPeekHeight = BottomSheetDefaults.SheetPeekHeight + MaterialTheme.spacing.large,
        sheetContent = {
            val availableHeightDp =
                1f - (WindowInsets.statusBars.asPaddingValues()
                    .calculateTopPadding() / LocalConfiguration.current.screenHeightDp.dp)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(availableHeightDp)
                    .padding(MaterialTheme.spacing.small)
                    .padding(bottom = MaterialTheme.spacing.large)
            ) {
                item {
                    Text(
                        text = target,
                        modifier = Modifier
                            .padding(MaterialTheme.spacing.large),
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
        textTranslationViewModel = TextTranslationViewModel(),
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