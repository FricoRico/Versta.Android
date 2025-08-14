package app.versta.translate.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.core.model.CustomThemeViewModel
import app.versta.translate.utils.lightness

enum class StatusBarStyle {
    Dark, Light
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TranslateTheme(
    customThemeViewModel: CustomThemeViewModel,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val context = LocalContext.current

    val customColorScheme by customThemeViewModel.colorScheme.collectAsStateWithLifecycle()
    val colorScheme =
        customColorScheme ?: remember {
            when {
                darkTheme -> VerstaDarkDynamicColorScheme(context, dynamicColor, windowAdaptiveInfo)
                else -> VerstaLightDynamicColorScheme(context, dynamicColor, windowAdaptiveInfo)
            }
        }

    val animatedColorScheme = animateColorScheme(colorScheme)

    val spacing = Spacing()
    val easing = Easing()

    SetStatusBarColorForColorScheme(colorScheme, darkTheme)
    CompositionLocalProvider(
        LocalSpacing provides spacing,
        LocalEasing provides easing
    ) {
        MaterialExpressiveTheme(
            motionScheme = MotionScheme.expressive(),
            colorScheme = animatedColorScheme,
            typography = Typography,
            content = content,
        )
    }
}

@Composable
private fun SetStatusBarColorForColorScheme(
    colorScheme: ColorScheme,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val view = LocalView.current
    val context = LocalContext.current

    val activity = remember { context as ComponentActivity }
    val window = remember { activity.window }

    LaunchedEffect(colorScheme, darkTheme) {
        val themeStatusBarStyle = statusBarStyleForSystemTheme(darkTheme)
        val colorSchemeStatusBarStyle = statusBarStyleForColorScheme(colorScheme)
        val isLightStatusBar = isLAppearanceLight(themeStatusBarStyle, colorSchemeStatusBarStyle)

        val windowInsetsController = WindowCompat.getInsetsController(window, view)
        windowInsetsController.isAppearanceLightStatusBars = isLightStatusBar
        windowInsetsController.isAppearanceLightNavigationBars = isLightStatusBar
    }
}

private fun isLAppearanceLight(
    themeStatusBarStyle: StatusBarStyle,
    colorStatusBarStyle: StatusBarStyle
): Boolean {
    return themeStatusBarStyle == StatusBarStyle.Light && colorStatusBarStyle == StatusBarStyle.Light
}

private fun statusBarStyleForSystemTheme(darkTheme: Boolean): StatusBarStyle {
    return if (darkTheme) StatusBarStyle.Dark else StatusBarStyle.Light
}

private fun statusBarStyleForColorScheme(colorScheme: ColorScheme): StatusBarStyle {
    val darkTheme = colorScheme.contentColorFor(colorScheme.background).lightness() > 0.5

    return if (darkTheme) StatusBarStyle.Dark else StatusBarStyle.Light
}

private fun defaultColorTransitionSpec(): FiniteAnimationSpec<Color> = tween(durationMillis = 200)

@Composable
private fun animateColorScheme(colorScheme: ColorScheme): ColorScheme {
    val transition = updateTransition(colorScheme, label = "themeTransition")

    val primaryColors = animatedPrimaryColors(transition)
    val backgroundColors = animatedBackgroundColors(transition)
    val surfaceColors = animatedSurfaceColors(transition)

    return colorScheme.copy(
        primary = primaryColors.primary,
        onPrimary = primaryColors.onPrimary,
        primaryContainer = primaryColors.primaryContainer,
        onPrimaryContainer = primaryColors.onPrimaryContainer,
        inversePrimary = primaryColors.inversePrimary,
        secondary = primaryColors.secondary,
        onSecondary = primaryColors.onSecondary,
        secondaryContainer = primaryColors.secondaryContainer,
        onSecondaryContainer = primaryColors.onSecondaryContainer,
        tertiary = primaryColors.tertiary,
        onTertiary = primaryColors.onTertiary,
        tertiaryContainer = primaryColors.tertiaryContainer,
        onTertiaryContainer = primaryColors.onTertiaryContainer,
        background = backgroundColors.background,
        onBackground = backgroundColors.onBackground,
        surface = surfaceColors.surface,
        onSurface = surfaceColors.onSurface,
        surfaceVariant = surfaceColors.surfaceVariant,
        onSurfaceVariant = surfaceColors.onSurfaceVariant,
        surfaceTint = surfaceColors.surfaceTint,
        inverseSurface = surfaceColors.inverseSurface,
        inverseOnSurface = surfaceColors.inverseOnSurface,
        surfaceBright = surfaceColors.surfaceBright,
        surfaceDim = surfaceColors.surfaceDim,
        surfaceContainer = surfaceColors.surfaceContainer,
        surfaceContainerHigh = surfaceColors.surfaceContainerHigh,
        surfaceContainerHighest = surfaceColors.surfaceContainerHighest,
        surfaceContainerLow = surfaceColors.surfaceContainerLow,
        surfaceContainerLowest = surfaceColors.surfaceContainerLowest,
    )
}

internal data class AnimatedColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

@Composable
private fun animatedPrimaryColors(transition: Transition<ColorScheme>): AnimatedColors {
    val primary by transition.animateColor(
        label = "primary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.primary }
    val onPrimary by transition.animateColor(
        label = "onPrimary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onPrimary }
    val primaryContainer by transition.animateColor(
        label = "primaryContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.primaryContainer }
    val onPrimaryContainer by transition.animateColor(
        label = "onPrimaryContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onPrimaryContainer }
    val inversePrimary by transition.animateColor(
        label = "inversePrimary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.inversePrimary }
    val secondary by transition.animateColor(
        label = "secondary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.secondary }
    val onSecondary by transition.animateColor(
        label = "onSecondary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onSecondary }
    val secondaryContainer by transition.animateColor(
        label = "secondaryContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.secondaryContainer }
    val onSecondaryContainer by transition.animateColor(
        label = "onSecondaryContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onSecondaryContainer }
    val tertiary by transition.animateColor(
        label = "tertiary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.tertiary }
    val onTertiary by transition.animateColor(
        label = "onTertiary",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onTertiary }
    val tertiaryContainer by transition.animateColor(
        label = "tertiaryContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.tertiaryContainer }
    val onTertiaryContainer by transition.animateColor(
        label = "onTertiaryContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onTertiaryContainer }

    return AnimatedColors(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
    )
}

internal data class AnimatedBackgroundColors(
    val background: Color,
    val onBackground: Color,
)

@Composable
private fun animatedBackgroundColors(transition: Transition<ColorScheme>): AnimatedBackgroundColors {
    val background by transition.animateColor(
        label = "background",
        transitionSpec = { defaultColorTransitionSpec() }) { it.background }
    val onBackground by transition.animateColor(
        label = "onBackground",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onBackground }

    return AnimatedBackgroundColors(
        background = background,
        onBackground = onBackground,
    )
}

internal data class AnimatedSurfaceColors(
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceTint: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val surfaceBright: Color,
    val surfaceDim: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainerLowest: Color,
)

@Composable
private fun animatedSurfaceColors(transition: Transition<ColorScheme>): AnimatedSurfaceColors {
    val surface by transition.animateColor(
        label = "surface",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surface }
    val onSurface by transition.animateColor(
        label = "onSurface",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onSurface }
    val surfaceVariant by transition.animateColor(
        label = "surfaceVariant",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceVariant }
    val onSurfaceVariant by transition.animateColor(
        label = "onSurfaceVariant",
        transitionSpec = { defaultColorTransitionSpec() }) { it.onSurfaceVariant }
    val surfaceTint by transition.animateColor(
        label = "surfaceTint",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceTint }
    val inverseSurface by transition.animateColor(
        label = "inverseSurface",
        transitionSpec = { defaultColorTransitionSpec() }) { it.inverseSurface }
    val inverseOnSurface by transition.animateColor(
        label = "inverseOnSurface",
        transitionSpec = { defaultColorTransitionSpec() }) { it.inverseOnSurface }
    val surfaceBright by transition.animateColor(
        label = "surfaceBright",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceBright }
    val surfaceDim by transition.animateColor(
        label = "surfaceDim",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceDim }
    val surfaceContainer by transition.animateColor(
        label = "surfaceContainer",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceContainer }
    val surfaceContainerHigh by transition.animateColor(
        label = "surfaceContainerHigh",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceContainerHigh }
    val surfaceContainerHighest by transition.animateColor(
        label = "surfaceContainerHighest",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceContainerHighest }
    val surfaceContainerLow by transition.animateColor(
        label = "surfaceContainerLow",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceContainerLow }
    val surfaceContainerLowest by transition.animateColor(
        label = "surfaceContainerLowest",
        transitionSpec = { defaultColorTransitionSpec() }) { it.surfaceContainerLowest }

    return AnimatedSurfaceColors(
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
    )
}
