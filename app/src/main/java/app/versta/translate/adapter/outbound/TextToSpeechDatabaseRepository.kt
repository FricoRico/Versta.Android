package app.versta.translate.adapter.outbound

import app.cash.sqldelight.coroutines.asFlow
import app.versta.translate.core.entity.TextToSpeechModel
import app.versta.translate.core.entity.TextToSpeechModelFiles
import app.versta.translate.core.entity.TextToSpeechModelMetadata
import app.versta.translate.database.DatabaseContainer
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.TextToSpeechModel as TextToSpeechModelDatabaseModel

// Hardcoded for now, as there is only one model available
internal const val MODEL_ID = "kokoro"

class TextToSpeechDatabaseRepository(
    private val database: DatabaseContainer,
) : TextToSpeechRepository {
    /**
     * Gets the text to speech model available in the repository.
     */
    override fun getTextToSpeechModel() =
        database.textToSpeechModels.getById(MODEL_ID).asFlow().map {
            mapTextToSpeechModelDatabaseModelToTextToSpeechModelFiles(it.executeAsOneOrNull())
        }

    /**
     * Inserts or updates the text to speech model in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertTextToSpeechModel(metadata: TextToSpeechModel) {
        val textToSpeechModel =
            mapTextToSpeechMetadataToTextToSpeechModelDatabaseModel(metadata.model)

        upsertTextToSpeechDatabaseModel(data = textToSpeechModel)
    }

    /**
     * Deletes the text to speech model in the repository.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteTextToSpeechModel() {
        database.textToSpeechModels.getById(id = MODEL_ID).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.textToSpeechModels.deleteById(id = MODEL_ID)
    }

    /**
     * Inserts a [TextToSpeechModelDatabaseModel] into the repository, ignoring if it already exists.
     * @param data The data to insert.
     */
    private fun upsertTextToSpeechDatabaseModel(data: TextToSpeechModelDatabaseModel) {
        database.textToSpeechModels.upsert(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures,
            path = data.path,
            version = data.version
        )
    }

    /**
     * Maps a [TextToSpeechModelMetadata] to a [TextToSpeechModelDatabaseModel].
     * @param data The metadata to map.
     * @return The mapped database model.
     */
    private fun mapTextToSpeechMetadataToTextToSpeechModelDatabaseModel(
        data: TextToSpeechModelMetadata
    ): TextToSpeechModelDatabaseModel {
        return TextToSpeechModelDatabaseModel(
            id = MODEL_ID,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            version = data.version
        )
    }

    /**
     * Maps a [TextToSpeechVoiceDatabaseModel] to a [TextToSpeechModelFiles].
     * @param data The metadata to map.
     * @return The mapped database model.
     */
    private fun mapTextToSpeechModelDatabaseModelToTextToSpeechModelFiles(data: TextToSpeechModelDatabaseModel?): TextToSpeechModelFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return TextToSpeechModelFiles.load(path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load text to speech model files")
            return null
        }
    }

    companion object {
        private val TAG: String = TextToSpeechDatabaseRepository::class.java.simpleName
    }
}