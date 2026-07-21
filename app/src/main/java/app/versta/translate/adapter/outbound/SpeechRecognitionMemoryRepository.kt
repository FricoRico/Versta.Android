package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.SpeechRecognitionArchitecture
import app.versta.translate.core.entity.SpeechRecognitionInferenceFiles
import app.versta.translate.core.entity.SpeechRecognitionModel
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class SpeechRecognitionMemoryRepository : SpeechRecognitionRepository {
    private val _mockPath = Path("")

    private val _models = mutableMapOf(
        "whisper-base-en" to SpeechRecognitionWithFiles(
            id = "whisper-base-en",
            path = _mockPath,
            baseModel = "openai/whisper-base.en",
            architectures = listOf(SpeechRecognitionArchitecture.Whisper),
            languages = listOf("en"),
            version = "v1.0.0",
            inference = SpeechRecognitionInferenceFiles(
                model = _mockPath,
                vad = _mockPath,
            )
        )
    )

    override fun getSpeechRecognitionModel(id: String) = _models[id]

    override fun getSpeechRecognitionModels(): Flow<List<SpeechRecognitionWithFiles>> {
        return flowOf(_models.values.toList())
    }

    override fun upsertSpeechRecognitionModel(metadata: SpeechRecognitionModel) {
        return
    }

    override fun deleteSpeechRecognitionModel(id: String) {
        _models.remove(id)
    }
}
