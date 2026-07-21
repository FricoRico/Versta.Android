package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalSpeechRecognitionModelDefinition
import app.versta.translate.core.entity.ExternalSpeechRecognitionModelWithState
import app.versta.translate.core.entity.ExternalSpeechRecognitionModels
import app.versta.translate.core.entity.SpeechRecognitionArchitecture
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ExternalSpeechRecognitionModelsMemoryRepository : ExternalSpeechRecognitionModelsRepository {
    private val _definitions = listOf(
        ExternalSpeechRecognitionModelDefinition(
            id = "whisper-base-en",
            name = "Whisper Base (English)",
            baseModel = "openai/whisper-base.en",
            architectures = listOf(SpeechRecognitionArchitecture.Whisper),
            languages = listOf("en"),
            size = 147322514,
            version = "v1.0.0",
            bundle = "https://mock.versta.app/whisper-base-en-bundle.tar.gz",
            checksum = "https://mock.versta.app/whisper-base-en-bundle.tar.gz.sha256",
        )
    )

    override fun getDefinitions(): Flow<List<ExternalSpeechRecognitionModelDefinition>> {
        return flowOf(_definitions)
    }

    override fun getDefinition(id: String): Flow<ExternalSpeechRecognitionModelDefinition> {
        return flowOf(_definitions.first { it.id == id })
    }

    override fun getDefinitionsByState(
        imported: Flow<List<SpeechRecognitionWithFiles>>,
    ): Flow<ExternalSpeechRecognitionModels> {
        return flowOf(
            ExternalSpeechRecognitionModels(
                installed = emptyList(),
                updates = emptyList(),
                available = _definitions.map { ExternalSpeechRecognitionModelWithState(definition = it) },
            )
        )
    }
}
