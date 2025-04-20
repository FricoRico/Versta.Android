package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.icu.text.DecimalFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechSettings(
    navController: NavController,
    textToSpeechViewModel: TextToSpeechViewModel,
) {
    val orientation = LocalContext.current.resources.configuration.orientation

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
            textToSpeechViewModel.reload()
        }

        navController.popBackStack()
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
                    SettingsHeaderItem(
                        content = stringResource(R.string.text_to_speech_settings_voice_headline), groupSize = 3, index = 0
                    )
                }

                item {
                    SettingsButtonItem(
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
                        headlineContent = stringResource(R.string.text_to_speech_settings_voice_gender_title),
                        supportingContent = stringResource(R.string.text_to_speech_settings_voice_gender_description),
                        trailingContent = {
                            TooltipBox(
                                positionProvider = tooltipPositionProvider,
                                tooltip = {
                                    RichTooltip(
                                        colors = TooltipDefaults.richTooltipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                                        caretSize = DpSize(12.dp, 8.dp),
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
                                        text = voiceOptions[voiceGender] ?: stringResource(R.string.unknown),
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
                        content = stringResource(R.string.text_to_speech_settings_synthesis_headline), groupSize = 2, index = 0
                    )
                }

                item {
                    SettingsButtonItem(
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
@Preview(showBackground = true)
fun TextToSpeechSettingsPreview() {
    TextToSpeechSettings(
        navController = rememberNavController(),
        textToSpeechViewModel = TextToSpeechViewModel(
            tokenizer = TextToSpeechMockTokenizer(),
            model = TextToSpeechMockInference(),
            audioPlayer = AudioMockPlayer(),
            voiceRepository = VoiceMemoryRepository(),
            textToSpeechPreferenceRepository = TextToSpeechPreferenceMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        )
    )
}