package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.ModelMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class LanguageMemoryRepository : LanguageRepository {
    private val _mockPath = Path("")

    private val _languages = mutableListOf(
        LanguagePair(
            source = Language.fromIsoCode("en"),
            target = Language.fromIsoCode("ja")
        )
    )
    private val _languageModels = mutableMapOf(
        "en-ja" to LanguageModelFiles(
            tokenizer = LanguageModelTokenizerFiles(
                config = _mockPath,
                sourceVocabulary = _mockPath,
                targetVocabulary = _mockPath,
                source = _mockPath,
                target = _mockPath,
            ),
            path = _mockPath,
            inference = LanguageModelInferenceFiles(
                encoder = _mockPath,
                decoder = _mockPath,
            )
        )
    )

    /**
     * Gets the languages available in the repository.
     */
    override fun getLanguages(): Flow<List<LanguagePair>> {
        return flowOf(
            _languages
        )
    }

    /**
     * Gets the source languages available in the repository.
     */
    override fun getSourceLanguages(): Flow<List<Language>> {
        return flowOf(
            _languages.map { it.source }
        )
    }

    /**
     * Gets the target languages for a given source language.
     */
    override fun getTargetLanguagesBySource(sourceLanguage: Language): Flow<List<Language>> {
        return flowOf(
            _languages.filter { it.source == sourceLanguage }.map { it.target }
        )
    }

    /**
     * Gets the language model files for a given language pair.
     */
    override fun getLanguageModel(languagePair: LanguagePair): Flow<LanguageModelFiles?> {
        return flowOf(
            _languageModels[languagePair.toString()]
        )
    }

    /**
     * Inserts a [LanguageMetadata] into the repository, ignoring if it already exists.
     * @param metadata The metadata to insert.
     */
    override fun insertLanguageOrIgnore(metadata: LanguageMetadata) {
        val sourceLanguage = Language.fromIsoCode(metadata.sourceLanguage)
        val targetLanguage = Language.fromIsoCode(metadata.targetLanguage)

        if (_languages.any { it.source == sourceLanguage && it.target == targetLanguage }) {
            return
        }

        _languages.add(
            LanguagePair(
                source = sourceLanguage,
                target = targetLanguage
            )
        )
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModel(metadata: LanguageMetadata) {
        val path = metadata.root ?: Path("")

        val sourceLanguage = Language.fromIsoCode(metadata.sourceLanguage)
        val targetLanguage = Language.fromIsoCode(metadata.targetLanguage)

        _languageModels[LanguagePair(sourceLanguage, targetLanguage).toString()] = LanguageModelFiles(
            path = path,
            tokenizer = LanguageModelTokenizerFiles(
                config = path.resolve(metadata.files["tokenizer"]?.get("config") ?: ""),
                sourceVocabulary = path.resolve(metadata.files["tokenizer"]?.get("vocabulary_optimized") ?: ""),
                targetVocabulary = null, // TODO: Target vocabulary not supported right now
                source = path.resolve(metadata.files["tokenizer"]?.get("source") ?: ""),
                target = path.resolve(metadata.files["tokenizer"]?.get("target") ?: "")
            ),
            inference = LanguageModelInferenceFiles(
                encoder = path.resolve(metadata.files["inference"]?.get("encoder") ?: ""),
                decoder = path.resolve(metadata.files["inference"]?.get("decoder") ?: "")
            )
        )
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModels(metadata: ModelMetadata) {
        metadata.languageMetadata.forEach {
            insertLanguageOrIgnore(it)
            upsertLanguageModel(it)
        }
    }
}