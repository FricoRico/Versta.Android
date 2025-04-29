package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.LanguageOptionPair
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.flow.Flow

internal const val DEFAULT_PIVOT_TRANSLATION = true

interface LanguagePreferenceRepository {
    /**
     * Gets the source language from the data store.
     */
    fun getSourceLanguage(): Flow<LanguageOption?>

    /**
     * Sets the source language in the data store.
     * @param language The language to set.
     */
    suspend fun setSourceLanguage(language: LanguageOption)

    /**
     * Gets the target language from the data store.
     */
    fun getTargetLanguage(): Flow<Language?>

    /**
     * Sets the target language in the data store.
     * @param language The language to set.
     */
    suspend fun setTargetLanguage(language: Language)

    /**
     * Gets the pivot translation option from the data store.
     */
    fun getPivotTranslation(): Flow<Boolean>

    /**
     * Sets the pivot translation option in the data store.
     */
    suspend fun setPivotTranslation(enabled: Boolean)

    /**
     * Gets the language pair from the data store.
     */
    fun getLanguagePair(): Flow<LanguageOptionPair?>

    /**
     * Swaps the source and target languages in the data store.
     */
    suspend fun swapLanguages()


    /**
     * Clears the source language in the data store.
     */
    suspend fun clearSourceLanguage()

    /**
     * Clears the target language in the data store.
     */
    suspend fun clearTargetLanguage()

    /**
     * Clears the language selection if the language pair is the same as the current one.
     */
    suspend fun clearLanguageSelectionForPair(languagePair: LanguagePair)
}
