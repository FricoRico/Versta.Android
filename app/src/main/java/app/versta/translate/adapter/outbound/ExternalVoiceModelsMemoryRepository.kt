package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.core.entity.ExternalVoiceModelDefinitions
import app.versta.translate.core.entity.ExternalVoiceModels
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ExternalVoiceModelsMemoryRepository : ExternalVoiceModelsRepository {
    /**
     * Returns a flow of [ExternalVoiceModelDefinitions] that contains the definitions of the
     * external text-to-speech models.
     */
    override fun getDefinitions(): Flow<ExternalVoiceModelDefinitions> = flowOf(listOf(definition))

    /**
     * Returns a flow of [ExternalVoiceModelDefinition] that contains the definition of the
     * external language model for the given [id].
     */
    override fun getDefinition(id: String): Flow<ExternalVoiceModelDefinition> = flowOf(definition)

    /**
     * Returns a flow of [ExternalVoiceModels] that contains the definition of the
     * external text-to-speech models. These definitions are filtered by the state of the imported
     * text-to-speech models.
     */
    override fun getDefinitionsByState(imported: Flow<List<VoiceWithModelFiles>>): Flow<ExternalVoiceModels> =
        flowOf(
            ExternalVoiceModels(
                installed = emptyList(),
                updates = emptyList(),
                available = listOf(definition),
            ),
        )

    private companion object {
        val definition =
            ExternalVoiceModelDefinition(
                id = Supertonic3.ID,
                name = "Supertonic 3",
                baseModel = "Supertone/supertonic-3",
                version = "v2.0.0",
                size = 114532352,
                voices = Supertonic3.VOICES,
                architectures = listOf(VoiceModelArchitecture.Supertonic3),
                bundle = "https://mock.versta.app/supertonic3-bundle.tar.gz",
                checksum = "https://mock.versta.app/supertonic3-bundle.tar.gz.sha256",
            )
    }
}
