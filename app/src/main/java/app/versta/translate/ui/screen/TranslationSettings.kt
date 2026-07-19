package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.DEFAULT_CACHE_ENABLED
import app.versta.translate.adapter.outbound.DEFAULT_CACHE_SIZE
import app.versta.translate.adapter.outbound.DEFAULT_MAX_SEQUENCE_LENGTH
import app.versta.translate.adapter.outbound.DEFAULT_NUMBER_OF_BEAMS
import app.versta.translate.adapter.outbound.DEFAULT_PIVOT_TRANSLATION
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.component.SliderPredefinedValues
import app.versta.translate.ui.theme.spacing

@Composable
fun TranslationSettings(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    translationViewModel: TranslationViewModel,
    languageViewModel: LanguageViewModel
) {
    val layoutDirection = LocalLayoutDirection.current

    val cacheSizeOptions = remember { listOf(64, 256, 1024, 4096, 8192, Int.MAX_VALUE) }
    val sequenceLengthOptions = remember { listOf(16, 32, 64, 128, 256, 512) }

    val cacheSize by translationViewModel.cacheSize.collectAsStateWithLifecycle(DEFAULT_CACHE_SIZE)
    val cacheEnabled by translationViewModel.cacheEnabled.collectAsStateWithLifecycle(
        DEFAULT_CACHE_ENABLED
    )
    val pivotTranslationEnabled by languageViewModel.pivotTranslationEnabled.collectAsStateWithLifecycle(
        DEFAULT_PIVOT_TRANSLATION
    )
    val beamSize by translationViewModel.beamSize.collectAsStateWithLifecycle(
        DEFAULT_NUMBER_OF_BEAMS
    )
    val maxSequenceLength by translationViewModel.maxSequenceLength.collectAsStateWithLifecycle(
        DEFAULT_MAX_SEQUENCE_LENGTH
    )

    var settingsChanged by remember {
        mutableStateOf(false)
    }

    navigationViewModel.onNavigationCallback {
        if (!settingsChanged) {
            return@onNavigationCallback
        }

        translationViewModel.reload()
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.translation_settings_title))
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            ScaffoldCompactBarEmptyActions()
        },
        actionsContentKey = "ScaffoldCompactBarEmptyActions",
        wrapContent = true
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            )
        ) {
                item {
                    SettingsHeaderItem(
                        content = stringResource(R.string.translation_settings_history_headline),
                        groupSize = 3,
                        index = 0
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.translation_settings_history_toggle_title),
                        supportingContent = stringResource(R.string.translation_settings_history_toggle_description),
                        onClick = {
                            settingsChanged = true
                            translationViewModel.setCacheEnabled(!cacheEnabled)
                        },
                        trailingContent = {
                            Switch(
                                checked = cacheEnabled,
                                onCheckedChange = {
                                    settingsChanged = true
                                    translationViewModel.setCacheEnabled(it)
                                },
                            )
                        },
                        groupSize = 3,
                        index = 1
                    )
                }

                item {
                    SettingsButtonItem(
                        enabled = cacheEnabled,
                        headlineContent = stringResource(R.string.translation_settings_history_size_title),
                        supportingContent = stringResource(R.string.translation_settings_history_size_description),
                        trailingContent = {
                            Text(
                                text = if (cacheSize != Int.MAX_VALUE) cacheSize.toString() else "∞",
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 22.sp
                            )
                        },
                        underlineContent = {
                            SliderPredefinedValues(
                                value = cacheSize,
                                options = cacheSizeOptions,
                                onValueChange = {
                                    settingsChanged = true
                                    translationViewModel.setCacheSize(it)
                                },
                            )
                        },
                        groupSize = 3,
                        index = 2
                    )
                }

                ListDivider()

                item {
                    SettingsHeaderItem(
                        content = stringResource(R.string.translation_settings_inference_headline),
                        groupSize = 4,
                        index = 0
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.translation_settings_language_pivot_translation_title),
                        supportingContent = stringResource(R.string.translation_settings_language_pivot_translation_description),
                        onClick = {
                            settingsChanged = true
                            languageViewModel.setPivotTranslationEnabled(!pivotTranslationEnabled)
                        },
                        trailingContent = {
                            Switch(
                                checked = pivotTranslationEnabled,
                                onCheckedChange = {
                                    settingsChanged = true
                                    languageViewModel.setPivotTranslationEnabled(it)
                                },
                            )
                        },
                        groupSize = 4,
                        index = 1
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.translation_settings_inference_beam_size_title),
                        supportingContent = stringResource(R.string.translation_settings_inference_beam_size_description),
                        trailingContent = {
                            Text(
                                text = beamSize.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 22.sp
                            )
                        },
                        underlineContent = {
                            Slider(
                                value = beamSize.toFloat(),
                                onValueChange = {
                                    settingsChanged = true
                                    translationViewModel.setBeamSize(it.toInt())
                                },
                                valueRange = 1f..8f,
                                steps = 6,
                            )
                        },
                        groupSize = 4,
                        index = 2
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.translation_settings_inference_max_length_title),
                        supportingContent = stringResource(R.string.translation_settings_inference_max_length_description),
                        trailingContent = {
                            Text(
                                text = maxSequenceLength.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 22.sp
                            )
                        },
                        underlineContent = {
                            SliderPredefinedValues(
                                value = maxSequenceLength,
                                options = sequenceLengthOptions,
                                onValueChange = {
                                    settingsChanged = true
                                    translationViewModel.setMaxSequenceLength(it)
                                },
                            )
                        },
                        groupSize = 4,
                        index = 3
                    )
                }
            }
        }
}

@Composable
@Preview(showBackground = true)
fun TranslationSettingsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    val languageViewModel = LanguageViewModel(
        context = LocalContext.current,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )

    val translationMockInference = TranslationMockInference()

    TranslationSettings(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        translationViewModel = TranslationViewModel(
            intermediateModel = translationMockInference,
            outputModel = translationMockInference,
            translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
            languageViewModel = languageViewModel
        ),
        languageViewModel = languageViewModel
    )
}