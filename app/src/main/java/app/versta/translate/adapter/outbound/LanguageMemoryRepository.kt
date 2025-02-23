package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.BundleMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import app.versta.translate.core.entity.ModelArchitecture
import app.versta.translate.core.entity.ModelMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class LanguageMemoryRepository : LanguageRepository {
    private val _mockPath = Path("")

    private val _languages = mutableListOf(
        LanguagePair(
            source = Language.fromIsoCode("en"),
            target = Language.fromIsoCode("ja"),
        ),
        LanguagePair(
            source = Language.fromIsoCode("en"),
            target = Language.fromIsoCode("de"),
        ),
        LanguagePair(
            source = Language.fromIsoCode("ja"),
            target = Language.fromIsoCode("en"),
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
            baseModel = "Helsinki-NLP/opus-mt-en-ja",
            path = _mockPath,
            architectures = listOf(ModelArchitecture.MarianMTModel),
            version = "v1.0.0",
            inference = LanguageModelInferenceFiles(
                encoder = _mockPath,
                decoder = _mockPath,
            )
        )
    )

    /**
     * Gets the languages available in the repository.
     */
    override fun getLanguagePairs() = flowOf(_languages)

    /**
     * Gets the source languages available in the repository.
     */
    override fun getSourceLanguages() = flowOf(_languages.map { it.source }.distinct())

    /**
     * Gets the language models metadata available in the repository.
     */
    override fun getLanguages(): Flow<List<LanguagePairWithModelFiles>> = flow {
        emit(
            _languages.map {
                val files = _languageModels[it.id] ?: return@map null

                LanguagePairWithModelFiles(
                    sourceLocale = it.source.locale,
                    targetLocale = it.target.locale,
                    files = files
                )
            }.filterNotNull()
        )
    }

    /**
     * Gets the target languages for a given source language.
     */
    override fun getTargetLanguagesBySource(sourceLanguage: Language) =
        flowOf(_languages.filter { it.source == sourceLanguage }.map { it.target }.distinct())

    /**
     * Gets the language model files for a given language pair.
     */
    override fun getLanguageModel(languagePair: LanguagePair): LanguageModelFiles? {
        return _languageModels[languagePair.toString()]
    }

    /**
     * Inserts a [LanguageMetadata] into the repository, ignoring if it already exists.
     * @param bundleMetadata The metadata of the bundle containing the language model.
     * @param languageMetadata The metadata of the language model to insert.
     */
    override fun insertLanguageOrIgnore(
        bundleMetadata: BundleMetadata,
        languageMetadata: LanguageMetadata
    ) {
        val sourceLanguage = Language.fromIsoCode(languageMetadata.sourceLanguage)
        val targetLanguage = Language.fromIsoCode(languageMetadata.targetLanguage)

        if (_languages.any { it.source == sourceLanguage && it.target == targetLanguage }) {
            return
        }

        _languages.add(
            LanguagePair(
                source = sourceLanguage,
                target = targetLanguage,
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

        _languageModels[LanguagePair(sourceLanguage, targetLanguage).toString()] =
            LanguageModelFiles(
                path = path,
                baseModel = metadata.baseModel,
                tokenizer = LanguageModelTokenizerFiles(
                    config = Path(metadata.files.tokenizer.config),
                    sourceVocabulary = Path(metadata.files.tokenizer.sourceVocabulary),
                    targetVocabulary = metadata.files.tokenizer.targetVocabulary?.let { Path(it) },
                    source = Path(metadata.files.tokenizer.source),
                    target = Path(metadata.files.tokenizer.target)
                ),
                architectures = metadata.architectures,
                version = metadata.version,
                inference = LanguageModelInferenceFiles(
                    encoder = Path(metadata.files.inference.encoder),
                    decoder = Path(metadata.files.inference.decoder)
                )
            )
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModels(metadata: ModelMetadata) {
        metadata.languageMetadata.forEach {
            insertLanguageOrIgnore(metadata.bundleMetadata, it)
            upsertLanguageModel(it)
        }
    }

    /**
     * Deletes the language models in the repository by the source, including all related models.
     * @param language The language to delete.
     */
    override fun deleteLanguageModelsBySourceLanguage(language: Language): List<LanguagePair> {
        val pairs = _languages.filter { it.source == language || it.target == language }

        pairs.forEach { _languageModels.remove(it.toString()) }
        _languages.removeAll(pairs)

        return pairs
    }

    /**
     * Deletes the language models in the repository.
     * @param languagePair The language pair to delete.
     */
    override fun deleteLanguageModel(languagePair: LanguagePair): LanguagePair {
        _languages.remove(languagePair)
        _languageModels.remove(languagePair.id)

        return languagePair
    }
}