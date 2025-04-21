package app.versta.translate.adapter.outbound

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.db.AfterVersion
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceInferenceFilesMetadata
import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceModelFilesMetadata
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.core.entity.VoiceModelMetadata
import app.versta.translate.database.Database
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.Voice as VoiceDatabaseModel
import java.app.versta.translate.database.sqldelight.VoiceModel as VoiceModelDatabaseModel

class VoiceDatabaseRepository(
    private val database: DatabaseContainer,
) : VoiceRepository {
    /**
     * Gets the text to speech model available in the repository.
     */
    override fun getVoiceModel(id: String) =
        mapVoiceModelDatabaseModelToVoiceModelFiles(
            database.voiceModels.getById(id).executeAsOneOrNull()
        )

    /**
     * Gets the text to speech models available in the repository.
     */
    override fun getVoiceModels() =
        database.voiceModels.getAll().executeAsListFlow()
            .map { it.mapNotNull { voice -> mapVoiceModelDatabaseModelToVoiceModelFiles(voice) } }

    /**
     * Gets the voice models available in the repository by language.
     * @param language The language to filter the voice models.
     */
    override fun getVoiceModelsByLanguage(language: Language): VoiceWithModelFiles? {
        val voice =
            database.voices.getByLanguage(language.isoCode).executeAsOneOrNull() ?: return null

        return mapVoiceModelDatabaseModelToVoiceModelFiles(
            database.voiceModels.getById(voice.modelId).executeAsOneOrNull()
        )
    }

    /**
     * Inserts or updates the text to speech model in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertVoiceModel(metadata: VoiceModel) {
        val voiceModel =
            mapVoiceMetadataToVoiceModelDatabaseModel(metadata.id, metadata.model)
        val voices = mapVoiceMetadataToVoiceDatabaseModel(metadata.id, metadata.model)

        upsertVoiceDatabaseModel(data = voiceModel)
        insertOrIgnoreVoices(data = voices)
    }

    /**
     * Deletes the text to speech model in the repository.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteVoiceModel(id: String) {
        database.voiceModels.getById(id = id).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.voiceModels.deleteById(id = id)
        database.voices.deleteById(modelId = id)
    }

    /**
     * Inserts a [VoiceModelDatabaseModel] into the repository, ignoring if it already exists.
     * @param data The data to insert.
     */
    private fun upsertVoiceDatabaseModel(data: VoiceModelDatabaseModel) {
        database.voiceModels.upsert(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures,
            path = data.path,
            version = data.version
        )
    }

    /**
     * Inserts a list of [VoiceDatabaseModel] into the repository, ignoring if it already exists.
     * @param data The data to insert.
     */
    private fun insertOrIgnoreVoices(data: List<VoiceDatabaseModel>) {
        database.voices.transaction {
            data.forEach {
                database.voices.insertOrIgnore(
                    modelId = it.modelId,
                    language = it.language,
                    gender = it.gender
                )
            }
        }
    }

    /**
     * Maps a [VoiceModelMetadata] to a [VoiceModelDatabaseModel].
     * @param data The metadata to map.
     * @return The mapped database model.
     */
    private fun mapVoiceMetadataToVoiceModelDatabaseModel(
        id: String,
        data: VoiceModelMetadata
    ): VoiceModelDatabaseModel {
        return VoiceModelDatabaseModel(
            id = id,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            version = data.version
        )
    }

    /**
     * Maps a [VoiceModelDatabaseModel] to a [VoiceWithModelFiles].
     * @param data The metadata to map.
     * @return The mapped database model.
     */
    private fun mapVoiceModelDatabaseModelToVoiceModelFiles(data: VoiceModelDatabaseModel?): VoiceWithModelFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return VoiceWithModelFiles.load(id = data.id, path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load text to speech model files")
            return null
        }
    }

    /**
     * Maps a [VoiceModelMetadata] to a list of [VoiceDatabaseModel].
     * @param data The metadata to map.
     * @return The mapped database model.
     */
    private fun mapVoiceMetadataToVoiceDatabaseModel(
        modelId: String,
        data: VoiceModelMetadata
    ): List<VoiceDatabaseModel> {
        return data.files.voices.map {
            val file = Path(it).fileName.toString()

            val language = when {
                file.startsWith("a") -> "en"
                file.startsWith("b") -> "en"
                file.startsWith("j") -> "ja"
                file.startsWith("f") -> "fr"
                file.startsWith("e") -> "es"
                file.startsWith("h") -> "hi"
                file.startsWith("i") -> "it"
                file.startsWith("z") -> "zh"
                file.startsWith("p") -> "pt"
                else -> throw Exception("Determining language from file name: $it")
            }

            val gender = when {
                file.startsWith("f", 1) -> "female"
                file.startsWith("m", 1) -> "male"
                else -> throw Exception("Determining gender from file name: $it")
            }

            VoiceDatabaseModel(
                modelId = modelId,
                language = language,
                gender = gender
            )
        }
    }

    init {
        val migrationScope = CoroutineScope(Dispatchers.Default)

        Database.Schema.migrate(
            driver = database.driver,
            oldVersion = 3,
            newVersion = Database.Schema.version,
            AfterVersion(4) {
                migrationScope.launch {
                    getVoiceModels().collect { models ->
                        models.forEach { model ->
                            val metadata = VoiceModelMetadata(
                                version = model.version,
                                baseModel = model.baseModel,
                                architectures = model.architectures,
                                files = VoiceModelFilesMetadata(
                                    inference = VoiceInferenceFilesMetadata(
                                        model = model.inference.model.absolutePathString()
                                    ),
                                    voices = model.voices.map { toString() }
                                ),
                            )

                            Timber.tag(TAG).d("Running migration")
                            insertOrIgnoreVoices(
                                mapVoiceMetadataToVoiceDatabaseModel(
                                    model.id,
                                    metadata
                                )
                            )
                        }
                    }
                }
            }
        )
    }

    companion object {
        private val TAG: String = VoiceDatabaseRepository::class.java.simpleName
    }
}