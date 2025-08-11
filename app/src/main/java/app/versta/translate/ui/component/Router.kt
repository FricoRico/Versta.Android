package app.versta.translate.ui.component

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.core.model.VoiceViewModel
import app.versta.translate.ui.screen.About
import app.versta.translate.ui.screen.ApplicationLogs
import app.versta.translate.ui.screen.Home
import app.versta.translate.ui.screen.LanguageAttributions
import app.versta.translate.ui.screen.LanguageDetails
import app.versta.translate.ui.screen.LanguageSettings
import app.versta.translate.ui.screen.PrivacyPolicy
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.screen.Settings
import app.versta.translate.ui.screen.TextToSpeechSettings
import app.versta.translate.ui.screen.TextTranslation
import app.versta.translate.ui.screen.ThirdParty
import app.versta.translate.ui.screen.TranslationSettings
import app.versta.translate.ui.screen.Troubleshooting
import app.versta.translate.ui.screen.VoiceAttributions
import app.versta.translate.ui.screen.VoiceDetails
import app.versta.translate.ui.screen.VoicesSettings

@Composable
fun Router(
    startDestination: Screens? = null,
    animationDurationMillis: Int = 300,
    languageViewModel: LanguageViewModel,
    licenseViewModel: LicenseViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel,
    voiceViewModel: VoiceViewModel,
    loggingViewModel: LoggingViewModel
) {
    val backStack = rememberNavBackStack(startDestination ?: Screens.Home)

    return NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize(),
        entryProvider = entryProvider {
            entry<Screens.Home> {
                Home(
                    backStack = backStack,
                    languageViewModel = languageViewModel,
                    licenseViewModel = licenseViewModel,
                    textTranslationViewModel = textTranslationViewModel
                )
            }
            entry<Screens.Settings> {
                Settings(
                    backStack = backStack,
                    licenseViewModel = licenseViewModel
                )
            }
            entry<Screens.LanguageSettings> {
                LanguageSettings(
                    backStack = backStack,
                    languageViewModel = languageViewModel
                )
            }
            entry<Screens.LanguageDetails> {
                LanguageDetails(
                    id = it.id,
                    backStack = backStack,
                    languageViewModel = languageViewModel
                )
            }
            entry<Screens.LanguageAttributions> {
                LanguageAttributions(
                    backStack = backStack,
                    languageViewModel = languageViewModel
                )
            }
            entry<Screens.TextTranslation> {
                TextTranslation(
                    backStack = backStack,
                    languageViewModel = languageViewModel,
                    translationViewModel = translationViewModel,
                    textTranslationViewModel = textTranslationViewModel,
                    textToSpeechViewModel = textToSpeechViewModel
                )
            }
            entry<Screens.TranslationSettings> {
                TranslationSettings(
                    backStack = backStack,
                    translationViewModel = translationViewModel,
                    languageViewModel = languageViewModel
                )
            }
            entry<Screens.TextToSpeechSettings> {
                TextToSpeechSettings(
                    backStack = backStack,
                    textToSpeechViewModel = textToSpeechViewModel
                )
            }
            entry<Screens.VoicesSettings> {
                VoicesSettings(
                    backStack = backStack,
                    voiceViewModel = voiceViewModel
                )
            }
            entry<Screens.VoiceDetails> {
                VoiceDetails(
                    id = it.id,
                    backStack = backStack,
                    voiceViewModel = voiceViewModel
                )
            }
            entry<Screens.VoiceAttributions> {
                VoiceAttributions(
                    backStack = backStack,
                    voiceViewModel = voiceViewModel
                )
            }
            entry<Screens.About> {
                About(
                    backStack = backStack,
                    licenseViewModel = licenseViewModel
                )
            }
            entry<Screens.ThirdParty> {
                ThirdParty(
                    backStack = backStack
                )
            }
            entry<Screens.PrivacyPolicy> {
                PrivacyPolicy(
                    backStack = backStack
                )
            }
            entry<Screens.Troubleshooting> {
                Troubleshooting(
                    backStack = backStack,
                    licenseViewModel = licenseViewModel
                )
            }
            entry<Screens.ApplicationLogs> {
                ApplicationLogs(
                    backStack = backStack,
                    loggingViewModel = loggingViewModel
                )
            }
        }, transitionSpec = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = animationDurationMillis)
            ) + fadeIn(animationSpec = tween(durationMillis = animationDurationMillis)) togetherWith slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(durationMillis = animationDurationMillis)
            ) + fadeOut(animationSpec = tween(durationMillis = animationDurationMillis))
        }, popTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = animationDurationMillis)
            ) + fadeIn(animationSpec = tween(durationMillis = animationDurationMillis)) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = animationDurationMillis)
            ) + fadeOut(animationSpec = tween(durationMillis = animationDurationMillis))
        }, predictivePopTransitionSpec = {
            slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(durationMillis = animationDurationMillis)
            ) + fadeIn(animationSpec = tween(durationMillis = animationDurationMillis)) togetherWith slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = animationDurationMillis)
            ) + fadeOut(animationSpec = tween(durationMillis = animationDurationMillis))
        }
    )
}