package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.TextToSpeechModel
import app.versta.translate.core.entity.TextToSpeechModelFiles
import kotlinx.coroutines.flow.Flow

interface TextToSpeechRepository {
    /**
     * Gets the text to speech model available in the repository.
     */
    fun getTextToSpeechModel(): Flow<TextToSpeechModelFiles?>

    /**
     * Inserts or updates the text to speech model in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertTextToSpeechModel(metadata: TextToSpeechModel)

    /**
     * Deletes the text to speech model in the repository.
     */
    fun deleteTextToSpeechModel()
}