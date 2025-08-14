package app.versta.translate.core.model

import androidx.compose.material3.ColorScheme
import androidx.lifecycle.ViewModel
import app.versta.translate.utils.CustomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.collections.get

class CustomThemeViewModel(
    private val customThemeEntries: Map<CustomTheme, ColorScheme>
): ViewModel() {
    private val _currentTheme =
        MutableStateFlow<CustomTheme?>(null)
    private val _colorScheme = MutableStateFlow<ColorScheme?>(null)
    val colorScheme = _colorScheme.asStateFlow()

    fun setTheme(theme: CustomTheme?) {
        _currentTheme.value = theme
        _colorScheme.value = customThemeEntries[_currentTheme.value]
    }
}