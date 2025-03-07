package app.versta.translate.adapter.outbound

import android.util.Log
import app.versta.translate.core.entity.BundleMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LanguagePairWithModelFiles
import app.versta.translate.core.entity.ModelMetadata
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
        database.languages.getAllSourceLanguages().executeAsListFlow()
            .map { it.map { language -> mapSingleLanguageDatabaseModelToLanguage(language) } }

    /**
     * Gets the language models metadata available in the repository.
     */
    override fun getLanguages(): Flow<List<LanguagePairWithModelFiles>> =
        database.languages.getAll().executeAsListFlow().map {
            it.map { language ->
                val languageModel = mapLanguageModelDatabaseModelToLanguageModelFiles(
                    data = database.languageModels.getAllByLanguageId(language.id).executeAsOneOrNull()
                ) ?: return@map null

                LanguagePairWithModelFiles(
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
            .executeAsListFlow()
            .map { it.map { language -> mapSingleLanguageDatabaseModelToLanguage(language) } }

    /**
     * Gets the language model files for a given language pair.
     */
    override fun getLanguageModel(languagePair: LanguagePair) =
        mapLanguageModelDatabaseModelToLanguageModelFiles(
            database.languageModels.getAllByLanguageId(
                languagePair.id
            ).executeAsOneOrNull()
        )

    /**
     * Inserts a [LanguageMetadata] into the repository, ignoring if it already exists.
     * @param bundleMetadata The metadata of the bundle containing the language model.
     * @param languageMetadata The metadata of the language model to insert.
     */
    override fun insertLanguageOrIgnore(
        bundleMetadata: BundleMetadata, languageMetadata: LanguageMetadata
    ) {
        val languageModel =
            mapLanguageMetadataToLanguageDatabaseModel(bundleMetadata, languageMetadata)
        insertLanguageDatabaseModelOrIgnore(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModel(metadata: LanguageMetadata) {
        val languageModel = mapLanguageMetadataToLanguageModelDatabaseModel(data = metadata)
        upsertLanguageModelDatabaseModel(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModels(metadata: ModelMetadata) {
        database.languages.transaction {
            metadata.languageMetadata.forEach {
                insertLanguageOrIgnore(
                    bundleMetadata = metadata.bundleMetadata, languageMetadata = it
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
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.languages.deleteByIds(ids = languageIds)

        return languagePairs
    }

    /**
     * Deletes the language models in the repository.
     * @param languagePair The language pair to delete.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteLanguageModel(languagePair: LanguagePair): LanguagePair {
        database.languageModels.getAllByLanguageId(languageId = languagePair.id).executeAsList()
            .map { it.path.toPath().toNioPath().parent }.distinct()
            .forEach { it.deleteRecursively() }

        database.languages.deleteById(id = languagePair.id)

        return languagePair
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
            architectures = data.architectures,
            path = data.path,
            version = data.version,
        )
    }

    /**
     * Maps a [LanguageMetadata] to a [LanguageDatabaseModel].
     * @param languageMetadata The language metadata to map.
     */
    private fun mapLanguageMetadataToLanguageDatabaseModel(
        bundleMetadata: BundleMetadata, languageMetadata: LanguageMetadata
    ): LanguageDatabaseModel {
        val source = Language.fromIsoCode(languageMetadata.sourceLanguage)
        val target = Language.fromIsoCode(languageMetadata.targetLanguage)
        val pair = LanguagePair(
            source = source,
            target = target,
        )

        return LanguageDatabaseModel(
            id = pair.id,
            source = source.locale.language,
            target = target.locale.language,
            bidirectional = bundleMetadata.bidirectional
        )
    }

    /**
     * Maps a [LanguageMetadata] to a [LanguageModelDatabaseModel].
     * @param data The language metadata to map.
     */
    private fun mapLanguageMetadataToLanguageModelDatabaseModel(data: LanguageMetadata): LanguageModelDatabaseModel {
        val source = Language.fromIsoCode(data.sourceLanguage)
        val target = Language.fromIsoCode(data.targetLanguage)
        val pair = LanguagePair(
            source = source,
            target = target,
        )

        return LanguageModelDatabaseModel(
            languageId = pair.id,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            version = data.version,
        )
    }

    /**
     * Maps a [LanguageModelDatabaseModel] to a [LanguageModelFiles].
     * @param data The language model database model to map.
     */
    private fun mapLanguageModelDatabaseModelToLanguageModelFiles(data: LanguageModelDatabaseModel?): LanguageModelFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return LanguageModelFiles.load(path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load language model files")
            return null
        }
    }

    /**
     * Maps a [String] iso code to a [Language].
     * @param data The language to map.
     */
    private fun mapSingleLanguageDatabaseModelToLanguage(data: String): Language {
        return Language.fromIsoCode(data)
    }

    /**
     * Maps a [LanguageDatabaseModel] to a [LanguagePair].
     * @param data The language database model to map.
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