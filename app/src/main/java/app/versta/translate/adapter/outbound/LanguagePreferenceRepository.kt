package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.flow.Flow

interface LanguagePreferenceRepository {
    /**
     * Gets the source language from the data store.
     */
    fun getSourceLanguage(): Flow<Language?>

    /**
     * Sets the source language in the data store.
     * @param language The language to set.
     */
    suspend fun setSourceLanguage(language: Language)

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
     * Gets the language pair from the data store.
     */
    fun getLanguagePair(): Flow<LanguagePair?>

    /**
     * Swaps the source and target languages in the data store.
     */
    suspend fun swapLanguages()

    /**
     * Clears the target language in the data store.
     */
    suspend fun clearTargetLanguage()

    /**
     * Clears the language selection if the language pair is the same as the current one.
     */
    suspend fun clearLanguageSelectionForPair(languagePair: LanguagePair)
}
