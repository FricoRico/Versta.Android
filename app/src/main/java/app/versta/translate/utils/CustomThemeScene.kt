package app.versta.translate.utils

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.navEntryDecorator
import app.versta.translate.core.model.CustomThemeViewModel
import app.versta.translate.utils.CustomThemeScene.Companion.CustomThemeKey

enum class CustomTheme {
    Obsidian,
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class CustomThemeScene {
    internal sealed interface ThemeMetadata {
        val theme: CustomTheme
    }

    internal class ObsidianThemeMetadata(override val theme: CustomTheme) : ThemeMetadata

    companion object {
        internal val CustomThemeKey: String = CustomTheme::class.qualifiedName!!

        fun obsidian(): Map<String, Any> = mapOf(
            CustomThemeKey to ObsidianThemeMetadata(
                CustomTheme.Obsidian
            )
        )
    }
}

@Composable
fun rememberCustomThemeEntryDecorator(
    customThemeViewModel: CustomThemeViewModel,
): NavEntryDecorator<NavKey> =
    remember { customThemeEntryDecorator(customThemeViewModel) }

internal fun customThemeEntryDecorator(
    customThemeViewModel: CustomThemeViewModel,
): NavEntryDecorator<NavKey> {
    return navEntryDecorator { entry ->
        val theme = (entry.metadata[CustomThemeKey] as CustomThemeScene.ThemeMetadata?)?.theme

        customThemeViewModel.setTheme(theme)

        entry.Content()
    }
}
