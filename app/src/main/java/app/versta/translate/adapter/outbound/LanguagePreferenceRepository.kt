package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LanguagePreferenceRepository(
    private val dataStore: DataStore<Preferences>
) {
    val sourceLanguage: Flow<Language?> = dataStore.data.map { preferences ->
        val data = preferences[SOURCE_LANGUAGE_KEY]
        if (data != null) mapIsoCodeToLanguage(data) else null
    }

    suspend fun setSourceLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[SOURCE_LANGUAGE_KEY] = mapLanguageEntityToIsoCode(language)
        }
    }

    val targetLanguage: Flow<Language?> = dataStore.data.map { preferences ->
        val data = preferences[TARGET_LANGUAGE_KEY]
        if (data != null) mapIsoCodeToLanguage(data) else null
    }

    val languagePair: Flow<LanguagePair?> = dataStore.data.map { preferences ->
        val sourceData = preferences[SOURCE_LANGUAGE_KEY]
        val targetData = preferences[TARGET_LANGUAGE_KEY]

        if (sourceData != null && targetData != null) {
            mapIsoCodesToLanguagePair(sourceData, targetData)
        } else {
            null
        }
    }

    suspend fun setTargetLanguage(language: Language) {
        dataStore.edit { preferences ->
            preferences[TARGET_LANGUAGE_KEY] = mapLanguageEntityToIsoCode(language)
        }
    }

    suspend fun swapLanguages() {
        dataStore.edit { preferences ->
            val sourceLanguage = preferences[SOURCE_LANGUAGE_KEY]
            val targetLanguage = preferences[TARGET_LANGUAGE_KEY]

            preferences[SOURCE_LANGUAGE_KEY] = targetLanguage ?: ""
            preferences[TARGET_LANGUAGE_KEY] = sourceLanguage ?: ""
        }
    }

    suspend fun clearTargetLanguage() {
        dataStore.edit { preferences ->
            preferences.remove(TARGET_LANGUAGE_KEY)
        }
    }

    private fun mapLanguageEntityToIsoCode(language: Language): String {
        return language.locale.language
    }

    private fun mapIsoCodeToLanguage(isoCode: String): Language {
        return Language.fromIsoCode(isoCode)
    }

    private fun mapIsoCodesToLanguagePair(sourceIsoCode: String, targetIsoCode: String): LanguagePair {
        return LanguagePair(mapIsoCodeToLanguage(sourceIsoCode), mapIsoCodeToLanguage(targetIsoCode))
    }

    companion object {
        val SOURCE_LANGUAGE_KEY = stringPreferencesKey("source_language")
        val TARGET_LANGUAGE_KEY = stringPreferencesKey("target_language")
    }
}
