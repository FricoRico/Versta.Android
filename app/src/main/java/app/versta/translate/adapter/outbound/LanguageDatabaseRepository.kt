package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageBundleData
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelMetadata
import app.versta.translate.core.entity.LanguageModelPair
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.PivotPair
import app.versta.translate.core.entity.PivotPairModelFiles
import app.versta.translate.core.entity.localeForLanguageCode
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File
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
     *
     * Observes both the language and the model tables so installs, updates and
     * removals emit regardless of which table changed.
     */
    override fun getLanguagePairs() =
        combine(
            database.languages.getAll().executeAsListFlow(),
            database.languageModels.getAll().executeAsListFlow(),
        ) { languages, _ ->
            languages.map { language -> mapLanguageDatabaseModelToLanguagePair(language) }
        }

    /**
     * Gets the source languages available in the repository.
     */
    override fun getSourceLanguages() =
        database.languages.getAllSourceLanguages().executeAsListFlow().map {
            it
                .map { language -> mapSingleLanguageDatabaseModelToLanguage(language) }
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
     *
     * Observes both the language and the model tables: the queries run inside
     * [upsertLanguageModels] touch both, and observing only the language table
     * misses the emission when the installer commits while the screen is open.
     */
    override fun getLanguages(): Flow<List<LanguageModelPair>> =
        combine(
            database.languages.getAll().executeAsListFlow(),
            database.languageModels.getAll().executeAsListFlow(),
        ) { languages, languageModels ->
            languages
                .map { language ->
                    val languageModel =
                        mapLanguageModelDatabaseModelToLanguageModelFiles(
                            data = languageModels.firstOrNull { it.languageId == language.id },
                        ) ?: return@map null

                    LanguageModelPair(
                        sourceLocale = localeForLanguageCode(language.source),
                        targetLocale = localeForLanguageCode(language.target),
                        files = languageModel,
                    )
                }.filterNotNull()
        }

    /**
     * Gets the target languages for a given source language.
     */
    override fun getTargetLanguagesBySource(sourceLanguage: Language) =
        database.languages
            .getAllBySourceLanguage(sourceLanguage.normalizedCode)
            .executeAsList()
            .map { mapSingleLanguageDatabaseModelToLanguage(it) }

    /**
     * Gets the language model files for a given language pair.
     */
    override fun getLanguageModel(
        languagePair: LanguagePair,
        pivotTranslation: Boolean,
    ): PivotPairModelFiles? {
        var output =
            database.languageModels
                .getAllByLanguageId(
                    languagePair.id,
                ).executeAsOneOrNull()

        if (output != null) {
            return PivotPairModelFiles(null, mapLanguageModelDatabaseModelToLanguageModelFiles(output))
        }

        if (!pivotTranslation) {
            return null
        }

        val pivotPair =
            findPivotPair(languagePair.source, languagePair.target) ?: return null

        val intermediary =
            database.languageModels
                .getAllByLanguageId(
                    pivotPair.intermediary.id,
                ).executeAsOneOrNull()

        output =
            database.languageModels
                .getAllByLanguageId(
                    pivotPair.output.id,
                ).executeAsOneOrNull()

        return PivotPairModelFiles(
            intermediary =
                mapLanguageModelDatabaseModelToLanguageModelFiles(
                    intermediary,
                ),
            output =
                mapLanguageModelDatabaseModelToLanguageModelFiles(
                    output,
                ),
        )
    }

    private fun findPivotPair(
        source: Language,
        target: Language,
    ): PivotPair? {
        val sourceLanguage =
            database.languages
                .getBySource(source.normalizedCode)
                .executeAsList()
        val targetLanguage =
            database.languages
                .getByTarget(target.normalizedCode)
                .executeAsList()

        val intermediaryPair =
            sourceLanguage.find { sourceLang ->
                targetLanguage.any { targetLang ->
                    sourceLang.target == targetLang.source
                }
            }

        val outputPair =
            targetLanguage.find { targetLang ->
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
        languageBundleMetadata: LanguageBundleMetadata,
        languageModelMetadata: LanguageModelMetadata,
    ) {
        val languageModel =
            mapLanguageMetadataToLanguageDatabaseModel(
                languageBundleMetadata,
                languageModelMetadata,
            )
        insertLanguageDatabaseModelOrIgnore(data = languageModel)
    }

    /**
     * Inserts or updates the language models in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertLanguageModel(metadata: LanguageModelMetadata) {
        upsertLanguageModel(metadata = metadata, bundleVersion = metadata.version)
    }

    /**
     * Inserts or updates a single language model in the repository.
     *
     * The stored version identifies the installed bundle, matching the version
     * scheme used by the external catalog, so [app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository.getDefinitionsByState]
     * can compare installed against catalog versions. Per-module engine
     * versions (e.g. the MarianMT version baked into a module's metadata.json)
     * are not meaningful for that comparison.
     */
    private fun upsertLanguageModel(
        metadata: LanguageModelMetadata,
        bundleVersion: String,
    ) {
        val languageModel =
            mapLanguageMetadataToLanguageModelDatabaseModel(
                data = metadata,
                version = bundleVersion.ifBlank { metadata.version },
            )
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
                    languageBundleMetadata = metadata.bundle,
                    languageModelMetadata = it,
                )
                upsertLanguageModel(
                    metadata = it,
                    bundleVersion = metadata.bundle.version,
                )
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
            database.languages
                .getBySourceIncludingBidirecional(source = language.normalizedCode)
                .executeAsList()
                .map { mapLanguageDatabaseModelToLanguagePair(it) }
        val languageIds = languagePairs.map { it.id }

        database.languageModels
            .getAllByLanguageIds(languageIds = languageIds)
            .executeAsList()
            .map { it.path.toPath().toNioPath() }
            .distinct()
            .forEach { it.deleteRecursively() }

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
        languagePair: LanguagePair,
        bidirectional: Boolean,
    ): List<LanguagePair> {
        val languagePairs =
            listOfNotNull(
                languagePair,
                if (bidirectional) {
                    LanguagePair(
                        source = languagePair.target,
                        target = languagePair.source,
                    )
                } else {
                    null
                },
            )
        val languageIds = languagePairs.map { it.id }

        database.languageModels
            .getAllByLanguageIds(languageIds = languageIds)
            .executeAsList()
            .map {
                it.path
                    .toPath()
                    .toNioPath()
                    .parent
            }.distinct()
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
            bidirectional = data.bidirectional,
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
        languageBundleMetadata: LanguageBundleMetadata,
        languageModelMetadata: LanguageModelMetadata,
    ): LanguageDatabaseModel {
        val source = Language.fromIsoCode(languageModelMetadata.sourceLanguage)
        val target = Language.fromIsoCode(languageModelMetadata.targetLanguage)
        val pair =
            LanguagePair(
                source = source,
                target = target,
            )

        return LanguageDatabaseModel(
            id = pair.id,
            source = source.normalizedCode,
            target = target.normalizedCode,
            bidirectional = languageBundleMetadata.bidirectional,
        )
    }

    /**
     * Maps a [LanguageModelMetadata] to a [LanguageModelDatabaseModel].
     * @param data The language metadata to map.
     */
    private fun mapLanguageMetadataToLanguageModelDatabaseModel(
        data: LanguageModelMetadata,
        version: String = data.version,
    ): LanguageModelDatabaseModel {
        val source = Language.fromIsoCode(data.sourceLanguage)
        val target = Language.fromIsoCode(data.targetLanguage)
        val pair =
            LanguagePair(
                source = source,
                target = target,
            )

        return LanguageModelDatabaseModel(
            languageId = pair.id,
            baseModel = data.baseModel,
            path = data.root?.absolutePathString() ?: "",
            version = version,
        )
    }

    /**
     * Maps a [LanguageModelDatabaseModel] to a [LanguageModel].
     *
     * The exposed [LanguageModel.version] is the installed bundle version,
     * which is what the external catalog compares against in
     * [app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository.getDefinitionsByState].
     * The bundle version is stored at install time; installs predating that
     * still carry the per-module engine version, so the bundle's own
     * metadata.json next to the module directory takes precedence when it
     * exists.
     *
     * @param data The language model database model to map.
     * @return The mapped language model files.
     */
    private fun mapLanguageModelDatabaseModelToLanguageModelFiles(data: LanguageModelDatabaseModel?): LanguageModel? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            val model = LanguageModel.load(path = path)
            return model.copy(version = resolveInstalledVersion(path, data.version, model.version))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load language model files")
            return null
        }
    }

    /**
     * Resolves the version exposed for an installed language model. Order of
     * precedence: the extracted bundle's metadata.json (ground truth for the
     * installed bundle, heals installs that predate bundle-version storage),
     * the version column stored at install time, then the per-module metadata
     * version as a legacy fallback.
     */
    private fun resolveInstalledVersion(
        modulePath: java.nio.file.Path,
        storedVersion: String,
        moduleVersion: String,
    ): String {
        val bundleVersion = loadBundleVersion(modulePath.parent)

        return when {
            !bundleVersion.isNullOrBlank() -> bundleVersion
            storedVersion.isNotBlank() -> storedVersion
            else -> moduleVersion
        }
    }

    /**
     * Reads the bundle version from the metadata.json at the root of the
     * extracted bundle (the parent of a module's root directory).
     */
    private fun loadBundleVersion(bundlePath: java.nio.file.Path?): String? {
        val metadataFile =
            bundlePath
                ?.let { File(it.toFile(), "metadata.json") }
                ?.takeIf { it.exists() }

        val version =
            try {
                metadataFile?.let {
                    _serializer.decodeFromString<LanguageBundleMetadata>(it.readText()).version
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to read bundle metadata at $bundlePath")
                null
            }

        return version?.takeIf { it.isNotBlank() }
    }

    /**
     * Maps a [String] iso code to a [Language].
     * @param data The language to map.
     * @return The mapped language.
     */
    private fun mapSingleLanguageDatabaseModelToLanguage(data: String): Language = Language(locale = localeForLanguageCode(data))

    /**
     * Maps a [LanguageDatabaseModel] to a [LanguagePair].
     * @param data The language database model to map.
     * @return The mapped language pair.
     */
    private fun mapLanguageDatabaseModelToLanguagePair(data: LanguageDatabaseModel): LanguagePair {
        val source = Language(locale = localeForLanguageCode(data.source))
        val target = Language(locale = localeForLanguageCode(data.target))

        return LanguagePair(
            source = source,
            target = target,
        )
    }

    companion object {
        private val TAG: String = LanguageDatabaseRepository::class.java.simpleName

        private val _serializer =
            Json {
                ignoreUnknownKeys = true
            }
    }
}
