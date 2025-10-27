package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.ObjectCharacterRecognitionArchitecture
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorInferenceFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerInferenceFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerTokenizerFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class ObjectCharacterRecognitionRepositoryMemoryRepository : ObjectCharacterRecognitionRepository {
    private val _mockPath = Path("")

    private val _detectors = mutableMapOf(
        "pp-ocr-det*" to ObjectCharacterRecognitionDetectorWithFiles(
            id = "pp-ocr-det",
            path = _mockPath,
            baseModel = "PaddlePaddle/PP-OCRv5_mobile_det",
            architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
            languages = listOf("*"),
            version = "v1.0.0",
            size = 12345678,
            inference = ObjectCharacterRecognitionDetectorInferenceFiles(
                model = _mockPath
            )
        )
    )

    private val _recognizers = mutableMapOf(
        "pp-ocr-rec-latin" to ObjectCharacterRecognitionRecognizerWithFiles(
            id = "pp-ocr-rec-latin",
            path = _mockPath,
            baseModel = "PaddlePaddle/latin_PP-OCRv5_mobile_rec",
            architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
            languages = listOf(
                "ad",
                "ar",
                "at",
                "au",
                "be",
                "bo",
                "br",
                "ca",
                "ch",
                "cl",
                "co",
                "cr",
                "cu",
                "cz",
                "de",
                "dk",
                "do",
                "ec",
                "es",
                "fi",
                "fr",
                "gt",
                "hn",
                "hr",
                "ht",
                "hu",
                "id",
                "ie",
                "it",
                "lu",
                "mx",
                "nl",
                "no",
                "pa",
                "pe",
                "ph",
                "pl",
                "pt",
                "py",
                "se",
                "sv",
                "us",
                "uy",
                "va",
                "ve"
            ),
            version = "v1.0.0",
            inference = ObjectCharacterRecognitionRecognizerInferenceFiles(
                model = _mockPath,
            ),
            tokenizer = ObjectCharacterRecognitionRecognizerTokenizerFiles(
                vocabulary = _mockPath,
            )
        )
    )

    /**
     * Gets the object character recognizer available in the repository.
     */
    override fun getObjectCharacterRecognitionRecognizer(id: String) = _recognizers[id]

    /**
     * Gets the object character recognizers available in the repository.
     */
    override fun getObjectCharacterRecognitionRecognizers(): Flow<List<ObjectCharacterRecognitionRecognizerWithFiles>> {
        return flowOf(_recognizers.values.toList())
    }

    /**
     * Gets the object character recognizer available in the repository by language.
     * @param language The language to filter the object character recognizers.
     */
    override fun getObjectCharacterRecognizerByLanguage(language: Language) =
        _recognizers.values.first()

    /**
     * Inserts or updates the object character recognizer in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertObjectCharacterRecognitionRecognizer(metadata: ObjectCharacterRecognitionRecognizerModel) {
        return
    }

    /**
     * Deletes the object character recognizer in the repository.
     */
    override fun deleteObjectCharacterRecognitionRecognizer(id: String) {
        _recognizers.remove(id)
    }

    /**
     * Gets the object character recognition detector available in the repository.
     */
    override fun getObjectCharacterRecognitionDetector(id: String) = _detectors[id]

    /**
     * Gets the object character recognition detectors available in the repository.
     */
    override fun getObjectCharacterRecognitionDetectors(): Flow<List<ObjectCharacterRecognitionDetectorWithFiles>> {
        return flowOf(_detectors.values.toList())
    }

    /**
     * Gets the object character recognition detector available in the repository by language.
     * @param language The language to filter the object character recognition detectors.
     */
    override fun getObjectCharacterRecognitionDetectorByLanguage(language: Language) =
        _detectors.values.first()

    /**
     * Inserts or updates the object character recognition detector in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertObjectCharacterRecognitionDetector(metadata: ObjectCharacterRecognitionDetectorModel) {
        return
    }

    /**
     * Deletes the object character recognition recognizer in the repository.
     */
    override fun deleteObjectCharacterRecognitionDetector(id: String) {
        _detectors.remove(id)
    }
}