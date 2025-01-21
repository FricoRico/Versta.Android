package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.BundleMetadata
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
    fun getLanguageModel(languagePair: LanguagePair): LanguageModelFiles?

    /**
     * Inserts a [LanguageMetadata] into the repository, ignoring if it already exists.
     * @param bundleMetadata The metadata of the bundle containing the language model.
     * @param languageMetadata The metadata of the language model to insert.
     */
    fun insertLanguageOrIgnore(bundleMetadata: BundleMetadata, languageMetadata: LanguageMetadata)

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

    /**
     * Deletes the language models in the repository by the source, including all related models.
     * @param language The language to delete.
     */
    fun deleteLanguageModelsBySourceLanguage(language: Language): List<LanguagePair>

    /**
     * Deletes the language models in the repository.
     * @param languagePair The language pair to delete.
     */
    fun deleteLanguageModel(languagePair: LanguagePair): LanguagePair
}