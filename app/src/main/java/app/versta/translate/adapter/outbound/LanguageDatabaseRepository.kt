package app.versta.translate.adapter.outbound

import androidx.core.net.toUri
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.ModelMetadata
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import java.nio.file.Path
import java.app.versta.translate.database.sqldelight.Language as LanguageDatabaseModel
import java.app.versta.translate.database.sqldelight.LanguageModel as LanguageModelDatabaseModel
import kotlin.io.path.absolutePathString

class LanguageDatabaseRepository(
    private val database: DatabaseContainer,
) {
    /**
     * Gets the languages available in the database.
     */
    fun getLanguages() = database.languages.getAll().executeAsListFlow().map { results ->
        results.map { mapLanguageDatabaseModelToLanguagePair(it) }
    }

    /**
     * Gets the source languages available in the database.
     */
    fun getSourceLanguages() =
        database.languages.getAllSourceLanguages().executeAsListFlow().map { results ->
            results.map { mapSingleLanguageDatabaseModelToLanguage(it) }
        }

    /**
     * Gets the target languages for a given source language.
     */
    fun getTargetLanguagesBySource(sourceLanguage: Language) =
            database.languages.getAllBySourceLanguage(sourceLanguage.locale.language)
                .executeAsListFlow().map { results ->
                results.map { mapSingleLanguageDatabaseModelToLanguage(it) }
            }

    fun getLanguageModel(languagePair: LanguagePair) =
        database.languageModels.getAllByLanguageId(languagePair.id).executeAsListFlow().map { results ->
            results.firstOrNull()?.let { mapLanguageModelDatabaseModelToLanguageModelFiles(it) }
        }

    /**
     * Inserts a [LanguageMetadata] into the database, ignoring if it already exists.
     * @param metadata The metadata to insert.
     */
    fun insertLanguageOrIgnore(metadata: LanguageMetadata) {
        val languageModel = mapLanguageMetadataToLanguageDatabaseModel(metadata)
        insertLanguageDatabaseModelOrIgnore(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the database.
     * @param metadata The metadata to insert or update.
     */
    fun upsertLanguageModel(metadata: LanguageMetadata) {
        val languageModel = mapLanguageMetadataToLanguageModelDatabaseModel(metadata)
        upsertLanguageModelDatabaseModel(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the database.
     * @param metadata The metadata to insert or update.
     */
    fun upsertLanguageModels(metadata: ModelMetadata) {
        database.languages.transaction {
            metadata.languageMetadata.forEach { metadata ->
                insertLanguageOrIgnore(metadata)
                upsertLanguageModel(metadata)
            }
        }
    }

    /**
     * Inserts a [LanguageDatabaseModel] into the database, ignoring if it already exists.
     * @param data The data to insert.
     */
    private fun insertLanguageDatabaseModelOrIgnore(data: LanguageDatabaseModel) {
        database.languages.insertOrIgnore(
            id = data.id,
            source = data.source,
            target = data.target,
        )
    }

    /**
     * Inserts or updates a [LanguageModelDatabaseModel] into the database.
     * @param data The data to upsert.
     */
    private fun upsertLanguageModelDatabaseModel(data: LanguageModelDatabaseModel) {
        database.languageModels.upsert(
            languageId = data.languageId,
            baseModel = data.baseModel,
            architectures = data.architectures,
            path = data.path,
            files = data.files
        )
    }

    /**
     * Maps a [LanguageMetadata] to a [LanguageDatabaseModel].
     * @param data The language metadata to map.
     */
    private fun mapLanguageMetadataToLanguageDatabaseModel(data: LanguageMetadata): LanguageDatabaseModel {
        val source = Language.fromIsoCode(data.sourceLanguage)
        val target = Language.fromIsoCode(data.targetLanguage)

        return mapLanguagePairToLanguageDatabaseModel(
            data = LanguagePair(
                source = source,
                target = target
            )
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
            target = target
        )

        return LanguageModelDatabaseModel(
            languageId = pair.id,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            files = data.files
        )
    }

    private fun mapLanguageModelDatabaseModelToLanguageModelFiles(data: LanguageModelDatabaseModel): LanguageModelFiles {
        val path = data.path.toPath().toNioPath()

        return LanguageModelFiles(
            path = path,
            tokenizer = LanguageModelTokenizerFiles(
                config = path.resolve(data.files["tokenizer"]?.get("config") ?: ""),
                sourceVocabulary = path.resolve(data.files["tokenizer"]?.get("vocabulary") ?: ""),
                targetVocabulary = null, // TODO: Target vocabulary not supported right now
                source = path.resolve(data.files["tokenizer"]?.get("source") ?: ""),
                target = path.resolve(data.files["tokenizer"]?.get("target") ?: "")
            ),
            inference = LanguageModelInferenceFiles(
                encoder = path.resolve(data.files["inference"]?.get("encoder") ?: ""),
                decoder = path.resolve(data.files["inference"]?.get("decoder") ?: "")
            )
        )
    }

    /**
     * Maps a [LanguagePair] to a [LanguageDatabaseModel].
     * @param data The language pair to map.
     */
    private fun mapLanguagePairToLanguageDatabaseModel(data: LanguagePair): LanguageDatabaseModel {
        return LanguageDatabaseModel(
            id = data.id,
            source = data.source.locale.language,
            target = data.target.locale.language
        )
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
            source = source,
            target = target
        )
    }
}