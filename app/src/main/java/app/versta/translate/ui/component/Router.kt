package app.versta.translate.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.versta.translate.core.model.LanguageImportViewModel
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.TextRecognitionViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.screen.About
import app.versta.translate.ui.screen.ThirdParty
import app.versta.translate.ui.screen.CameraTranslation
import app.versta.translate.ui.screen.Home
import app.versta.translate.ui.screen.LanguageAttributions
import app.versta.translate.ui.screen.LanguageImport
import app.versta.translate.ui.screen.LanguageSettings
import app.versta.translate.ui.screen.PrivacyPolicy
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.screen.Settings
import app.versta.translate.ui.screen.StatusBarStyle
import app.versta.translate.ui.screen.TextTranslation
import app.versta.translate.ui.screen.TranslationSettings

@Composable
fun Router(
    startDestination: String? = null,
    languageViewModel: LanguageViewModel,
    languageImportViewModel: LanguageImportViewModel,
    licenseViewModel: LicenseViewModel,
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textRecognitionViewModel: TextRecognitionViewModel,
) {
    val navController = rememberNavController()

    val view = LocalView.current
    val activity = remember { view.context as ComponentActivity }
    val window = remember { activity.window }
    val darkTheme = isSystemInDarkTheme()

    LaunchedEffect(startDestination) {
        startDestination?.let {
            navController.navigate(it) {
                popUpTo(navController.graph.startDestinationId) {
                    inclusive = true
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val statusBarColor = if (darkTheme) StatusBarStyle.Dark else StatusBarStyle.Light
            val statusBarStyle = destination.route?.let { route ->
                Screens.valueOf(route).statusBarStyle
            } ?: statusBarColor

            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars =
                statusBarStyle == statusBarColor
            windowInsetsController.isAppearanceLightNavigationBars =
                statusBarStyle == statusBarColor
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    return NavHost(
        navController = navController,
        startDestination = startDestination ?: Screens.Home(),
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screens.Home()) {
            Home(
                navController = navController,
                languageViewModel = languageViewModel,
                licenseViewModel = licenseViewModel,
                textTranslationViewModel = textTranslationViewModel
            )
        }
        composable(Screens.Camera()) {
            CameraTranslation(
                navController = navController,
                textRecognitionViewModel = textRecognitionViewModel,
                translationViewModel = translationViewModel
            )
        }
        composable(Screens.Settings()) {
            Settings(
                navController = navController,
                licenseViewModel = licenseViewModel,
            )
        }
        composable(Screens.LanguageSettings()) {
            LanguageSettings(
                navController = navController,
                languageViewModel = languageViewModel
            )
        }
        composable(Screens.LanguageImport()) {
            LanguageImport(
                navController = navController,
                languageImportViewModel = languageImportViewModel,
            )
        }
        composable(Screens.TextTranslation()) {
            TextTranslation(
                navController = navController,
                languageViewModel = languageViewModel,
                translationViewModel = translationViewModel,
                textTranslationViewModel = textTranslationViewModel
            )
        }
        composable(Screens.TranslationSettings()) {
            TranslationSettings(
                navController = navController,
                translationViewModel = translationViewModel
            )
        }
        composable(Screens.About()) {
            About(
                navController = navController,
                licenseViewModel = licenseViewModel
            )
        }
        composable(Screens.LanguageAttributions()) {
            LanguageAttributions(
                navController = navController,
                languageViewModel = languageViewModel
            )
        }
        composable(Screens.ThirdParty()) {
            ThirdParty(
                navController = navController
            )
        }
        composable(Screens.PrivacyPolicy()) {
            PrivacyPolicy(
                navController = navController
            )
        }
    }
}