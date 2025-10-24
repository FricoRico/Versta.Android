package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
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
import app.versta.translate.adapter.outbound.DEFAULT_OBJECT_CHARACTER_RECOGNIZER_CROP_WIDTH
import app.versta.translate.adapter.outbound.DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_HEIGHT
import app.versta.translate.adapter.outbound.DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_WIDTH
import app.versta.translate.adapter.outbound.DEFAULT_OBJECT_CHARACTER_RECOGNIZER_MAX_BATCH_SIZE
import app.versta.translate.adapter.outbound.DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_HEIGHT
import app.versta.translate.adapter.outbound.DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_WIDTH
import app.versta.translate.adapter.outbound.ObjectCharacterRecognizerPreferenceMemoryRepository
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ObjectCharacterRecognizerViewModel
import app.versta.translate.core.model.ScaffoldViewModel
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
fun ObjectCharacterRecognizerSettings(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    objectCharacterRecognizerViewModel: ObjectCharacterRecognizerViewModel
) {
    val layoutDirection = LocalLayoutDirection.current

    val sizeOptions = remember { listOf(320, 480, 640, 960, 1280) }
    val cropWidthOptions = remember { listOf(320, 480, 640, 960) }
    val batchSizeOptions = remember { listOf(8, 16, 24, 32, 48) }

    val detectWidth by objectCharacterRecognizerViewModel.detectWidth.collectAsStateWithLifecycle(
        DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_WIDTH
    )
    val detectHeight by objectCharacterRecognizerViewModel.detectHeight.collectAsStateWithLifecycle(
        DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_HEIGHT
    )
    val recognizeWidth by objectCharacterRecognizerViewModel.recognizeWidth.collectAsStateWithLifecycle(
        DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_WIDTH
    )
    val recognizeHeight by objectCharacterRecognizerViewModel.recognizeHeight.collectAsStateWithLifecycle(
        DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_HEIGHT
    )
    val cropWidth by objectCharacterRecognizerViewModel.cropWidth.collectAsStateWithLifecycle(
        DEFAULT_OBJECT_CHARACTER_RECOGNIZER_CROP_WIDTH
    )
    val maxBatchSize by objectCharacterRecognizerViewModel.maxBatchSize.collectAsStateWithLifecycle(
        DEFAULT_OBJECT_CHARACTER_RECOGNIZER_MAX_BATCH_SIZE
    )

    var settingsChanged by remember {
        mutableStateOf(false)
    }

    navigationViewModel.onNavigationCallback {
        if (!settingsChanged) {
            return@onNavigationCallback
        }

        // Reload OCR inference if needed
        // This would need to be implemented based on how the app manages OCR lifecycle
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.object_character_recognizer_settings_title))
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
                    content = stringResource(R.string.object_character_recognizer_settings_detection_headline),
                    groupSize = 3,
                    index = 0
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.object_character_recognizer_settings_detect_width_title),
                    supportingContent = stringResource(R.string.object_character_recognizer_settings_detect_width_description),
                    trailingContent = {
                        Text(
                            text = detectWidth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        SliderPredefinedValues(
                            value = detectWidth,
                            options = sizeOptions,
                            onValueChange = {
                                settingsChanged = true
                                objectCharacterRecognizerViewModel.setDetectWidth(it)
                            },
                        )
                    },
                    groupSize = 3,
                    index = 1
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.object_character_recognizer_settings_detect_height_title),
                    supportingContent = stringResource(R.string.object_character_recognizer_settings_detect_height_description),
                    trailingContent = {
                        Text(
                            text = detectHeight.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        SliderPredefinedValues(
                            value = detectHeight,
                            options = sizeOptions,
                            onValueChange = {
                                settingsChanged = true
                                objectCharacterRecognizerViewModel.setDetectHeight(it)
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
                    content = stringResource(R.string.object_character_recognizer_settings_recognition_headline),
                    groupSize = 4,
                    index = 0
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.object_character_recognizer_settings_recognize_width_title),
                    supportingContent = stringResource(R.string.object_character_recognizer_settings_recognize_width_description),
                    trailingContent = {
                        Text(
                            text = recognizeWidth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        SliderPredefinedValues(
                            value = recognizeWidth,
                            options = sizeOptions,
                            onValueChange = {
                                settingsChanged = true
                                objectCharacterRecognizerViewModel.setRecognizeWidth(it)
                            },
                        )
                    },
                    groupSize = 4,
                    index = 1
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.object_character_recognizer_settings_recognize_height_title),
                    supportingContent = stringResource(R.string.object_character_recognizer_settings_recognize_height_description),
                    trailingContent = {
                        Text(
                            text = recognizeHeight.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        SliderPredefinedValues(
                            value = recognizeHeight,
                            options = sizeOptions,
                            onValueChange = {
                                settingsChanged = true
                                objectCharacterRecognizerViewModel.setRecognizeHeight(it)
                            },
                        )
                    },
                    groupSize = 4,
                    index = 2
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.object_character_recognizer_settings_crop_width_title),
                    supportingContent = stringResource(R.string.object_character_recognizer_settings_crop_width_description),
                    trailingContent = {
                        Text(
                            text = cropWidth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        SliderPredefinedValues(
                            value = cropWidth,
                            options = cropWidthOptions,
                            onValueChange = {
                                settingsChanged = true
                                objectCharacterRecognizerViewModel.setCropWidth(it)
                            },
                        )
                    },
                    groupSize = 4,
                    index = 3
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.object_character_recognizer_settings_max_batch_size_title),
                    supportingContent = stringResource(R.string.object_character_recognizer_settings_max_batch_size_description),
                    trailingContent = {
                        Text(
                            text = maxBatchSize.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        SliderPredefinedValues(
                            value = maxBatchSize,
                            options = batchSizeOptions,
                            onValueChange = {
                                settingsChanged = true
                                objectCharacterRecognizerViewModel.setMaxBatchSize(it)
                            },
                        )
                    },
                    groupSize = 4,
                    index = 4
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun ObjectCharacterRecognizerSettingsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    ObjectCharacterRecognizerSettings(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        objectCharacterRecognizerViewModel = ObjectCharacterRecognizerViewModel(
            preferenceRepository = ObjectCharacterRecognizerPreferenceMemoryRepository()
        )
    )
}
