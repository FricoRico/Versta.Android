package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.core.entity.ExternalVoiceModelDefinitions
import app.versta.translate.core.entity.ExternalVoiceModels
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.Flow

interface ExternalVoiceModelsRepository {
    /**
     * Returns a flow of [ExternalVoiceModelDefinitions] that contains the definitions of the
     * external text-to-speech models.
     */
    fun getDefinitions(): Flow<ExternalVoiceModelDefinitions>

    /**
     * Returns a flow of [ExternalVoiceModelDefinitions] that contains the definition of the
     * external language model for the given [id].
     */
    fun getDefinition(id: String): Flow<ExternalVoiceModelDefinition>

    /**
     * Returns a flow of [VoiceWithModelFiles] that contains the definition of the
     * external text-to-speech models. These definitions are filtered by the state of the imported
     * text-to-speech models.
     */
    fun getDefinitionsByState(imported: Flow<List<VoiceWithModelFiles>>): Flow<ExternalVoiceModels>
}