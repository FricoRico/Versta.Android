package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.LanguageOptionPair
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

class LanguagePreferenceMemoryRepository : LanguagePreferenceRepository {
    private var _pivotTranslation: Boolean = DEFAULT_PIVOT_TRANSLATION

    private var _sourceLanguage: LanguageOption? = Language(
        locale = Locale.ENGLISH
    )
    private var _targetLanguage: Language? = Language(
        locale = Locale.JAPANESE
    )

    /**
     * Gets the source language from the repository.
     */
    override fun getSourceLanguage(): Flow<LanguageOption?> {
        return flowOf(_sourceLanguage)
    }

    /**
     * Sets the source language in the repository.
     * @param language The language to set.
     */
    override suspend fun setSourceLanguage(language: LanguageOption) {
        _sourceLanguage = language
    }

    /**
     * Gets the target language from the repository.
     */
    override fun getTargetLanguage(): Flow<Language?> {
        return flowOf(_targetLanguage)
    }

    /**
     * Sets the target language in the repository.
     * @param language The language to set.
     */
    override suspend fun setTargetLanguage(language: Language) {
        _targetLanguage = language
    }

    /**
     * Gets the pivot translation option from the data store.
     */
    override fun getPivotTranslation(): Flow<Boolean> {
        return flowOf(_pivotTranslation)
    }

    /**
     * Sets the pivot translation option in the data store.
     */
    override suspend fun setPivotTranslation(enabled: Boolean) {
        _pivotTranslation = enabled
    }

    /**
     * Gets the language pair from the repository.
     */
    override fun getLanguagePair(): Flow<LanguageOptionPair?> {
        if (_sourceLanguage != null && _targetLanguage != null) {
            return flowOf(
                LanguageOptionPair(
                    source = _sourceLanguage!!,
                    target = _targetLanguage!!
                )
            )
        }

        return flowOf(null)
    }

    /**
     * Swaps the source and target languages in the repository.
     */
    override suspend fun swapLanguages() {
        val source = _sourceLanguage
        val target = _targetLanguage

        if (source !is Language) {
            return
        }

        _sourceLanguage = target
        _targetLanguage = source
    }

    /**
     * Clears the source language in the repository.
     */
    override suspend fun clearSourceLanguage() {
        _sourceLanguage = null
    }

    /**
     * Clears the target language in the repository.
     */
    override suspend fun clearTargetLanguage() {
        _targetLanguage = null
    }

    /**
     * Clears the language selection if the language pair is the same as the current one.
     */
    override suspend fun clearLanguageSelectionForPair(languagePair: LanguagePair) {
        if (_sourceLanguage == languagePair.source || _targetLanguage == languagePair.target || _sourceLanguage == languagePair.source) {
            _sourceLanguage = null
            _targetLanguage = null
        }
    }
}