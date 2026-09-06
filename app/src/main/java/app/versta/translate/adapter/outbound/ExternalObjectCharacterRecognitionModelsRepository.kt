package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModels
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelWithState
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleWithFiles
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
        importedModules: Flow<List<ObjectCharacterRecognitionModuleWithFiles>>
    ): Flow<ExternalObjectCharacterRecognitionModels>
}

