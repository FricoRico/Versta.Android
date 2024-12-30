package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.ModelMetadata
import kotlinx.coroutines.flow.Flow

interface LanguageRepository {
    /**
     * Gets the languages available in the repository.
     */
    fun getLanguages(): Flow<List<LanguagePair>>

    /**
     * Gets the source languages available in the repository.
     */
    fun getSourceLanguages(): Flow<List<Language>>

    /**
     * Gets the target languages for a given source language.
     */
    fun getTargetLanguagesBySource(sourceLanguage: Language): Flow<List<Language>>

    /**
     * Gets the language model files for a given language pair.
     */
    fun getLanguageModel(languagePair: LanguagePair): Flow<LanguageModelFiles?>

    /**
     * Inserts a [LanguageMetadata] into the repository, ignoring if it already exists.
     * @param metadata The metadata to insert.
     */
    fun insertLanguageOrIgnore(metadata: LanguageMetadata)

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertLanguageModel(metadata: LanguageMetadata)

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertLanguageModels(metadata: ModelMetadata)
}