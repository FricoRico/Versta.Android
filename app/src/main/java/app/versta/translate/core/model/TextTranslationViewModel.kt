package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class TextTranslationViewModel : ViewModel() {
    private val _sourceText = MutableStateFlow("")
    val sourceText = _sourceText

    private val _translatedText = MutableStateFlow("")
    val targetText = _translatedText

    fun setSourceText(text: String) {
        _sourceText.value = text
    }

    fun setTargetText(text: String) {
        _translatedText.value = text
    }
}