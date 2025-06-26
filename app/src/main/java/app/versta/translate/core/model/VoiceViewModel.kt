package app.versta.translate.core.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.DOWNLOAD_VOICE_STATUS_INTENT
import app.versta.translate.adapter.inbound.DownloadVoiceWorker
import app.versta.translate.adapter.outbound.ExternalVoiceModelsRepository
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalVoiceDownloadTask
import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class VoiceViewModel(
    context: Context,
    private val voiceRepository: VoiceRepository,
    private val externalVoiceModelsRepository: ExternalVoiceModelsRepository
) : ViewModel() {

    val voiceModels = externalVoiceModelsRepository.getDefinitions().distinctUntilChanged()
    val importedVoices = voiceRepository.getVoiceModels().distinctUntilChanged()
    val voiceByState = externalVoiceModelsRepository.getDefinitionsByState(importedVoices)

    private val downloadManager = DownloadManager<ExternalVoiceDownloadTask>(
        context = context,
        statusIntentAction = DOWNLOAD_VOICE_STATUS_INTENT,
        workerClass = DownloadVoiceWorker::class.java
    )
    val downloadTasks: StateFlow<List<ExternalVoiceDownloadTask>> =
        downloadManager.downloadTasks.asStateFlow()

    /**
     * Returns a flow of [ExternalVoiceModelDefinition] that contains the definitions of the
     * external voice model for the given [id].
     */
    fun getVoiceModelDefinition(id: String): Flow<ExternalVoiceModelDefinition> {
        return externalVoiceModelsRepository.getDefinition(id).distinctUntilChanged()
    }

    /**
     * Queues a download for the given voice model.
     */
    fun queueDownload(model: ExternalVoiceModelDefinition) {
        val task = ExternalVoiceDownloadTask(
            model = model,
            status = DownloadStatus.Queued
        )
        downloadManager.queueDownload(task)
    }

    /**
     * Cancels all pending downloads.
     */
    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    /**
     * Deletes the voice model with the given ID.
     */
    fun deleteVoiceModel(id: String) {
        viewModelScope.launch {
            voiceRepository.deleteVoiceModel(id)
        }
    }

    init {
        downloadManager.register()
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.unregister()
    }
}