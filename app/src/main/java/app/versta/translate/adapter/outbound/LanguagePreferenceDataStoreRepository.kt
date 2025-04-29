package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.versta.translate.core.entity.AUTO_DETECT_ISO_CODE
import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.LanguageOptionPair
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LanguagePreferenceDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : LanguagePreferenceRepository {
    /**
     * Gets the source language from the repository.
     */
    override fun getSourceLanguage(): Flow<LanguageOption?> {
        return dataStore.data.map { preferences ->
            val data = preferences[SOURCE_LANGUAGE_KEY]
            if (data != null) mapIsoCodeToLanguage(data) else null
        }
    }

    /**
     * Sets the source language in the repository.
     * @param language The language to set.
     */
    override suspend fun setSourceLanguage(language: LanguageOption) {
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
            if (data != null) {
                val language = mapIsoCodeToLanguage(data)

                if (language is Language) {
                    return@map language
                }

                return@map null
            }

            null
        }
    }

    /**
     * Gets the language pair from the repository.
     */
    override fun getLanguagePair(): Flow<LanguageOptionPair?> {
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
     * Gets the pivot translation option from the data store.
     */
    override fun getPivotTranslation(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[PIVOT_TRANSLATION_KEY]?.toBoolean() ?: DEFAULT_PIVOT_TRANSLATION
        }
    }

    /**
     * Sets the pivot translation option in the data store.
     */
    override suspend fun setPivotTranslation(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PIVOT_TRANSLATION_KEY] = enabled.toString()
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
    override suspend fun clearSourceLanguage() {
        dataStore.edit { preferences ->
            preferences.remove(SOURCE_LANGUAGE_KEY)
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
    private fun mapLanguageEntityToIsoCode(language: LanguageOption): String {
        return language.isoCode
    }

    /**
     * Maps an ISO code to a [Language].
     * @param isoCode The ISO code to map.
     */
    private fun mapIsoCodeToLanguage(isoCode: String): LanguageOption {
        if (isoCode == AUTO_DETECT_ISO_CODE) {
            return AutoDetectLanguage()
        }

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
    ): LanguageOptionPair? {
        val source = mapIsoCodeToLanguage(sourceIsoCode)
        val target = mapIsoCodeToLanguage(targetIsoCode)

        if (target !is Language) {
            return null
        }

        return LanguageOptionPair(
            source = source,
            target = target
        )
    }

    companion object {
        val SOURCE_LANGUAGE_KEY = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE_KEY = stringPreferencesKey("target_language")
        val PIVOT_TRANSLATION_KEY = stringPreferencesKey("pivot_translation_enabled")
    }
}
