package app.versta.translate.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.versta.translate.MainApplication
import app.versta.translate.core.model.LanguageImportViewModel
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.TextRecognitionViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.screen.CameraTranslation
import app.versta.translate.ui.screen.Home
import app.versta.translate.ui.screen.LanguageImport
import app.versta.translate.ui.screen.LanguageSettings
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.screen.Settings
import app.versta.translate.ui.screen.StatusBarStyle
import app.versta.translate.ui.screen.TextTranslation
import app.versta.translate.ui.screen.TranslationSettings

@Composable
fun Router(
    languageViewModel: LanguageViewModel,
    languageImportViewModel: LanguageImportViewModel,
    licenseViewModel: LicenseViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textRecognitionViewModel: TextRecognitionViewModel,
    translationViewModel: TranslationViewModel,
) {
    val navController = rememberNavController()

    val view = LocalView.current
    val activity = view.context as ComponentActivity
    val window = activity.window

    DisposableEffect(Unit) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val statusBarStyle = destination.route?.let { route ->
                Screens.valueOf(route).statusBarStyle
            } ?: StatusBarStyle.Dark

            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars =
                statusBarStyle == StatusBarStyle.Dark
            windowInsetsController.isAppearanceLightNavigationBars =
                statusBarStyle == StatusBarStyle.Dark
        }

        navController.addOnDestinationChangedListener(listener)

        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    return NavHost(
        navController = navController,
        startDestination = Screens.Home(),
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Screens.Home()) {
            Home(
                navController = navController,
                textTranslationViewModel = textTranslationViewModel,
                licenseViewModel = licenseViewModel,
                languageViewModel = languageViewModel
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
    }
}