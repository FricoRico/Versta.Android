package app.versta.translate.ui.screen

import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.icu.text.DecimalFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.DEFAULT_SPEED
import app.versta.translate.adapter.outbound.DEFAULT_VOICE_GENDER
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsHeaderItem
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.versta.translate.adapter.outbound.DataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalDataMemoryRepository
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.entity.DownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechSettings(
    navController: NavController,
    textToSpeechViewModel: TextToSpeechViewModel,
) {
    val orientation = LocalConfiguration.current.orientation

    val tooltipScope = rememberCoroutineScope()
    val tooltipState = rememberTooltipState(isPersistent = true)
    val tooltipPositionProvider = rememberTooltipPositionProvider()

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

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

    fun onBackNavigation() {
        if (settingsChanged) {
            textToSpeechViewModel.reloadVoice()
        }

        navController.popBackStack()
    }

    LaunchedEffect(downloadTask) {
        if (downloadTask == null || downloadTask.status !is DownloadStatus.Error) {
            return@LaunchedEffect
        }

        textToSpeechViewModel.setTextToSpeechEnabled(false)
    }

    BackHandler {
        onBackNavigation()
    }

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowestColor(),
        title = {
            Text(
                text = stringResource(R.string.text_to_speech_settings_title),
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
                modifier = Modifier.nestedScroll(scrollConnection), contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
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
                            Box (
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
                                        imageVector = Icons.Default.PriorityHigh,
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
        })
}

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
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "Waiting for other downloads to finish...",
                style = MaterialTheme.typography.bodyMedium,
            )

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }

    AnimatedVisibility(
        visible = status is DownloadStatus.Progress,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500)),
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

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }

    AnimatedVisibility(
        visible = status is DownloadStatus.Processing,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            modifier = Modifier.padding(padding)
        ) {
            Text(
                text = "Extracting...",
                style = MaterialTheme.typography.bodyMedium,
            )

            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }

    AnimatedVisibility(
        visible = status is DownloadStatus.Error,
        enter = fadeIn(animationSpec = tween(500)),
        exit = fadeOut(animationSpec = tween(500)),
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
@SuppressLint("ViewModelConstructorInComposable")
fun TextToSpeechSettingsPreview() {
    TextToSpeechSettings(
        navController = rememberNavController(),
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