package app.versta.translate.ui.screen

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.DataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalDataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.TextField
import app.versta.translate.ui.component.TextFieldDefaults
import app.versta.translate.ui.theme.FilledIconButtonDefaults
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.darken
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import timber.log.Timber

@OptIn(
    ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class
)
@Composable
fun TextTranslation(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val layoutDirection = LocalLayoutDirection.current
    val lazyListState = rememberLazyListState()

    val input by textTranslationViewModel.input.collectAsStateWithLifecycle("")

    var floatingInputVisible by rememberSaveable { mutableStateOf(true) }

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        null
    }

    LaunchedEffect(notificationPermissionState) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return@LaunchedEffect
        }

        if (notificationPermissionState?.status == PermissionStatus.Granted) {
            return@LaunchedEffect
        }

        notificationPermissionState?.launchPermissionRequest()
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        bottomBar = {
            FloatingTextTranslationInputBar(
                visible = floatingInputVisible,
                navigationViewModel = navigationViewModel,
                textTranslationViewModel = textTranslationViewModel
            )
        }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium + FloatingToolbarDefaults.ScreenOffset + FloatingToolbarDefaults.ContainerSize
            ),
            verticalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.spacing.small,
                alignment = Alignment.Bottom
            ),
            modifier = Modifier
                .fillMaxSize()
                .floatingToolbarVerticalNestedScroll(
                    expanded = floatingInputVisible,
                    onExpand = { floatingInputVisible = true },
                    onCollapse = { floatingInputVisible = false },
                    reverseLayout = true
                ),
            state = lazyListState,
            reverseLayout = true,
        ) {

        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingTextTranslationInputBar(
    visible: Boolean,
    navigationViewModel: NavigationViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val value by textTranslationViewModel.input.collectAsStateWithLifecycle()

    var focussed by remember { mutableStateOf(false) }

    fun setFocus(state: Boolean) {
        focussed = state
    }

    fun onValueChange(text: String) {
        textTranslationViewModel.setInput(text)
    }

    fun onSubmit(text: String) {
        textTranslationViewModel.translate(text)
    }

    fun onNavigateVision() {
        navigationViewModel.navigate(Screens.Vision)
    }

    Box(
        modifier = Modifier
            .windowInsetsPadding(
                WindowInsets.systemBars
                    .union(WindowInsets.displayCutout)
                    .union(WindowInsets.ime)
                    .only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.End
                    )
            )
            .padding(
                horizontal = MaterialTheme.spacing.large
            )
            .padding(
                bottom = MaterialTheme.spacing.large
            )
            .fillMaxWidth()
    ) {
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.Center),
            visible = visible,
            exit = slideOutVertically { it } + fadeOut(),
            enter = slideInVertically { it } + fadeIn(),
        ) {
            HorizontalFloatingToolbar(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .height(IntrinsicSize.Min)
                    .wrapContentSize(align = Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    toolbarContentColor = MaterialTheme.colorScheme.onSecondaryContainer.darken(
                        0.1f
                    ),
                ),
                expanded = true,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)
                ) {
                    TextField(
                        modifier = Modifier
                            .graphicsLayer(clip = true)
                            .weight(1f),
                        value = value,
                        placeholder = stringResource(R.string.text_translation_placeholder),
                        onValueChange = {
                            onValueChange(it)
                        },
                        onSubmit = {
                            onSubmit(value)
                        },
                        onFocus = { setFocus(true) },
                        onBlur = { setFocus(false) },
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors = TextFieldDefaults.colors(),
                        shape = MaterialTheme.shapes.extraLarge,
                        maxLines = 8,
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = !focussed && value.isBlank(),
                                enter = slideInHorizontally { it } + fadeIn(),
                                exit = slideOutHorizontally { it } + fadeOut(),
                            ) {
                                IconButton(
                                    modifier = Modifier.size(MaterialTheme.spacing.extraLargeIncreased),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    onClick = {
                                        onNavigateVision()
                                    }
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.rounded_photo_camera_24),
                                        contentDescription = stringResource(R.string.translate)
                                    )
                                }
                            }
                        }
                    )

                    FilledIconButton(
                        modifier = Modifier
                            .padding(vertical = MaterialTheme.spacing.extraSmall)
                            .size(MaterialTheme.spacing.extraLargeIncreased),
                        onClick = {
                            onSubmit(value)
                        },
                        colors = FilledIconButtonDefaults.surfaceIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                0.4f
                            )
                        )
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.rounded_translate_24),
                            contentDescription = stringResource(R.string.translate)
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun TextTranslationPreview() {
    val languageViewModel = LanguageViewModel(
        context = LocalContext.current,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )

    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    val translationMockInference = TranslationMockInference()

    TextTranslation(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        textTranslationViewModel = TextTranslationViewModel(
            languageViewModel = languageViewModel,
            translationViewModel = TranslationViewModel(
                intermediateModel = translationMockInference,
                outputModel = translationMockInference,
                translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
                languageViewModel = languageViewModel
            )
        ),
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
            externalDataRepository = ExternalDataMemoryRepository(),
        ),
    )
}
