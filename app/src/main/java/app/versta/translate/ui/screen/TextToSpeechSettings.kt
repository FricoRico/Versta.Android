package app.versta.translate.ui.screen

import android.icu.text.DecimalFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.DEFAULT_SPEED
import app.versta.translate.adapter.outbound.DEFAULT_VOICE_GENDER
import app.versta.translate.adapter.outbound.DataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalDataMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechSettings(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel,
) {
    val layoutDirection = LocalLayoutDirection.current

    val tooltipScope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val tooltipPositionProvider = rememberTooltipPositionProvider()

    val maxThreadCount = remember { Runtime.getRuntime().availableProcessors() }
    var voiceOptionsExpanded by remember { mutableStateOf(false) }
    val voiceOptions = mapOf(
        VoiceGender.Female to stringResource(R.string.text_to_speech_settings_voice_gender_female),
        VoiceGender.Male to stringResource(R.string.text_to_speech_settings_voice_gender_male)
    )

    val speed by textToSpeechViewModel.speed.collectAsStateWithLifecycle(DEFAULT_SPEED)
    val voiceGender by textToSpeechViewModel.gender.collectAsStateWithLifecycle(DEFAULT_VOICE_GENDER)
    val threadCount by textToSpeechViewModel.threadCount.collectAsStateWithLifecycle(
        maxThreadCount
    )
    val textToSpeechEnabled by textToSpeechViewModel.enabled.collectAsStateWithLifecycle(false)
    val downloadTasks by textToSpeechViewModel.downloadTasks.collectAsStateWithLifecycle(emptyList())
    val downloadTask = downloadTasks.firstOrNull()

    var settingsChanged by remember {
        mutableStateOf(false)
    }

    fun onShowPreferredGenderTooltip() {
        tooltipScope.launch {
            tooltipState.show()
        }
    }

    navigationViewModel.onNavigationCallback {
        if (!settingsChanged) {
            return@onNavigationCallback
        }

        textToSpeechViewModel.reloadVoice()
    }

    LaunchedEffect(downloadTask) {
        if (downloadTask == null || downloadTask.status !is DownloadStatus.Error) {
            return@LaunchedEffect
        }

        textToSpeechViewModel.setTextToSpeechEnabled(false)
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.text_to_speech_settings_title))
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
                SettingsButtonItem(
                    headlineContent = "Enable Text-to-Speech",
                    supportingContent = "Enabling this will download the necessary data.",
                    onClick = {
                        settingsChanged = true
                        textToSpeechViewModel.setTextToSpeechEnabled(!textToSpeechEnabled)
                    },
                    trailingContent = {
                        Switch(
                            checked = textToSpeechEnabled,
                            onCheckedChange = {
                                settingsChanged = true
                                textToSpeechViewModel.setTextToSpeechEnabled(it)
                            },
                        )
                    },
                    underlineContent = {
                        Box(
                            modifier = Modifier.animateContentSize()
                        ) {
                            TextToSpeechDataDownloadProgress(
                                status = downloadTask?.status
                            )
                        }
                    },
                    underlineContentPadding = PaddingValues(MaterialTheme.spacing.extraSmall),
                    groupSize = 1,
                    index = 0,
                )
            }

            ListDivider()

            item {
                SettingsHeaderItem(
                    enabled = textToSpeechEnabled,
                    content = stringResource(R.string.text_to_speech_settings_voice_headline),
                    groupSize = 3,
                    index = 0
                )
            }

            item {
                SettingsButtonItem(
                    enabled = textToSpeechEnabled,
                    headlineContent = stringResource(R.string.text_to_speech_settings_voice_speech_rate_title),
                    supportingContent = stringResource(R.string.text_to_speech_settings_voice_speech_rate_description),
                    trailingContent = {
                        Text(
                            text = DecimalFormat("0.0").format(speed),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        Slider(
                            enabled = textToSpeechEnabled,
                            value = speed,
                            onValueChange = {
                                val rounded = (it * 10).roundToInt() / 10f
                                textToSpeechViewModel.setSpeed(rounded)
                            },
                            valueRange = 0.5f..2.0f,
                            steps = 14,
                        )
                    },
                    groupSize = 3,
                    index = 1
                )
            }

            item {
                SettingsButtonItem(
                    enabled = textToSpeechEnabled,
                    headlineContent = stringResource(R.string.text_to_speech_settings_voice_gender_title),
                    supportingContent = stringResource(R.string.text_to_speech_settings_voice_gender_description),
                    trailingContent = {
                        TooltipBox(
                            positionProvider = tooltipPositionProvider,
                            tooltip = {
                                RichTooltip(
                                    colors = TooltipDefaults.richTooltipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                    shadowElevation = 6.dp,
                                ) {
                                    Text(
                                        text = stringResource(R.string.text_to_speech_settings_voice_gender_footnote_text),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(
                                            horizontal = MaterialTheme.spacing.extraSmall,
                                            vertical = MaterialTheme.spacing.small
                                        ),
                                    )
                                }
                            },
                            state = tooltipState
                        ) {
                            FilledIconButton(
                                enabled = textToSpeechEnabled,
                                colors = FilledIconButtonDefaults.surfaceIconButtonColors(),
                                onClick = {
                                    onShowPreferredGenderTooltip()
                                }
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.rounded_priority_high_24),
                                    contentDescription = stringResource(R.string.text_to_speech_settings_voice_gender_footnote_button)
                                )
                            }
                        }
                    },
                    underlineContentPadding = PaddingValues(),
                    underlineContent = {
                        ExposedDropdownMenuBox(
                            expanded = voiceOptionsExpanded,
                            onExpandedChange = { voiceOptionsExpanded = !voiceOptionsExpanded }
                        ) {
                            Button(
                                enabled = textToSpeechEnabled,
                                colors = ButtonDefaults.transparentButtonColors(),
                                onClick = { voiceOptionsExpanded = true },
                                contentPadding = PaddingValues(
                                    vertical = MaterialTheme.spacing.medium,
                                    horizontal = MaterialTheme.spacing.large
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(
                                        type = PrimaryNotEditable,
                                        enabled = true
                                    )
                            ) {
                                Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = voiceOptions[voiceGender]
                                        ?: stringResource(R.string.unknown),
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = TextAlign.Start,
                                    fontSize = 22.sp
                                )
                            }

                            ExposedDropdownMenu(
                                expanded = voiceOptionsExpanded,
                                shape = MaterialTheme.shapes.extraLarge,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                onDismissRequest = { voiceOptionsExpanded = false },
                                shadowElevation = 6.dp,
                            ) {
                                voiceOptions.map { voice ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = voice.value,
                                                fontSize = MaterialTheme.typography.bodyLarge.fontSize
                                            )
                                        },
                                        contentPadding = PaddingValues(
                                            vertical = MaterialTheme.spacing.medium,
                                            horizontal = MaterialTheme.spacing.large
                                        ),
                                        onClick = {
                                            settingsChanged = true

                                            voiceOptionsExpanded = false
                                            textToSpeechViewModel.setGender(voice.key)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    groupSize = 3,
                    index = 2
                )
            }

            ListDivider()

            item {
                SettingsHeaderItem(
                    enabled = textToSpeechEnabled,
                    content = stringResource(R.string.text_to_speech_settings_synthesis_headline),
                    groupSize = 2,
                    index = 0
                )
            }

            item {
                SettingsButtonItem(
                    enabled = textToSpeechEnabled,
                    headlineContent = stringResource(R.string.text_to_speech_settings_synthesis_thread_limit_title),
                    supportingContent = stringResource(R.string.text_to_speech_settings_synthesis_thread_limit_description),
                    trailingContent = {
                        Text(
                            text = threadCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 22.sp
                        )
                    },
                    underlineContent = {
                        Slider(
                            enabled = textToSpeechEnabled,
                            value = threadCount.toFloat(),
                            onValueChange = {
                                settingsChanged = true
                                textToSpeechViewModel.setThreadCount(it.toInt())
                            },
                            valueRange = 1f..maxThreadCount.toFloat(),
                            steps = maxThreadCount - 2,
                        )
                    },
                    groupSize = 2,
                    index = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TextToSpeechDataDownloadProgress(
    status: DownloadStatus?,
) {
    if (status == null || status is DownloadStatus.Completed || status is DownloadStatus.Idle) {
        return
    }

    val padding = PaddingValues(
        start = MaterialTheme.spacing.large,
        end = MaterialTheme.spacing.large,
        bottom = MaterialTheme.spacing.large
    )
    AnimatedVisibility(
        visible = status is DownloadStatus.Queued,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "Waiting for other downloads to finish...",
                style = MaterialTheme.typography.bodyMedium,
            )

            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }
    }

    AnimatedVisibility(
        visible = status is DownloadStatus.Progress,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        if (status !is DownloadStatus.Progress) return@AnimatedVisibility
        val progress = status.downloaded.toFloat() / status.total.toFloat()

        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "Downloading required data...",
                style = MaterialTheme.typography.bodyMedium,
            )

            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }
    }

    AnimatedVisibility(
        visible = status is DownloadStatus.Processing,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "Extracting...",
                style = MaterialTheme.typography.bodyMedium,
            )

            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                wavelength = MaterialTheme.spacing.extraExtraLarge,
                waveSpeed = MaterialTheme.spacing.large,
            )
        }
    }

    AnimatedVisibility(
        visible = status is DownloadStatus.Error,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "Something went wrong while downloading the required data. Please try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
fun TextToSpeechSettingsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    TextToSpeechSettings(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
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
            externalDataRepository = ExternalDataMemoryRepository()
        )
    )
}