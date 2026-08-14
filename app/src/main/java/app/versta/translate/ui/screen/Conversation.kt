package app.versta.translate.ui.screen

import android.Manifest
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.ExternalSpeechRecognitionModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.SpeechRecognitionMemoryRepository
import app.versta.translate.adapter.outbound.SpeechRecognitionMockInference
import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.ExternalSpeechRecognitionModels
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.SpeechRecognitionSegment
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ReadyState
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.SpeechRecognitionViewModel
import app.versta.translate.core.model.StartResult
import app.versta.translate.ui.component.GradientMicButton
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.VoiceWaveform
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.SPECTRUM_BAND_COUNT
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

/**
 * Voice-first conversation surface: the user speaks, whisper.cpp transcribes
 * on-device and finished utterances accumulate as a transcript. Mic loudness
 * drives the waveform and gradient mic orb.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Conversation(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    languageViewModel: LanguageViewModel,
    speechRecognitionViewModel: SpeechRecognitionViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layoutDirection = LocalLayoutDirection.current
    val lazyListState = rememberLazyListState()

    val segments by speechRecognitionViewModel.segments.collectAsStateWithLifecycle(emptyList())
    val listening by speechRecognitionViewModel.listening.collectAsStateWithLifecycle(false)
    val finalizing by speechRecognitionViewModel.finalizing.collectAsStateWithLifecycle(false)
    val spectrum by speechRecognitionViewModel.spectrum
        .collectAsStateWithLifecycle(FloatArray(SPECTRUM_BAND_COUNT))
    val readyState by speechRecognitionViewModel.speechRecognitionReadyState.collectAsStateWithLifecycle()
    val speechRecognitionModels by speechRecognitionViewModel.speechRecognitionModelsByState
        .collectAsStateWithLifecycle(ExternalSpeechRecognitionModels())

    val sourceLanguage by languageViewModel.sourceLanguage.collectAsStateWithLifecycle(null)
    val targetLanguage by languageViewModel.targetLanguage.collectAsStateWithLifecycle(null)

    val microphonePermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val permissionGranted = microphonePermission.status == PermissionStatus.Granted

    var pendingStart by remember { mutableStateOf(false) }

    val modelInstalled = speechRecognitionModels.installed.isNotEmpty()

    fun startDictation() {
        when (speechRecognitionViewModel.start(scope)) {
            StartResult.Started -> pendingStart = false
            StartResult.NotLoaded -> pendingStart = true
            StartResult.MicrophoneUnavailable -> Toast.makeText(
                context,
                R.string.speech_recognition_microphone_unavailable_message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun onToggle() {
        if (listening) {
            speechRecognitionViewModel.stop()
            return
        }
        if (!permissionGranted) {
            microphonePermission.launchPermissionRequest()
            return
        }
        startDictation()
    }

    // The model can still be loading when the user taps: queue the start and line
    // it off as soon as the recognizer reports ready.
    LaunchedEffect(readyState, pendingStart) {
        if (pendingStart && readyState == ReadyState.Ready) {
            pendingStart = false
            startDictation()
        }
    }

    // A granted permission satisfies a queued start immediately.
    LaunchedEffect(permissionGranted) {
        if (permissionGranted && pendingStart) {
            startDictation()
        }
    }

    // The speech recognition pipeline is app-scoped and shared; screens only
    // stop their own session. stop() no-ops when not listening.
    DisposableEffect(Unit) {
        onDispose {
            speechRecognitionViewModel.stop()
        }
    }

    LaunchedEffect(segments.size) {
        if (segments.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = { ScaffoldCompactBarTitle(text = stringResource(R.string.conversation_title)) },
        titleContentKey = "Conversation",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                    end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConversationLanguageChip(
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                )

                ConversationTranscript(
                    segments = segments,
                    lazyListState = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                ConversationStatus(
                    listening = listening,
                    finalizing = finalizing,
                    modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
                )

                VoiceWaveform(
                    spectrum = spectrum,
                    active = listening,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                )

                Spacer(modifier = Modifier.height(MaterialTheme.spacing.large))

                GradientMicButton(
                    listening = listening || pendingStart,
                    processing = finalizing || (pendingStart && !listening),
                    enabled = modelInstalled && permissionGranted,
                    onClick = { onToggle() },
                )
            }

            if (!permissionGranted) {
                ConversationPermissionDenied(
                    state = microphonePermission,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            } else if (!modelInstalled) {
                ConversationNoModel(
                    onManage = { navigationViewModel.navigate(Screens.SpeechRecognitionSettings) },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                )
            }
        }
    }
}

@Composable
private fun ConversationLanguageChip(
    sourceLanguage: LanguageOption?,
    targetLanguage: Language?,
    modifier: Modifier = Modifier,
) {
    val sourceName = when (sourceLanguage) {
        is Language -> sourceLanguage.name
        is AutoDetectLanguage -> sourceLanguage.name
        else -> null
    }
    val targetName = targetLanguage?.name
    if (sourceName == null || targetName == null) {
        return
    }

    Text(
        text = stringResource(R.string.language_attributes_source_target_combination, sourceName, targetName),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = modifier.padding(vertical = MaterialTheme.spacing.small),
    )
}

@Composable
private fun ConversationTranscript(
    segments: List<SpeechRecognitionSegment>,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = lazyListState,
        reverseLayout = true,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.Bottom),
        modifier = modifier,
    ) {
        items(
            items = segments.reversed(),
            key = { it.startMs },
        ) { segment ->
            val latest = segment == segments.lastOrNull()
            Text(
                text = segment.text,
                style = if (latest) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (latest) 1f else 0.55f),
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem(),
            )
        }
    }
}

@Composable
private fun ConversationStatus(
    listening: Boolean,
    finalizing: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = when {
        finalizing -> stringResource(R.string.conversation_processing)
        listening -> stringResource(R.string.conversation_listening)
        else -> stringResource(R.string.conversation_tap_to_speak)
    }

    AnimatedContent(
        targetState = label,
        label = "conversationStatus",
        modifier = modifier,
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun ConversationPermissionDenied(
    state: PermissionState,
    modifier: Modifier = Modifier,
) {
    val rationale = state.status.shouldShowRationale

    Column(
        modifier = modifier.padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.conversation_microphone_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        if (!rationale) {
            Button(onClick = { state.launchPermissionRequest() }) {
                Text(text = stringResource(R.string.conversation_grant_permission))
            }
        }
    }
}

@Composable
private fun ConversationNoModel(
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.conversation_no_model_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacing.medium))
        Button(onClick = onManage) {
            Text(text = stringResource(R.string.conversation_manage_speech_models))
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Preview(showBackground = true)
@Composable
private fun ConversationPreview() {
    val context = LocalContext.current
    val languageViewModel = LanguageViewModel(
        context = context,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )
    val navigationViewModel = NavigationViewModel(Screens.Conversation)

    Conversation(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        languageViewModel = languageViewModel,
        speechRecognitionViewModel = SpeechRecognitionViewModel(
            context = context,
            speechRecognitionRepository = SpeechRecognitionMemoryRepository(),
            externalSpeechRecognitionModelsRepository = ExternalSpeechRecognitionModelsMemoryRepository(),
            speechRecognitionInference = SpeechRecognitionMockInference(),
            languageViewModel = languageViewModel,
        ),
    )
}
