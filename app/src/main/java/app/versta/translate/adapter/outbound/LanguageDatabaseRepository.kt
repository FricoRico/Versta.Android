package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageModelMetadata
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguageModelPair
import app.versta.translate.core.entity.LanguageBundleData
import app.versta.translate.core.entity.PivotPair
import app.versta.translate.core.entity.PivotPairModelFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import java.util.Locale
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.Language as LanguageDatabaseModel
import java.app.versta.translate.database.sqldelight.LanguageModel as LanguageModelDatabaseModel

class LanguageDatabaseRepository(
    private val database: DatabaseContainer,
) : LanguageRepository {
    /**
     * Gets the languages available in the repository.
     */
    override fun getLanguagePairs() = database.languages.getAll().executeAsListFlow()
        .map { it.map { language -> mapLanguageDatabaseModelToLanguagePair(language) } }

    /**
     * Gets the source languages available in the repository.
     */
    override fun getSourceLanguages() =
        database.languages.getAllSourceLanguages().executeAsListFlow().map {
            it.map { language -> mapSingleLanguageDatabaseModelToLanguage(language) }
                .plus(AutoDetectLanguage())
                .sortedBy { language -> if (language is AutoDetectLanguage) 0 else 1 }
        }

    /**
     * Gets the target languages available in the repository.
     */
    override fun getTargetLanguages(): Flow<List<Language>> =
        database.languages.getAllTargetLanguages().executeAsListFlow().map {
            it.map { language -> mapSingleLanguageDatabaseModelToLanguage(language) }
        }

    /**
     * Gets the language models metadata available in the repository.
     */
    override fun getLanguages(): Flow<List<LanguageModelPair>> =
        database.languages.getAll().executeAsListFlow().map {
            it.map { language ->
                val languageModel = mapLanguageModelDatabaseModelToLanguageModelFiles(
                    data = database.languageModels.getAllByLanguageId(language.id)
                        .executeAsOneOrNull()
                ) ?: return@map null

                LanguageModelPair(
                    sourceLocale = Locale.forLanguageTag(language.source),
                    targetLocale = Locale.forLanguageTag(language.target),
                    files = languageModel
                )
            }.filterNotNull()
        }

    /**
     * Gets the target languages for a given source language.
     */
    override fun getTargetLanguagesBySource(sourceLanguage: Language) =
        database.languages.getAllBySourceLanguage(sourceLanguage.locale.language)
            .executeAsList()
            .map { mapSingleLanguageDatabaseModelToLanguage(it) }

    /**
     * Gets the language model files for a given language pair.
     */
    override fun getLanguageModel(
        languagePair: LanguagePair,
        pivotTranslation: Boolean
    ): PivotPairModelFiles? {
        var output = database.languageModels.getAllByLanguageId(
            languagePair.id
        ).executeAsOneOrNull()

        if (output != null) {
            return PivotPairModelFiles(null, mapLanguageModelDatabaseModelToLanguageModelFiles(output))
        }

        if (!pivotTranslation) {
            return null
        }

        val pivotPair =
            findPivotPair(languagePair.source, languagePair.target) ?: return null

        val intermediary = database.languageModels.getAllByLanguageId(
            pivotPair.intermediary.id
        ).executeAsOneOrNull()

        output = database.languageModels.getAllByLanguageId(
            pivotPair.output.id
        ).executeAsOneOrNull()

        return PivotPairModelFiles(
            intermediary = mapLanguageModelDatabaseModelToLanguageModelFiles(
                intermediary
            ),
            output = mapLanguageModelDatabaseModelToLanguageModelFiles(
                output
            )
        )
    }

    private fun findPivotPair(
        source: Language, target: Language
    ): PivotPair? {
        val sourceLanguage = database.languages.getBySource(source.locale.language)
            .executeAsList()
        val targetLanguage = database.languages.getByTarget(target.locale.language)
            .executeAsList()

        val intermediaryPair = sourceLanguage.find { sourceLang ->
            targetLanguage.any { targetLang ->
                sourceLang.target == targetLang.source
            }
        }

        val outputPair = targetLanguage.find { targetLang ->
            sourceLanguage.any { sourceLang ->
                targetLang.source == sourceLang.target
            }
        }

        if (intermediaryPair == null || outputPair == null) {
            return null
        }

        return PivotPair(
            intermediary = LanguagePair.fromIsoCodes(intermediaryPair.source, intermediaryPair.target),
            output = LanguagePair.fromIsoCodes(outputPair.source, outputPair.target),
        )
    }

    /**
     * Inserts a [LanguageModelMetadata] into the repository, ignoring if it already exists.
     * @param languageBundleMetadata The metadata of the bundle containing the language model.
     * @param languageModelMetadata The metadata of the language model to insert.
     */
    override fun insertLanguageOrIgnore(
        languageBundleMetadata: LanguageBundleMetadata, languageModelMetadata: LanguageModelMetadata
    ) {
        val languageModel = mapLanguageMetadataToLanguageDatabaseModel(
            languageBundleMetadata, languageModelMetadata
        )
        insertLanguageDatabaseModelOrIgnore(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModel(metadata: LanguageModelMetadata) {
        val languageModel = mapLanguageMetadataToLanguageModelDatabaseModel(data = metadata)
        upsertLanguageModelDatabaseModel(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModels(metadata: LanguageBundleData) {
        database.languages.transaction {
            metadata.languages.forEach {
                insertLanguageOrIgnore(
                    languageBundleMetadata = metadata.bundle, languageModelMetadata = it
                )
                upsertLanguageModel(metadata = it)
            }
        }
    }

    /**
     * Deletes the language models in the repository by the source, including all related models.
     * @param language The language to delete.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteLanguageModelsBySourceLanguage(language: Language): List<LanguagePair> {
        val languagePairs =
            database.languages.getBySourceIncludingBidirecional(source = language.isoCode)
                .executeAsList().map { mapLanguageDatabaseModelToLanguagePair(it) }
        val languageIds = languagePairs.map { it.id }

        database.languageModels.getAllByLanguageIds(languageIds = languageIds).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct().forEach { it.deleteRecursively() }

        database.languages.deleteByIds(ids = languageIds)

        return languagePairs
    }

    /**
     * Deletes the language models in the repository.
     * @param languagePair The language pair to delete.
     * @param bidirectional Whether to delete the bidirectional model.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteLanguageModel(
        languagePair: LanguagePair, bidirectional: Boolean
    ): List<LanguagePair> {
        val languagePairs = listOfNotNull(
            languagePair, if (bidirectional) {
                LanguagePair(
                    source = languagePair.target, target = languagePair.source
                )
            } else {
                null
            }
        )
        val languageIds = languagePairs.map { it.id }

        database.languageModels.getAllByLanguageIds(languageIds = languageIds).executeAsList()
            .map { it.path.toPath().toNioPath().parent }.distinct()
            .forEach { it.deleteRecursively() }

        database.languages.deleteByIds(ids = languageIds)
        database.languageModels.deleteByIds(languageIds = languageIds)

        return languagePairs
    }

    /**
     * Inserts a [LanguageDatabaseModel] into the repository, ignoring if it already exists.
     * @param data The data to insert.
     */
    private fun insertLanguageDatabaseModelOrIgnore(data: LanguageDatabaseModel) {
        database.languages.insertOrIgnore(
            id = data.id,
            source = data.source,
            target = data.target,
            bidirectional = data.bidirectional
        )
    }

    /**
     * Inserts or updates a [LanguageModelDatabaseModel] into the repository.
     * @param data The data to upsert.
     */
    private fun upsertLanguageModelDatabaseModel(data: LanguageModelDatabaseModel) {
        database.languageModels.upsert(
            languageId = data.languageId,
            baseModel = data.baseModel,
            path = data.path,
            version = data.version,
        )
    }

    /**
     * Maps a [LanguageModelMetadata] to a [LanguageDatabaseModel].
     * @param languageModelMetadata The language metadata to map.
     */
    private fun mapLanguageMetadataToLanguageDatabaseModel(
        languageBundleMetadata: LanguageBundleMetadata, languageModelMetadata: LanguageModelMetadata
    ): LanguageDatabaseModel {
        val source = Language.fromIsoCode(languageModelMetadata.sourceLanguage)
        val target = Language.fromIsoCode(languageModelMetadata.targetLanguage)
        val pair = LanguagePair(
            source = source,
            target = target,
        )

        return LanguageDatabaseModel(
            id = pair.id,
            source = source.locale.language,
            target = target.locale.language,
            bidirectional = languageBundleMetadata.bidirectional
        )
    }

    /**
     * Maps a [LanguageModelMetadata] to a [LanguageModelDatabaseModel].
     * @param data The language metadata to map.
     */
    private fun mapLanguageMetadataToLanguageModelDatabaseModel(data: LanguageModelMetadata): LanguageModelDatabaseModel {
        val source = Language.fromIsoCode(data.sourceLanguage)
        val target = Language.fromIsoCode(data.targetLanguage)
        val pair = LanguagePair(
            source = source,
            target = target,
        )

        return LanguageModelDatabaseModel(
            languageId = pair.id,
            baseModel = data.baseModel,
            path = data.root?.absolutePathString() ?: "",
            version = data.version,
        )
    }

    /**
     * Maps a [LanguageModelDatabaseModel] to a [LanguageModel].
     * @param data The language model database model to map.
     * @return The mapped language model files.
     */
    private fun mapLanguageModelDatabaseModelToLanguageModelFiles(data: LanguageModelDatabaseModel?): LanguageModel? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return LanguageModel.load(path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load language model files")
            return null
        }
    }

    /**
     * Maps a [String] iso code to a [Language].
     * @param data The language to map.
     * @return The mapped language.
     */
    private fun mapSingleLanguageDatabaseModelToLanguage(data: String): Language {
        return Language.fromIsoCode(data)
    }

    /**
     * Maps a [LanguageDatabaseModel] to a [LanguagePair].
     * @param data The language database model to map.
     * @return The mapped language pair.
     */
    private fun mapLanguageDatabaseModelToLanguagePair(data: LanguageDatabaseModel): LanguagePair {
        val source = Language.fromIsoCode(data.source)
        val target = Language.fromIsoCode(data.target)

        return LanguagePair(
            source = source, target = target
        )
    }

    companion object {
        private val TAG: String = LanguageDatabaseRepository::class.java.simpleName
    }
}