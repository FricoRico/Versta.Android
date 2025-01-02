package app.versta.translate.core.model

import android.icu.text.Transliterator
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.RomanizationAdapter
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TextTranslationViewModel(
    private val languagePreferenceRepository: LanguagePreferenceRepository,
) : ViewModel() {
    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    private val _sourceText = MutableStateFlow("")
    val sourceText = _sourceText

    private val _sourceTextRomanizated = MutableStateFlow("")
    val sourceTextRomanizated = _sourceTextRomanizated

    private val _translatedText = MutableStateFlow("")
    val targetText = _translatedText

    private val _translatedTextRomanizated = MutableStateFlow("")
    val targetTextRomanizated = _translatedTextRomanizated

    private val languages = languagePreferenceRepository.getLanguagePair()

    private var sourceTransliterator: RomanizationAdapter? = null
    private var targetTransliterator: RomanizationAdapter? = null

    fun setSourceText(text: String) {
        _sourceText.value = text
        _sourceTextRomanizated.value = sourceTransliterator?.transliterate(text) ?: ""
    }

    fun clearSourceText() {
        _sourceText.value = ""
        _sourceTextRomanizated.value = ""
    }

    fun setTargetText(text: String) {
        _translatedText.value = text
        _translatedTextRomanizated.value = targetTransliterator?.transliterate(text) ?: ""
    }

    fun clearTargetText() {
        _translatedText.value = ""
        _translatedTextRomanizated.value = ""
    }

    fun load(languages: LanguagePair) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = LoadingProgress.InProgress

            try {
                sourceTransliterator = RomanizationAdapter(locale = languages.source.locale)
                targetTransliterator = RomanizationAdapter(locale = languages.target.locale)

                _loadingProgress.value = LoadingProgress.Completed
            } catch (e: Exception) {
                e.printStackTrace()
                _loadingProgress.value = LoadingProgress.Error(e)
            }
        }
    }

    init {
        viewModelScope.launch {
            languages.collect {
                if (it != null) {
                    load(it)
                }
            }
        }
    }
}