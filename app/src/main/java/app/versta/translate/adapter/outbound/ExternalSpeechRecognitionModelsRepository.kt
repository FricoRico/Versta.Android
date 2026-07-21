package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalSpeechRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalSpeechRecognitionModels
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
import kotlinx.coroutines.flow.Flow

interface ExternalSpeechRecognitionModelsRepository {
    fun getDefinitions(): Flow<List<ExternalSpeechRecognitionModelDefinition>>

    fun getDefinition(id: String): Flow<ExternalSpeechRecognitionModelDefinition>

    fun getDefinitionsByState(
        imported: Flow<List<SpeechRecognitionWithFiles>>,
    ): Flow<ExternalSpeechRecognitionModels>
}
