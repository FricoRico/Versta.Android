package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.TextToSpeechInferenceFiles
import app.versta.translate.core.entity.TextToSpeechModel
import app.versta.translate.core.entity.TextToSpeechModelArchitecture
import app.versta.translate.core.entity.TextToSpeechModelFiles
import app.versta.translate.core.entity.TextToSpeechVoicesFiles
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.Path

class TextToSpeechMemoryRepository : TextToSpeechRepository {
    private val _mockPath = Path("")

    private val _textToSpeechModels = TextToSpeechModelFiles(
        baseModel = "hexgrad/Kokoro-82M",
        path = _mockPath,
        architectures = listOf(TextToSpeechModelArchitecture.Kokoro),
        version = "v1.0.0",
        inference = TextToSpeechInferenceFiles(
            model = _mockPath
        ),
        voices = TextToSpeechVoicesFiles(),
    )

    /**
     * Gets the text to speech model available in the repository.
     */
    override fun getTextToSpeechModel() = flowOf(_textToSpeechModels)

    /**
     * Inserts or updates the text to speech model in the repository.
     * @param metadata The metadata to insert or update.
     */
    override fun upsertTextToSpeechModel(metadata: TextToSpeechModel) {
        return
    }

    /**
     * Deletes the text to speech model in the repository.
     */
    override fun deleteTextToSpeechModel() {
        return
    }
}