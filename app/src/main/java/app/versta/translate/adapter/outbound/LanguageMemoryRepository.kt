package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageModelMetadata
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguageModelPair
import app.versta.translate.core.entity.LanguageBundleData
import app.versta.translate.core.entity.PivotPairModelFiles
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
        "en-ja" to LanguageModel(
            baseModel = "Helsinki-NLP/opus-mt-en-ja",
            path = _mockPath,
            version = "v1.0.0",
            score = 52.8,
            files = LanguageModelFiles(
                model = _mockPath,
                vocabulary = _mockPath,
                shortlist = _mockPath,
            ),
            config = LanguageModelConfiguration(
                encoderLayers = 6,
                decoderLayers = 6,
                ffnDepth = 2,
                numHeads = 8,
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
     * Gets the target languages available in the repository.
     */
    override fun getTargetLanguages() = flowOf(_languages.map { it.target }.distinct())

    /**
     * Gets the language models metadata available in the repository.
     */
    override fun getLanguages(): Flow<List<LanguageModelPair>> = flow {
        emit(
            _languages.map {
                val files = _languageModels[it.id] ?: return@map null

                LanguageModelPair(
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
        _languages.filter { it.source == sourceLanguage }.map { it.target }.distinct()

    /**
     * Gets the language model files for a given language pair.
     */
    override fun getLanguageModel(languagePair: LanguagePair, pivotTranslation: Boolean): PivotPairModelFiles? {
        return PivotPairModelFiles(
            intermediary = null,
            output = _languageModels[languagePair.toString()]
        )
    }

    /**
     * Inserts a [LanguageModelMetadata] into the repository, ignoring if it already exists.
     * @param languageBundleMetadata The metadata of the bundle containing the language model.
     * @param languageModelMetadata The metadata of the language model to insert.
     */
    override fun insertLanguageOrIgnore(
        languageBundleMetadata: LanguageBundleMetadata,
        languageModelMetadata: LanguageModelMetadata
    ) {
        val sourceLanguage = Language.fromIsoCode(languageModelMetadata.sourceLanguage)
        val targetLanguage = Language.fromIsoCode(languageModelMetadata.targetLanguage)

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
    override fun upsertLanguageModel(metadata: LanguageModelMetadata) {
        val path = metadata.root ?: Path("")

        val sourceLanguage = Language.fromIsoCode(metadata.sourceLanguage)
        val targetLanguage = Language.fromIsoCode(metadata.targetLanguage)

        _languageModels[LanguagePair(sourceLanguage, targetLanguage).toString()] =
            LanguageModel(
                path = path,
                baseModel = metadata.baseModel,

                version = metadata.version,
                score = metadata.score ?: 0.0,
                files = LanguageModelFiles(
                    model = Path(metadata.files.model),
                    vocabulary = Path(metadata.files.vocabulary),
                    targetVocabulary = metadata.files.targetVocabulary?.let {
                        Path(it)
                    },
                    shortlist = Path(metadata.files.shortlist),
                ),
                config = LanguageModelConfiguration(
                    encoderLayers = metadata.config.encoderLayers,
                    decoderLayers = metadata.config.decoderLayers,
                    ffnDepth = metadata.config.ffnDepth,
                    numHeads = metadata.config.numHeads,
                    splitMode = metadata.config.splitMode
                )
            )
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModels(metadata: LanguageBundleData) {
        metadata.languages.forEach {
            insertLanguageOrIgnore(metadata.bundle, it)
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
     * @param bidirectional Whether to delete the bidirectional model.
     */
    override fun deleteLanguageModel(languagePair: LanguagePair, bidirectional: Boolean): List<LanguagePair> {
        _languages.remove(languagePair)
        _languageModels.remove(languagePair.id)

        return listOf(languagePair)
    }
}