package app.versta.translate.ui.component

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.NavHost
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.versta.translate.ui.screen.Camera
import app.versta.translate.ui.screen.Home
import app.versta.translate.ui.screen.LanguageImport
import app.versta.translate.ui.screen.LanguageSettings
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.screen.Settings
import app.versta.translate.ui.screen.StatusBarStyle
import app.versta.translate.ui.screen.TextTranslation

@Composable
fun Router() {
    val navController = rememberNavController()

    val view = LocalView.current
    val activity = view.context as androidx.activity.ComponentActivity
    val window = activity.window

    DisposableEffect(Unit) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            val statusBarStyle = destination.route?.let { route ->
                Screens.valueOf(route).statusBarStyle
            } ?: StatusBarStyle.Dark

            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.isAppearanceLightStatusBars = statusBarStyle == StatusBarStyle.Dark
            windowInsetsController.isAppearanceLightNavigationBars = statusBarStyle == StatusBarStyle.Dark
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
            Home(navController)
        }
        composable(Screens.Camera()) {
            Camera()
        }
        composable(Screens.Settings()) {
            Settings(navController)
        }
        composable(Screens.LanguageSettings()) {
            LanguageSettings(navController)
        }
        composable(Screens.LanguageImport()) {
            LanguageImport(navController)
        }
        composable(Screens.TextTranslation()) {
            TextTranslation(navController)
        }
    }
}