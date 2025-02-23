package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.entity.Language
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class LanguageType {
    Source,
    Target
}

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModel(
    private val languageRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState

    val sourceLanguage = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()
    val targetLanguage = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()

    val canSwapLanguages = sourceLanguage.combine(targetLanguage) { source, target ->
        source != null && target != null
    }

    val availableLanguages = languageRepository.getLanguages().distinctUntilChanged()
    val availableLanguagePairs = languageRepository.getLanguagePairs().distinctUntilChanged()

    val sourceLanguages = languageRepository.getSourceLanguages().distinctUntilChanged()
    val targetLanguages = sourceLanguage
        .flatMapLatest {
            if (it != null) {
                languageRepository.getTargetLanguagesBySource(it)
            } else {
                flowOf(emptyList())
            }
        }

    /**
     * Sets the language selection state.
     */
    fun setLanguageSelectionState(state: LanguageType?) {
        _languageSelectionState.value = state
    }

    /**
     * Sets the source language.
     */
    fun setSourceLanguage(language: Language): Job {
        return viewModelScope.launch {
            val current = sourceLanguage.first()
            languagePreferenceRepository.setSourceLanguage(language)

            // If there is a target language available for the current source language, set it instead
            // of clearing the target language.
            if (current != null && targetLanguages.first().contains(current)) {
                languagePreferenceRepository.setTargetLanguage(current)
                return@launch
            }

            // If the current target language is not available for the new source language, clear the
            // current target language.
            languageRepository.getTargetLanguagesBySource(language).collectLatest { languages ->
                if (languages.none { it == targetLanguage.first() }) {
                    clearTargetLanguage()
                }
            }
        }
    }

    /**
     * Sets the target language.
     */
    fun setTargetLanguage(language: Language): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.setTargetLanguage(language)
        }
    }

    /**
     * Swaps the source and target languages.
     */
    fun swapLanguages(): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.swapLanguages()
        }
    }

    /**
     * Clears the target language.
     */
    private fun clearTargetLanguage(): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.clearTargetLanguage()
        }
    }


    fun deleteBySource(language: Language) {
        viewModelScope.launch {
            languageRepository.deleteLanguageModelsBySourceLanguage(language).forEach {
                languagePreferenceRepository.clearLanguageSelectionForPair(it)
            }
        }
    }
}