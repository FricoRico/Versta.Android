package app.versta.translate.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import app.versta.translate.utils.isWide

val primaryLight = Color(0xFF00629F)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFF15A1FF)
val onPrimaryContainerLight = Color(0xFF00355A)
val secondaryLight = Color(0xFF236659)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFF3F8071)
val onSecondaryContainerLight = Color(0xFFF4FFFA)
val tertiaryLight = Color(0xFF8D445E)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFAB5C76)
val onTertiaryContainerLight = Color(0xFFFFFBFF)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF8F9FF)
val onBackgroundLight = Color(0xFF171C22)
val surfaceLight = Color(0xFFFAF9FB)
val onSurfaceLight = Color(0xFF1A1C1D)
val surfaceVariantLight = Color(0xFFDDE3ED)
val onSurfaceVariantLight = Color(0xFF414750)
val outlineLight = Color(0xFF717881)
val outlineVariantLight = Color(0xFFC1C7D1)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2F3032)
val inverseOnSurfaceLight = Color(0xFFF2F0F2)
val inversePrimaryLight = Color(0xFF9BCBFF)
val surfaceDimLight = Color(0xFFDBD9DC)
val surfaceBrightLight = Color(0xFFFAF9FB)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF4F3F5)
val surfaceContainerLight = Color(0xFFEFEDF0)
val surfaceContainerHighLight = Color(0xFFE9E8EA)
val surfaceContainerHighestLight = Color(0xFFE3E2E4)

val primaryDark = Color(0xFF9BCBFF)
val onPrimaryDark = Color(0xFF003256)
val primaryContainerDark = Color(0xFF15A1FF)
val onPrimaryContainerDark = Color(0xFF00355A)
val secondaryDark = Color(0xFF92D3C3)
val onSecondaryDark = Color(0xFF00382E)
val secondaryContainerDark = Color(0xFF5C9C8D)
val onSecondaryContainerDark = Color(0xFF003028)
val tertiaryDark = Color(0xFFFFB1C8)
val onTertiaryDark = Color(0xFF581932)
val tertiaryContainerDark = Color(0xFFCB7792)
val onTertiaryContainerDark = Color(0xFF4F122B)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF0F1419)
val onBackgroundDark = Color(0xFFDFE3EB)
val surfaceDark = Color(0xFF121315)
val onSurfaceDark = Color(0xFFE3E2E4)
val surfaceVariantDark = Color(0xFF414750)
val onSurfaceVariantDark = Color(0xFFC1C7D1)
val outlineDark = Color(0xFF8B919B)
val outlineVariantDark = Color(0xFF414750)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE3E2E4)
val inverseOnSurfaceDark = Color(0xFF2F3032)
val inversePrimaryDark = Color(0xFF00629F)
val surfaceDimDark = Color(0xFF121315)
val surfaceBrightDark = Color(0xFF38393B)
val surfaceContainerLowestDark = Color(0xFF0D0E10)
val surfaceContainerLowDark = Color(0xFF1A1C1D)
val surfaceContainerDark = Color(0xFF1F2021)
val surfaceContainerHighDark = Color(0xFF292A2C)
val surfaceContainerHighestDark = Color(0xFF343537)

val VerstaLightColorScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

val VerstaLightDynamicColorScheme: (Context, Boolean, WindowAdaptiveInfo) -> ColorScheme =
    { context, dynamicColor, windowAdaptiveInfo ->
        when {
            !dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                VerstaLightColorScheme
            }

            !windowAdaptiveInfo.isWide() -> {
                dynamicLightColorScheme(context)
            }

            else -> {
                val dynamicColors = dynamicLightColorScheme(context)

                dynamicColors.copy(
                    background = dynamicColors.surfaceContainerLowest,
                    surfaceContainerLowest = dynamicColors.background
                )
            }
        }
    }

val VerstaDarkColorScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

val VerstaDarkDynamicColorScheme: (Context, Boolean, WindowAdaptiveInfo) -> ColorScheme =
    { context, dynamicColor, windowAdaptiveInfo ->
        when {
            !dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
                VerstaDarkColorScheme
            }

            !windowAdaptiveInfo.isWide() -> {
                dynamicDarkColorScheme(context)
            }

            else -> {
                val dynamicColors = dynamicDarkColorScheme(context)

                dynamicColors.copy(
                    background = dynamicColors.surfaceContainerLowest,
                    surfaceContainerLowest = dynamicColors.background
                )
            }
        }
    }