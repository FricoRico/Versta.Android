package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.executeAsListFlow
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath
import timber.log.Timber
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteRecursively
import java.app.versta.translate.database.sqldelight.ObjectCharacterRecognitionDetectorModel as ObjectCharacterRecognitionDetectorDatabaseModel
import java.app.versta.translate.database.sqldelight.ObjectCharacterRecognitionRecognizerModel as ObjectCharacterRecognitionRecognizerDatabaseModel

class ObjectCharacterRecognitionRepositoryDatabaseRepository(
    private val database: DatabaseContainer
) : ObjectCharacterRecognitionRepository {
    /**
     * Gets the object character recognition detector available in the repository.
     */
    override fun getObjectCharacterRecognitionDetector(id: String): ObjectCharacterRecognitionDetectorWithFiles? {
        return mapObjectCharacterRecognitionDetectorDatabaseModelToObjectCharacterRecognitionDetectorFiles(
            database.objectCharacterRecognitionModels.getDetectorById(id).executeAsOneOrNull()
        )
    }

    /**
     * Gets the object character recognition detectors available in the repository.
     */
    override fun getObjectCharacterRecognitionDetectors() = database.objectCharacterRecognitionModels.getAllDetectors().executeAsListFlow()
        .map { it.mapNotNull { detector -> mapObjectCharacterRecognitionDetectorDatabaseModelToObjectCharacterRecognitionDetectorFiles(detector) } }

    /**
     * Gets the object character recognition detector available in the repository by language.
     * @param language The language to filter the object character recognition detectors.
     */
    override fun getObjectCharacterRecognitionDetectorByLanguage(language: Language) = database.objectCharacterRecognitionModels.getAllDetectors().executeAsList()
        .map { mapObjectCharacterRecognitionDetectorDatabaseModelToObjectCharacterRecognitionDetectorFiles(it) }
        .firstOrNull { detector -> detector?.languages?.any { it == language.isoCode || it == "*" } == true }

    /**
     * Inserts or updates the object character recognition detector in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertObjectCharacterRecognitionDetector(metadata: ObjectCharacterRecognitionDetectorModel) {
        val data = mapObjectCharacterRecognitionDetectorMetadataToObjectCharacterRecgonitionDetectorDatabaseModel(
            data = metadata.model
        )

        upsertObjectCharacterRecognitionDetector(data)
    }

    /**
     * Deletes the object character recognition recognizer in the repository.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteObjectCharacterRecognitionDetector(id: String) {
        database.objectCharacterRecognitionModels.getDetectorById(id = id).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.objectCharacterRecognitionModels.deleteDetectorById(id = id)
    }

    /**
     * Gets the object character recognizer available in the repository.
     */
    override fun getObjectCharacterRecognitionRecognizer(id: String): ObjectCharacterRecognitionRecognizerWithFiles? {
        return mapObjectCharacterRecognitionRecognizerDatabaseModelToObjectCharacterRecognitionRecognizerFiles(
            database.objectCharacterRecognitionModels.getRecognizerById(id).executeAsOneOrNull()
        )
    }

    /**
     * Gets the object character recognizers available in the repository.
     */
    override fun getObjectCharacterRecognitionRecognizers() = database.objectCharacterRecognitionModels.getAllRecognizers().executeAsListFlow()
        .map { it.mapNotNull { recognizer -> mapObjectCharacterRecognitionRecognizerDatabaseModelToObjectCharacterRecognitionRecognizerFiles(recognizer) } }

    /**
     * Gets the object character recognizer available in the repository by language.
     * @param language The language to filter the object character recognizers.
     */
    override fun getObjectCharacterRecognizerByLanguage(language: Language): ObjectCharacterRecognitionRecognizerWithFiles? {
        val models = database.objectCharacterRecognitionModels.getAllRecognizers().executeAsList().map { mapObjectCharacterRecognitionRecognizerDatabaseModelToObjectCharacterRecognitionRecognizerFiles(it) }

        return models.firstOrNull { model ->
            model?.languages?.any { it == language.isoCode } == true
        } ?: models.firstOrNull { model ->
            model?.languages?.any { it == "*" } == true
        }
    }

    /**
     * Inserts or updates the object character recognition recognizer in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertObjectCharacterRecognitionRecognizer(metadata: ObjectCharacterRecognitionRecognizerModel) {
        val data = mapObjectCharacterRecognitionRecognizerMetadataToObjectCharacterRecgonitionRecognizerDatabaseModel(
            data = metadata.model
        )

        upsertObjectCharacterRecognitionRecognizer(data)
    }

    /**
     * Deletes the object character recognizer in the repository.
     */
    @OptIn(ExperimentalPathApi::class)
    override fun deleteObjectCharacterRecognitionRecognizer(id: String) {
        database.objectCharacterRecognitionModels.getRecognizerById(id = id).executeAsList()
            .map { it.path.toPath().toNioPath() }.distinct()
            .forEach { it.deleteRecursively() }

        database.objectCharacterRecognitionModels.deleteRecognizerById(id = id)
    }

    /**
     * Inserts or updates the object character recognition detector in the repository.
     * @param data The data to insert or update.
     */
    private fun upsertObjectCharacterRecognitionDetector(data: ObjectCharacterRecognitionDetectorDatabaseModel) {
        database.objectCharacterRecognitionModels.upsertDetector(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures,
            path = data.path,
            version = data.version
        )
    }

    private fun mapObjectCharacterRecognitionDetectorMetadataToObjectCharacterRecgonitionDetectorDatabaseModel(
        data: ObjectCharacterRecognitionDetectorMetadata
    ): ObjectCharacterRecognitionDetectorDatabaseModel {
        return ObjectCharacterRecognitionDetectorDatabaseModel(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            version = data.version
        )
    }

    private fun mapObjectCharacterRecognitionDetectorDatabaseModelToObjectCharacterRecognitionDetectorFiles(data: ObjectCharacterRecognitionDetectorDatabaseModel?): ObjectCharacterRecognitionDetectorWithFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return ObjectCharacterRecognitionDetectorWithFiles.load(id = data.id, path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load object character recognition detector model files")
            return null
        }
    }


    /**
     * Inserts or updates the object character recognition detector in the repository.
     * @param data The data to insert or update.
     */
    private fun upsertObjectCharacterRecognitionRecognizer(data: ObjectCharacterRecognitionRecognizerDatabaseModel) {
        database.objectCharacterRecognitionModels.upsertRecognizer(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures,
            path = data.path,
            version = data.version
        )
    }

    private fun mapObjectCharacterRecognitionRecognizerMetadataToObjectCharacterRecgonitionRecognizerDatabaseModel(
        data: ObjectCharacterRecognitionRecognizerMetadata
    ): ObjectCharacterRecognitionRecognizerDatabaseModel {
        return ObjectCharacterRecognitionRecognizerDatabaseModel(
            id = data.id,
            baseModel = data.baseModel,
            architectures = data.architectures.map { it.value },
            path = data.root?.absolutePathString() ?: "",
            version = data.version
        )
    }

    private fun mapObjectCharacterRecognitionRecognizerDatabaseModelToObjectCharacterRecognitionRecognizerFiles(data: ObjectCharacterRecognitionRecognizerDatabaseModel?): ObjectCharacterRecognitionRecognizerWithFiles? {
        if (data == null) {
            return null
        }

        val path = data.path.toPath().toNioPath()

        try {
            return ObjectCharacterRecognitionRecognizerWithFiles.load(id = data.id, path = path)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load object character recognition recognizer model files")
            return null
        }
    }

    companion object {
        private val TAG: String =
            ObjectCharacterRecognitionRepositoryDatabaseRepository::class.java.simpleName
    }
}