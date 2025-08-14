package app.versta.translate.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

data class Easing(
    val emphasized: Int = 500,
    val emphasizedDecelerate: Int = 400,
    val emphasizedAccelerate: Int = 200,
    val standard: Int = 300,
    val standardDecelerate: Int = 250,
    val standardAccelerate: Int = 200,
)

val LocalEasing = staticCompositionLocalOf { Easing() }

val MaterialTheme.easing: Easing
    @Composable get() = LocalEasing.current
