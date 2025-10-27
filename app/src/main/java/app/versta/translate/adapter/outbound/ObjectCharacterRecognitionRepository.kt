package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.flow.Flow

interface ObjectCharacterRecognitionRepository {
    /**
     * Gets the object character recognition detector available in the repository.
     */
    fun getObjectCharacterRecognitionDetector(id: String): ObjectCharacterRecognitionDetectorWithFiles?

    /**
     * Gets the object character recognition detectors available in the repository.
     */
    fun getObjectCharacterRecognitionDetectors(): Flow<List<ObjectCharacterRecognitionDetectorWithFiles>>

    /**
     * Gets the object character recognition detector available in the repository by language.
     * @param language The language to filter the object character recognition detectors.
     */
    fun getObjectCharacterRecognitionDetectorByLanguage(language: Language): ObjectCharacterRecognitionDetectorWithFiles?

    /**
     * Inserts or updates the object character recognition detector in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertObjectCharacterRecognitionDetector(metadata: ObjectCharacterRecognitionDetectorModel)

    /**
     * Deletes the object character recognition recognizer in the repository.
     */
    fun deleteObjectCharacterRecognitionDetector(id: String)

    /**
     * Gets the object character recognition recognizer available in the repository.
     */
    fun getObjectCharacterRecognitionRecognizer(id: String): ObjectCharacterRecognitionRecognizerWithFiles?

    /**
     * Gets the object character recognition recognizers available in the repository.
     */
    fun getObjectCharacterRecognitionRecognizers(): Flow<List<ObjectCharacterRecognitionRecognizerWithFiles>>

    /**
     * Gets the object character recognition recognizer available in the repository by language.
     * @param language The language to filter the object character recognition recognizers.
     */
    fun getObjectCharacterRecognizerByLanguage(language: Language): ObjectCharacterRecognitionRecognizerWithFiles?

    /**
     * Inserts or updates the object character recognition recognizer in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertObjectCharacterRecognitionRecognizer(metadata: ObjectCharacterRecognitionRecognizerModel)

    /**
     * Deletes the object character recognition recognizer in the repository.
     */
    fun deleteObjectCharacterRecognitionRecognizer(id: String)
}