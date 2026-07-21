package app.versta.translate.ui.component

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.versta.translate.R
import app.versta.translate.core.model.CameraTranslationViewModel
import app.versta.translate.core.model.CustomThemeViewModel
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ObjectCharacterRecognitionViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.SpeechRecognitionViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.core.model.VoiceViewModel
import app.versta.translate.ui.screen.About
import app.versta.translate.ui.screen.ApplicationLogs
import app.versta.translate.ui.screen.LanguageAttributions
import app.versta.translate.ui.screen.LanguageDetails
import app.versta.translate.ui.screen.LanguageSettings
import app.versta.translate.ui.screen.PrivacyPolicy
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.screen.Settings
import app.versta.translate.ui.screen.TextToSpeechSettings
import app.versta.translate.ui.screen.TextTranslation
import app.versta.translate.ui.screen.TextTranslationLegacy
import app.versta.translate.ui.screen.ThirdParty
import app.versta.translate.ui.screen.TranslationSettings
import app.versta.translate.ui.screen.Troubleshooting
import app.versta.translate.ui.screen.CameraTranslation
import app.versta.translate.ui.screen.ObjectCharacterRecognitionAttributions
import app.versta.translate.ui.screen.ObjectCharacterRecognitionDetails
import app.versta.translate.ui.screen.ObjectCharacterRecognitionSettings
import app.versta.translate.ui.screen.SpeechRecognitionDetails
import app.versta.translate.ui.screen.SpeechRecognitionSettings
import app.versta.translate.ui.screen.VoiceAttributions
import app.versta.translate.ui.screen.VoiceDetails
import app.versta.translate.ui.screen.VoiceSettings
import app.versta.translate.utils.CustomThemeScene
import app.versta.translate.utils.rememberCustomThemeEntryDecorator

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun Router(
    scaffoldViewModel: ScaffoldViewModel,
    customThemeViewModel: CustomThemeViewModel,
    navigationViewModel: NavigationViewModel,
    cameraTranslationViewModel: CameraTranslationViewModel,
    languageViewModel: LanguageViewModel,
    licenseViewModel: LicenseViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel,
    voiceViewModel: VoiceViewModel,
    objectCharacterRecognitionViewModel: ObjectCharacterRecognitionViewModel,
    speechRecognitionViewModel: SpeechRecognitionViewModel,
    loggingViewModel: LoggingViewModel
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo = windowAdaptiveInfo).copy(
            horizontalPartitionSpacerSize = 0.dp
        )
    }
    val listDetailStrategy: ListDetailSceneStrategy<NavKey> = rememberListDetailSceneStrategy(
        backNavigationBehavior = BackNavigationBehavior.PopUntilContentChange,
        directive = directive
    )

    val textTranslationKey = remember { "TextTranslation" }
    val settingsKey = remember { "Settings" }

    return NavigationDrawer(
        modifier = Modifier.zIndex(1f),
        navigationViewModel = navigationViewModel,
        navigationItems = listOf(
            NavigationItem(
                label = "Text",
                route = Screens.TextTranslation,
                parent = Screens.TextTranslation, // Workaround to keep the animations the same
                icon = ImageVector.vectorResource(R.drawable.rounded_dictionary_24),
                selectedIcon = ImageVector.vectorResource(R.drawable.rounded_dictionary_24)
            ),
            NavigationItem(
                label = "Vision",
                route = Screens.Vision,
                parent = Screens.TextTranslation,
                icon = ImageVector.vectorResource(R.drawable.outline_camera_alt_24),
                selectedIcon = ImageVector.vectorResource(R.drawable.round_camera_alt_24)
            ),
            NavigationItem(
                label = "Converse",
                route = Screens.TextTranslationLegacy,
                parent = Screens.TextTranslation,
                icon = ImageVector.vectorResource(R.drawable.rounded_graphic_eq_24),
                selectedIcon = ImageVector.vectorResource(R.drawable.rounded_graphic_eq_24)
            ),
        ),
        footerNavigationItems = listOf(
            NavigationItem(
                label = "Settings",
                route = Screens.Settings,
                parent = Screens.TextTranslation,
                icon = ImageVector.vectorResource(R.drawable.round_settings_24),
                selectedIcon = ImageVector.vectorResource(R.drawable.round_settings_24)
            ),
        )
    ) {
        ScaffoldCompactBar(
            scaffoldViewModel = scaffoldViewModel,
            content = { innerPadding ->
                NavDisplay(
                    backStack = navigationViewModel.navigationBackStack,
                    modifier = Modifier,
                    onBack = { navigationViewModel.back() },
                    sceneStrategy = listDetailStrategy,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                        rememberCustomThemeEntryDecorator(
                            customThemeViewModel = customThemeViewModel
                        )
                    ),
                    entryProvider = entryProvider {
                        entry<Screens.TextTranslation>(
                            metadata = ListDetailSceneStrategy.listPane(
                                sceneKey = textTranslationKey,
                            )
                        ) {
                            TextTranslation(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                textToSpeechViewModel = textToSpeechViewModel,
                                textTranslationViewModel = textTranslationViewModel,
                                speechRecognitionViewModel = speechRecognitionViewModel,
                            )
                        }
                        entry<Screens.Vision>(
                            metadata = CustomThemeScene.obsidian()
                        ) {
                            CameraTranslation(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                cameraTranslationViewModel = cameraTranslationViewModel
                            )
                        }
                        entry<Screens.Settings>(
                            metadata = ListDetailSceneStrategy.listPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            Settings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                licenseViewModel = licenseViewModel
                            )
                        }
                        entry<Screens.LanguageSettings>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            LanguageSettings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                languageViewModel = languageViewModel
                            )
                        }
                        entry<Screens.LanguageDetails>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            LanguageDetails(
                                id = it.id,
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                languageViewModel = languageViewModel
                            )
                        }
                        entry<Screens.LanguageAttributions>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            LanguageAttributions(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                languageViewModel = languageViewModel
                            )
                        }
                        entry<Screens.TextTranslationLegacy> {
                            TextTranslationLegacy(
                                navigationViewModel = navigationViewModel,
                                languageViewModel = languageViewModel,
                                translationViewModel = translationViewModel,
                                textTranslationViewModel = textTranslationViewModel,
                                textToSpeechViewModel = textToSpeechViewModel
                            )
                        }
                        entry<Screens.TranslationSettings>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            TranslationSettings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                translationViewModel = translationViewModel,
                                languageViewModel = languageViewModel
                            )
                        }
                        entry<Screens.TextToSpeechSettings>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            TextToSpeechSettings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                textToSpeechViewModel = textToSpeechViewModel
                            )
                        }
                        entry<Screens.VoiceSettings>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            VoiceSettings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                voiceViewModel = voiceViewModel
                            )
                        }
                        entry<Screens.VoiceDetails>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            VoiceDetails(
                                id = it.id,
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                voiceViewModel = voiceViewModel
                            )
                        }
                        entry<Screens.VoiceAttributions>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            VoiceAttributions(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                voiceViewModel = voiceViewModel
                            )
                        }
                        entry<Screens.ObjectCharacterRecognitionSettings>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            ObjectCharacterRecognitionSettings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                objectCharacterRecognitionViewModel = objectCharacterRecognitionViewModel
                            )
                        }
                        entry<Screens.ObjectCharacterRecognitionDetails>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            ObjectCharacterRecognitionDetails(
                                id = it.id,
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                objectCharacterRecognitionViewModel = objectCharacterRecognitionViewModel
                            )
                        }
                        entry<Screens.SpeechRecognitionSettings>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            SpeechRecognitionSettings(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                speechRecognitionViewModel = speechRecognitionViewModel
                            )
                        }
                        entry<Screens.SpeechRecognitionDetails>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            SpeechRecognitionDetails(
                                id = it.id,
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                speechRecognitionViewModel = speechRecognitionViewModel
                            )
                        }
                        entry<Screens.ObjectCharacterRecognitionAttributions>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            ObjectCharacterRecognitionAttributions(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                objectCharacterRecognitionViewModel = objectCharacterRecognitionViewModel
                            )
                        }
                        entry<Screens.About>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            About(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                licenseViewModel = licenseViewModel
                            )
                        }
                        entry<Screens.ThirdParty>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            ThirdParty(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel
                            )
                        }
                        entry<Screens.PrivacyPolicy>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            PrivacyPolicy(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel
                            )
                        }
                        entry<Screens.Troubleshooting>(
                            metadata = ListDetailSceneStrategy.detailPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            Troubleshooting(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                licenseViewModel = licenseViewModel
                            )
                        }
                        entry<Screens.ApplicationLogs>(
                            metadata = ListDetailSceneStrategy.extraPane(
                                sceneKey = settingsKey
                            )
                        ) {
                            ApplicationLogs(
                                innerPadding = innerPadding,
                                scaffoldViewModel = scaffoldViewModel,
                                navigationViewModel = navigationViewModel,
                                loggingViewModel = loggingViewModel
                            )
                        }
                    },
                    transitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { it },
                        ) + fadeIn() togetherWith slideOutHorizontally(
                            targetOffsetX = { -it },
                        ) + fadeOut()
                    },
                    popTransitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                        ) + fadeIn() togetherWith slideOutHorizontally(
                            targetOffsetX = { it },
                        ) + fadeOut()
                    },
                    predictivePopTransitionSpec = {
                        slideInHorizontally(
                            initialOffsetX = { -it },
                        ) + fadeIn() togetherWith slideOutHorizontally(
                            targetOffsetX = { it },
                        ) + fadeOut()
                    })
            })
    }
}