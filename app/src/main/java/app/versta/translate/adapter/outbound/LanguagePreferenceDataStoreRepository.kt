package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LanguagePreferenceDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : LanguagePreferenceRepository {
    /**
     * Gets the source language from the repository.
     */
    override fun getSourceLanguage(): Flow<Language?> {
        return dataStore.data.map { preferences ->
            val data = preferences[SOURCE_LANGUAGE_KEY]
            if (data != null) mapIsoCodeToLanguage(data) else null
        }
    }

    /**
     * Sets the source language in the repository.
     * @param language The language to set.
     */
    override suspend fun setSourceLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[SOURCE_LANGUAGE_KEY] = mapLanguageEntityToIsoCode(language)
        }
    }

    /**
     * Gets the target language from the repository.
     */
    override fun getTargetLanguage(): Flow<Language?> {
        return dataStore.data.map { preferences ->
            val data = preferences[TARGET_LANGUAGE_KEY]
            if (data != null) mapIsoCodeToLanguage(data) else null
        }
    }

    /**
     * Gets the language pair from the repository.
     */
    override fun getLanguagePair(): Flow<LanguagePair?> {
        return dataStore.data.map { preferences ->
            val sourceData = preferences[SOURCE_LANGUAGE_KEY]
            val targetData = preferences[TARGET_LANGUAGE_KEY]

            if (sourceData != null && targetData != null) {
                mapIsoCodesToLanguagePair(sourceData, targetData)
            } else {
                null
            }
        }
    }

    /**
     * Sets the target language in the repository.
     * @param language The language to set.
     */
    override suspend fun setTargetLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[TARGET_LANGUAGE_KEY] = mapLanguageEntityToIsoCode(language)
        }
    }

    /**
     * Swaps the source and target languages in the repository.
     */
    override suspend fun swapLanguages() {
        dataStore.edit { preferences ->
            val sourceLanguage = preferences[SOURCE_LANGUAGE_KEY]
            val targetLanguage = preferences[TARGET_LANGUAGE_KEY]

            preferences[SOURCE_LANGUAGE_KEY] = targetLanguage ?: ""
            preferences[TARGET_LANGUAGE_KEY] = sourceLanguage ?: ""
        }
    }

    /**
     * Clears the target language in the repository.
     */
    override suspend fun clearTargetLanguage() {
        dataStore.edit { preferences ->
            preferences.remove(TARGET_LANGUAGE_KEY)
        }
    }

    /**
     * Clears the language selection if the language pair is the same as the current one.
     */
    override suspend fun clearLanguageSelectionForPair(languagePair: LanguagePair) {
        dataStore.edit { preferences ->
            val sourceLanguage = preferences[SOURCE_LANGUAGE_KEY]
            val targetLanguage = preferences[TARGET_LANGUAGE_KEY]

            if (sourceLanguage == mapLanguageEntityToIsoCode(languagePair.source) ||
                targetLanguage == mapLanguageEntityToIsoCode(languagePair.target) ||
                sourceLanguage == mapLanguageEntityToIsoCode(languagePair.source)
            ) {
                preferences.remove(SOURCE_LANGUAGE_KEY)
                preferences.remove(TARGET_LANGUAGE_KEY)
            }
        }
    }

    /**
     * Maps a [Language] to an ISO code.
     * @param language The language to map.
     */
    private fun mapLanguageEntityToIsoCode(language: Language): String {
        return language.locale.language
    }

    /**
     * Maps an ISO code to a [Language].
     * @param isoCode The ISO code to map.
     */
    private fun mapIsoCodeToLanguage(isoCode: String): Language {
        return Language.fromIsoCode(isoCode)
    }

    /**
     * Maps ISO codes to a [LanguagePair].
     * @param sourceIsoCode The source language ISO code.
     * @param targetIsoCode The target language ISO code.
     */
    private fun mapIsoCodesToLanguagePair(
        sourceIsoCode: String,
        targetIsoCode: String
    ): LanguagePair {
        return LanguagePair(
            mapIsoCodeToLanguage(sourceIsoCode),
            mapIsoCodeToLanguage(targetIsoCode)
        )
    }

    companion object {
        val SOURCE_LANGUAGE_KEY = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE_KEY = stringPreferencesKey("target_language")
    }
}
