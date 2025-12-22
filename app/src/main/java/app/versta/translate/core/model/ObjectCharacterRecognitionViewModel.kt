package app.versta.translate.core.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.DOWNLOAD_OBJECT_CHARACTER_RECOGNITION_STATUS_INTENT
import app.versta.translate.adapter.inbound.DownloadObjectCharacterRecognitionWorker
import app.versta.translate.adapter.outbound.ExternalObjectCharacterRecognitionModelsRepository
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionDownloadTask
import app.versta.translate.core.entity.ExternalObjectCharacterRecognitionModelDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ObjectCharacterRecognitionViewModel(
    context: Context,
    private val objectCharacterRecognitionRepository: ObjectCharacterRecognitionRepository,
    private val externalObjectCharacterRecognitionModelsRepository: ExternalObjectCharacterRecognitionModelsRepository
) : ViewModel() {

    val ocrModels = externalObjectCharacterRecognitionModelsRepository.getDefinitions().distinctUntilChanged()

    private val importedDetectors = objectCharacterRecognitionRepository.getObjectCharacterRecognitionDetectors().distinctUntilChanged()
    private val importedRecognizers = objectCharacterRecognitionRepository.getObjectCharacterRecognitionRecognizers().distinctUntilChanged()

    val ocrModelsByState = externalObjectCharacterRecognitionModelsRepository.getDefinitionsByState(
        importedDetectors,
        importedRecognizers
    ).distinctUntilChanged()

    private val downloadManager = DownloadManager<ExternalObjectCharacterRecognitionDownloadTask>(
        context = context,
        statusIntentAction = DOWNLOAD_OBJECT_CHARACTER_RECOGNITION_STATUS_INTENT,
        workerClass = DownloadObjectCharacterRecognitionWorker::class.java
    )
    val downloadTasks: StateFlow<List<ExternalObjectCharacterRecognitionDownloadTask>> =
        downloadManager.downloadTasks.asStateFlow()

    /**
     * Returns a flow of [ExternalObjectCharacterRecognitionModelDefinition] that contains the definitions of the
     * external OCR model for the given [id].
     */
    fun getOcrModelDefinition(id: String): Flow<ExternalObjectCharacterRecognitionModelDefinition> {
        return externalObjectCharacterRecognitionModelsRepository.getDefinition(id).distinctUntilChanged()
    }

    /**
     * Queues a download for the given OCR model.
     */
    fun queueDownload(model: ExternalObjectCharacterRecognitionModelDefinition) {
        val task = ExternalObjectCharacterRecognitionDownloadTask(
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
     * Deletes the OCR model with the given ID.
     */
    fun deleteOcrModel(id: String) {
        viewModelScope.launch {
            objectCharacterRecognitionRepository.deleteObjectCharacterRecognitionDetector(id)
            objectCharacterRecognitionRepository.deleteObjectCharacterRecognitionRecognizer(id)
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

