package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModels
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelWithState
import app.versta.translate.core.entity.ObjectCharacterRecognitionArchitecture
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ExternalObjectCharacterRecognitionModelsMemoryRepository : ExternalObjectCharacterRecognitionModelsRepository {
    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definitions of the
     * external OCR models.
     */
    override fun getDefinitions(): Flow<List<ExternalObjectCharacterRecognitionModelDefinition>> {
        return flowOf(
            listOf(
                ExternalObjectCharacterRecognitionModelDefinition(
                    id = "paddle-ocr",
                    name = "Paddle OCR",
                    baseModel = "PaddlePaddle/PP-OCRv5_mobile_rec",
                    architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
                    languages = listOf(
                        "en", "nl", "de", "fr", "es", "it", "pt", "ru", "ja", "ko", "zh"
                    ),
                    size = 46810672,
                    version = "v1.0.0",
                    bundle = "https://models.versta.app/object-character-recognition/v1.0.0/paddle-ocr-bundle.tar.gz",
                    checksum = "https://models.versta.app/object-character-recognition/v1.0.0/paddle-ocr-bundle.tar.sha256"
                )
            )
        )
    }

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definition of the
     * external OCR model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalObjectCharacterRecognitionModelDefinition> {
        return flowOf(
            ExternalObjectCharacterRecognitionModelDefinition(
                id = "paddle-ocr",
                name = "Paddle OCR",
                baseModel = "PaddlePaddle/PP-OCRv5_mobile_rec",
                architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
                languages = listOf(
                    "en", "nl", "de", "fr", "es", "it", "pt", "ru", "ja", "ko", "zh"
                ),
                size = 46810672,
                version = "v1.0.0",
                bundle = "https://models.versta.app/object-character-recognition/v1.0.0/paddle-ocr-bundle.tar.gz",
                checksum = "https://models.versta.app/object-character-recognition/v1.0.0/paddle-ocr-bundle.tar.sha256"
            )
        )
    }

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModels] that contains the definitions of the external
     * OCR models. These definitions are filtered by the state of the imported OCR models.
     */
    override fun getDefinitionsByState(
        importedDetectors: Flow<List<ObjectCharacterRecognitionDetectorWithFiles>>,
        importedRecognizers: Flow<List<ObjectCharacterRecognitionRecognizerWithFiles>>
    ): Flow<ExternalObjectCharacterRecognitionModels> {
        return flowOf(
            ExternalObjectCharacterRecognitionModels(
                installed = emptyList(),
                updates = emptyList(),
                available = listOf(
                    ExternalObjectCharacterRecognitionModelWithState(
                        definition = ExternalObjectCharacterRecognitionModelDefinition(
                            id = "paddle-ocr",
                            name = "Paddle OCR",
                            baseModel = "PaddlePaddle/PP-OCRv5_mobile_rec",
                            architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
                            languages = listOf(
                                "en", "nl", "de", "fr", "es", "it", "pt", "ru", "ja", "ko", "zh"
                            ),
                            size = 46810672,
                            version = "v1.0.0",
                            bundle = "https://models.versta.app/object-character-recognition/v1.0.0/paddle-ocr-bundle.tar.gz",
                            checksum = "https://models.versta.app/object-character-recognition/v1.0.0/paddle-ocr-bundle.tar.sha256"
                        )
                    )
                )
            )
        )
    }
}

