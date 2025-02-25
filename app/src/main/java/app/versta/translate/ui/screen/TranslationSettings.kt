package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.icu.text.DecimalFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.DEFAULT_CACHE_ENABLED
import app.versta.translate.adapter.outbound.DEFAULT_CACHE_SIZE
import app.versta.translate.adapter.outbound.DEFAULT_MAX_SEQUENCE_LENGTH
import app.versta.translate.adapter.outbound.DEFAULT_MIN_PROBABILITY
import app.versta.translate.adapter.outbound.DEFAULT_NUMBER_OF_BEAMS
import app.versta.translate.adapter.outbound.DEFAULT_REPETITION_PENALTY
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.MockInference
import app.versta.translate.adapter.outbound.MockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.component.SliderLogarithmic
import app.versta.translate.ui.component.SliderPredefinedValues
import app.versta.translate.ui.theme.spacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationSettings(
    navController: NavController,
    translationViewModel: TranslationViewModel,
) {
    val orientation = LocalContext.current.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    val maxThreadCount = remember { Runtime.getRuntime().availableProcessors() }
    val cacheSizeOptions = remember { listOf(16, 32, 64, 128, 256, 512, Int.MAX_VALUE) }
    val sequenceLengthOptions = remember { listOf(16, 32, 64, 128, 256, 512) }

    val cacheSize by translationViewModel.cacheSize.collectAsStateWithLifecycle(DEFAULT_CACHE_SIZE)
    val cacheEnabled by translationViewModel.cacheEnabled.collectAsStateWithLifecycle(
        DEFAULT_CACHE_ENABLED
    )
    val beamSize by translationViewModel.beamSize.collectAsStateWithLifecycle(
        DEFAULT_NUMBER_OF_BEAMS
    )
    val maxSequenceLength by translationViewModel.maxSequenceLength.collectAsStateWithLifecycle(
        DEFAULT_MAX_SEQUENCE_LENGTH
    )
    val minProbability by translationViewModel.minProbability.collectAsStateWithLifecycle(
        DEFAULT_MIN_PROBABILITY
    )
    val repetitionPenalty by translationViewModel.repetitionPenalty.collectAsStateWithLifecycle(
        DEFAULT_REPETITION_PENALTY
    )
    val threadCount by translationViewModel.threadCount.collectAsStateWithLifecycle(
        maxThreadCount / 2
    )

    var settingsChanged by remember {
        mutableStateOf(false)
    }

    fun onBackNavigation() {
        if (settingsChanged) {
            translationViewModel.reload()
        }

        navController.popBackStack()
    }

    BackHandler {
        onBackNavigation()
    }

    ScaffoldLargeHeader(topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarSecondaryColor(),
        title = {
            Text(
                text = "Translation",
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                onBackNavigation()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                )
            ) {
                item {
                    SettingsHeaderItem(
                        headlineContent = "History", groupSize = 3, index = 0
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Remember translations",
                        supportingContent = "Save for later reference, and avoid re-translating the same text.",
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
                        headlineContent = "History size",
                        supportingContent = "How many translations to keep, only the newest will be saved.",
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
                        headlineContent = "Translation", groupSize = 5, index = 0
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Beam size",
                        supportingContent = "The number of translations to consider during decoding. Higher values may improve translation quality, but will be slower.",
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
                        groupSize = 5,
                        index = 1
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Max sequence length",
                        supportingContent = "The maximum length of the input sequence. Longer text may be truncated.",
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
                        groupSize = 5,
                        index = 2
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Minimum probability",
                        supportingContent = "The minimum probability for a token to be considered during decoding. Lower values may improve translation quality, but may introduce more errors.",
                        trailingContent = {
                            Text(
                                text = DecimalFormat("0.000").format(minProbability),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 22.sp
                            )
                        },
                        underlineContent = {
                            SliderLogarithmic(
                                value = minProbability,
                                minValue = 0.01f,
                                maxValue = 0.5f,
                                onValueChange = {
                                    settingsChanged = true

                                    val rounded = (it * 1000).roundToInt() / 1000f
                                    translationViewModel.setMinProbability(rounded)
                                },
                            )
                        },
                        groupSize = 5,
                        index = 3
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Word repetition penalty",
                        supportingContent = "The penalty for repeating tokens in the translation. Higher values may reduce repeated words, but may introduce more context loss.",
                        trailingContent = {
                            Text(
                                text = DecimalFormat("0.000").format(repetitionPenalty),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 22.sp
                            )
                        },
                        underlineContent = {
                            Slider(
                                value = repetitionPenalty,
                                valueRange = 0.0f..2.0f,
                                onValueChange = {
                                    settingsChanged = true

                                    val rounded = (it * 1000).roundToInt() / 1000f
                                    translationViewModel.setRepetitionPenalty(rounded)
                                },
                            )
                        },
                        groupSize = 5,
                        index = 3
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Thread count",
                        supportingContent = "Limit the number of threads used for translation. Higher values may improve performance, but may impact other processes like voice input.",
                        trailingContent = {
                            Text(
                                text = threadCount.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 22.sp
                            )
                        },
                        underlineContent = {
                            Slider(
                                value = threadCount.toFloat(),
                                onValueChange = {
                                    settingsChanged = true
                                    translationViewModel.setThreadCount(it.toInt())
                                },
                                valueRange = 1f..maxThreadCount.toFloat(),
                                steps = maxThreadCount - 2,
                            )
                        },
                        groupSize = 5,
                        index = 4
                    )
                }
            }
        })
}

@Composable
@Preview(showBackground = true)
fun TranslationSettingsPreview() {
    TranslationSettings(
        navController = rememberNavController(), translationViewModel = TranslationViewModel(
            tokenizer = MockTokenizer(),
            model = MockInference(),
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            translationPreferenceRepository = TranslationPreferenceMemoryRepository()
        )
    )
}