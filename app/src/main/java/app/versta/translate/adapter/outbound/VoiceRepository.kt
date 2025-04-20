package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.Flow

interface VoiceRepository {
    /**
     * Gets the voice model available in the repository.
     */
    fun getVoiceModel(id: String): Flow<VoiceWithModelFiles?>

    /**
     * Gets the voice models available in the repository.
     */
    fun getVoiceModels(): Flow<List<VoiceWithModelFiles>>

    /**
     * Inserts or updates the voice model in the repository.
     * @param metadata The metadata to insert or update.
     */
    fun upsertVoiceModel(metadata: VoiceModel)

    /**
     * Deletes the voice model in the repository.
     */
    fun deleteVoiceModel(id: String)
}