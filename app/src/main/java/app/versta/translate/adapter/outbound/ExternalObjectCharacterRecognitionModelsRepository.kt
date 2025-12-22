package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModels
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.flow.Flow

interface ExternalObjectCharacterRecognitionModelsRepository {
    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definitions of the
     * external OCR models.
     */
    fun getDefinitions(): Flow<List<ExternalObjectCharacterRecognitionModelDefinition>>

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definition of the
     * external OCR model for the given [id].
     */
    fun getDefinition(id: String): Flow<ExternalObjectCharacterRecognitionModelDefinition>

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModels] that contains the definitions of the external
     * OCR models. These definitions are filtered by the state of the imported OCR models.
     */
    fun getDefinitionsByState(
        importedDetectors: Flow<List<ObjectCharacterRecognitionDetectorWithFiles>>,
        importedRecognizers: Flow<List<ObjectCharacterRecognitionRecognizerWithFiles>>
    ): Flow<ExternalObjectCharacterRecognitionModels>
}

