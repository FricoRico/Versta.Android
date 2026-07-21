package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.SpeechRecognitionModel
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
import kotlinx.coroutines.flow.Flow

interface SpeechRecognitionRepository {
    fun getSpeechRecognitionModel(id: String): SpeechRecognitionWithFiles?

    fun getSpeechRecognitionModels(): Flow<List<SpeechRecognitionWithFiles>>

    fun upsertSpeechRecognitionModel(metadata: SpeechRecognitionModel)

    fun deleteSpeechRecognitionModel(id: String)
}
