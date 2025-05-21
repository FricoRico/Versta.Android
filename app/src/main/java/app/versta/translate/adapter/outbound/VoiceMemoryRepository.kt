package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.core.entity.VoiceModelVoiceFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class VoiceMemoryRepository : VoiceRepository {
    private val _mockPath = Path("")

    private val _textToSpeechModels = mutableMapOf(
        "kokoro" to VoiceWithModelFiles(
            id = "kokoro",
            baseModel = "hexgrad/Kokoro-82M",
            path = _mockPath,
            architectures = listOf(VoiceModelArchitecture.StyleTTS2),
            version = "v1.0.0",
            inference = VoiceModelInferenceFiles(
                model = _mockPath
            ),
            voices = VoiceModelVoiceFiles(),
        )
    )

    /**
     * Gets the voice model available in the repository.
     */
    override fun getVoiceModel(id: String) = _textToSpeechModels[id]

    /**
     * Gets the voice models available in the repository.
     */
    override fun getVoiceModels(): Flow<List<VoiceWithModelFiles>> {
        return flowOf(_textToSpeechModels.values.toList())
    }

    /**
     * Gets the voice models available in the repository by language.
     * @param language The language to filter the voice models.
     */
    override fun getVoiceModelsByLanguage(language: Language) = _textToSpeechModels.values.first()

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
        _textToSpeechModels.remove(id)
    }
}