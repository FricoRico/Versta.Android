package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.SpeechRecognitionMetadata
import app.versta.translate.core.entity.SpeechRecognitionModel
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.SpeechRecognitionModel as SpeechRecognitionDatabaseModel

class SpeechRecognitionDatabaseRepository(
    private val database: DatabaseContainer
) : SpeechRecognitionRepository {
    override fun getSpeechRecognitionModel(id: String): SpeechRecognitionWithFiles? {
        return mapSpeechRecognitionDatabaseModelToSpeechRecognitionFiles(
            database.speechRecognitionModels.getById(id).executeAsOneOrNull()
        )
    }

    override fun getSpeechRecognitionModels() = database.speechRecognitionModels.getAll().executeAsListFlow()
        .map { it.mapNotNull { model -> mapSpeechRecognitionDatabaseModelToSpeechRecognitionFiles(model) } }

    override fun upsertSpeechRecognitionModel(metadata: SpeechRecognitionModel) {
        val data = mapSpeechRecognitionMetadataToSpeechRecognitionDatabaseModel(
            data = metadata.model
        )

        upsertSpeechRecognitionModel(data)
    }

    @OptIn(ExperimentalPathApi::class)
    override fun deleteSpeechRecognitionModel(id: String) {
        database.speechRecognitionModels.getById(id = id).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.speechRecognitionModels.deleteById(id = id)
    }

    private fun upsertSpeechRecognitionModel(data: SpeechRecognitionDatabaseModel) {
        database.speechRecognitionModels.upsert(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures,
            path = data.path,
            version = data.version
        )
    }

    private fun mapSpeechRecognitionMetadataToSpeechRecognitionDatabaseModel(
        data: SpeechRecognitionMetadata
    ): SpeechRecognitionDatabaseModel {
        return SpeechRecognitionDatabaseModel(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            version = data.version
        )
    }

    private fun mapSpeechRecognitionDatabaseModelToSpeechRecognitionFiles(data: SpeechRecognitionDatabaseModel?): SpeechRecognitionWithFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return SpeechRecognitionWithFiles.load(id = data.id, path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load speech recognition model files")
            return null
        }
    }

    companion object {
        private val TAG: String =
            SpeechRecognitionDatabaseRepository::class.java.simpleName
    }
}
