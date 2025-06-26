package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceModelTokenizerFiles
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.core.entity.VoiceModelVoiceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class VoiceMemoryRepository : VoiceRepository {
    private val _mockPath = Path("")

//    private val _textToSpeechModels = mutableMapOf(
//        "versta-tts-data" to TextToSpeechDataWithFiles(
//            id = "versta-tts-data",
//            path = _mockPath,
//            version = "v1.0.0",
//            files = TextToSpeechDataFiles(
//                espeak = _mockPath,
//                openJTalk = _mockPath,
//            )
//        )
//    )

    private val _voiceModels = mutableMapOf(
        "kokoro" to VoiceWithModelFiles(
            id = "kokoro",
            baseModel = "hexgrad/Kokoro-82M",
            path = _mockPath,
            architectures = listOf(VoiceModelArchitecture.StyleTTS2),
            version = "v1.0.0",
            inference = VoiceModelInferenceFiles(
                model = _mockPath
            ),
            tokenizer = VoiceModelTokenizerFiles(
                vocabulary = _mockPath
            ),
            voices = VoiceModelVoiceFiles(),
        )
    )

    /**
     * Gets the voice model available in the repository.
     */
    override fun getVoiceModel(id: String) = _voiceModels[id]

    /**
     * Gets the voice models available in the repository.
     */
    override fun getVoiceModels(): Flow<List<VoiceWithModelFiles>> {
        return flowOf(_voiceModels.values.toList())
    }

//    /**
//     * Gets the text to speech data available in the repository.
//     */
//    override fun getTextToSpeechData(): Flow<TextToSpeechDataWithFiles?> {
//        return flowOf(_textToSpeechModels.values.firstOrNull())
//    }

    /**
     * Gets the voice models available in the repository by language.
     * @param language The language to filter the voice models.
     */
    override fun getVoiceModelsByLanguage(language: Language) = _voiceModels.values.first()

    /**
     * Inserts or updates the voice model in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertVoiceModel(metadata: VoiceModel) {
        return
    }

    /**
     * Deletes the voice model in the repository.
     */
    override fun deleteVoiceModel(id: String) {
        _voiceModels.remove(id)
    }
}