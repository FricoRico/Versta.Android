package app.versta.translate.core.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import app.versta.translate.adapter.inbound.DownloadVoiceWorker
import app.versta.translate.adapter.outbound.ExternalVoiceModelsRepository
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.core.entity.DOWNLOAD_STATUS_INTENT
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalVoiceDownloadTask
import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.UUID

class VoiceViewModel(
    context: Context,
    private val voiceRepository: VoiceRepository,
    private val externalVoiceModelsRepository: ExternalVoiceModelsRepository
) : ViewModel() {
    private val _broadcastManager = LocalBroadcastManager.getInstance(context)

    val importedVoices = voiceRepository.getVoiceModels().distinctUntilChanged()
    val voicesByState =
        externalVoiceModelsRepository.getDefinitionsByState(importedVoices)

    private var _downloadWorker: WorkRequest? = null
    private val _downloadTasks = MutableStateFlow<List<ExternalVoiceDownloadTask>>(
        emptyList()
    )
    val downloadTasks: StateFlow<List<ExternalVoiceDownloadTask>> =
        _downloadTasks.asStateFlow()

    /**
     * Broadcast receiver for download status updates.
     */
    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val taskId = UUID.fromString(intent.getStringExtra("taskId"))
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("status", DownloadStatus::class.java)
            } else {
                intent.getSerializableExtra("status") as? DownloadStatus
            }

            status?.let {
                updateDownloadStatus(taskId, it)
            }
        }
    }


    /**
     * Returns a flow of [ExternalVoiceModelDefinition] that contains the definitions of the
     * external voice model for the given [id].
     */
    fun getVoiceModelDefinition(id: String): Flow<ExternalVoiceModelDefinition> {
        return externalVoiceModelsRepository.getDefinition(id)
            .distinctUntilChanged()
    }

    /**
     * Queues a download for the given voice model.
     */
    fun queueDownload(context: Context, model: ExternalVoiceModelDefinition) {
        var task = _downloadTasks.value.firstOrNull { it.model == model }

        if (task != null) {
            updateDownloadStatus(task.id, DownloadStatus.Queued)
        } else {
            task = ExternalVoiceDownloadTask(
                model = model,
                status = DownloadStatus.Queued,
            )
            _downloadTasks.value += task
        }

        val manager = WorkManager.getInstance(context)
        val worker = OneTimeWorkRequestBuilder<DownloadVoiceWorker>()
            .setInputData(
                workDataOf(
                    "taskId" to task.id.toString(),
                    "name" to task.model.name,
                    "uri" to task.model.bundleUri().toString(),
                    "checksum" to task.model.checksumUri().toString()
                )
            )
            .build()

        manager.enqueue(worker)
        manager.getWorkInfoByIdLiveData(worker.id)

        if (_downloadWorker == null) {
            _downloadWorker = worker
        }
    }

    /**
     * Cancels all pending downloads.
     */
    fun cancelDownload(context: Context) {
        _downloadWorker?.let {
            WorkManager.getInstance(context).cancelWorkById(it.id)
        }
        _downloadWorker = null
    }

    /**
     * Updates the download status of a task.
     */
    private fun updateDownloadStatus(
        taskId: UUID,
        status: DownloadStatus
    ) {
        when (status) {
            is DownloadStatus.Completed,
            is DownloadStatus.Error -> {
                removeDownloadTask(taskId)
            }

            is DownloadStatus.Cancelled -> {
                clearDownloadTasks()
            }

            else -> {
                _downloadTasks.value = _downloadTasks.value.map {
                    if (it.id == taskId) {
                        return@map it.copy(status = status)
                    }

                    it
                }
            }
        }
    }

    /**
     * Removes the download task from the queue.
     */
    private fun removeDownloadTask(taskId: UUID) {
        _downloadTasks.value = _downloadTasks.value.filter {
            it.id != taskId
        }
    }

    /**
     * Clears the download tasks.
     */
    private fun clearDownloadTasks() {
        _downloadTasks.value = emptyList()
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
        _broadcastManager.registerReceiver(
            downloadStatusReceiver,
            IntentFilter(DOWNLOAD_STATUS_INTENT)
        )
    }

    override fun onCleared() {
        super.onCleared()

        _broadcastManager.unregisterReceiver(downloadStatusReceiver)
    }
}