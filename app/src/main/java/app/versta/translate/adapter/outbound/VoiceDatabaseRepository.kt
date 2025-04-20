package app.versta.translate.adapter.outbound

import app.cash.sqldelight.coroutines.asFlow
import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.core.entity.VoiceModelMetadata
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.VoiceModel as VoiceModelDatabaseModel

class VoiceDatabaseRepository(
    private val database: DatabaseContainer,
) : VoiceRepository {
    /**
     * Gets the text to speech model available in the repository.
     */
    override fun getVoiceModel(id: String) =
        database.voiceModels.getById(id).asFlow().map {
            mapVoiceModelDatabaseModelToVoiceModelFiles(it.executeAsOneOrNull())
        }

    /**
     * Gets the text to speech models available in the repository.
     */
    override fun getVoiceModels() =
        database.voiceModels.getAll().executeAsListFlow()
            .map { it.mapNotNull { voice -> mapVoiceModelDatabaseModelToVoiceModelFiles(voice) } }

    /**
     * Inserts or updates the text to speech model in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertVoiceModel(metadata: VoiceModel) {
        val textToSpeechModel =
            mapVoiceMetadataToVoiceModelDatabaseModel(metadata.id, metadata.model)

        upsertVoiceDatabaseModel(data = textToSpeechModel)
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

    companion object {
        private val TAG: String = VoiceDatabaseRepository::class.java.simpleName
    }
}