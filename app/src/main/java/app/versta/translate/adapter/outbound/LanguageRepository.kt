package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageModelMetadata
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import app.versta.translate.core.entity.LanguageModel
import kotlinx.coroutines.flow.Flow

interface LanguageRepository {
    /**
     * Gets the language models metadata available in the repository.
     */
    fun getLanguages(): Flow<List<LanguagePairWithModelFiles>>

    /**
     * Gets the languages available in the repository.
     */
    fun getLanguagePairs(): Flow<List<LanguagePair>>

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
     * Inserts a [LanguageModelMetadata] into the repository, ignoring if it already exists.
     * @param languageBundleMetadata The metadata of the bundle containing the language model.
     * @param languageModelMetadata The metadata of the language model to insert.
     */
    fun insertLanguageOrIgnore(languageBundleMetadata: LanguageBundleMetadata, languageModelMetadata: LanguageModelMetadata)

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertLanguageModel(metadata: LanguageModelMetadata)

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertLanguageModels(metadata: LanguageModel)

    /**
     * Deletes the language models in the repository by the source, including all related models.
     * @param language The language to delete.
     */
    fun deleteLanguageModelsBySourceLanguage(language: Language): List<LanguagePair>

    /**
     * Deletes the language models in the repository.
     * @param languagePair The language pair to delete.
     * @param bidirectional Whether to delete the bidirectional model.
     */
    fun deleteLanguageModel(languagePair: LanguagePair, bidirectional: Boolean): List<LanguagePair>
}