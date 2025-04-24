package app.versta.translate.core.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import app.versta.translate.R
import app.versta.translate.adapter.inbound.DOWNLOAD_LANGUAGE_STATUS_INTENT
import app.versta.translate.adapter.inbound.DownloadLanguageWorker
import app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalLanguageDownloadTask
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

enum class LanguageType {
    Source, Target
}

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModel(
    context: Context,
    private val languageRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
    private val externalLanguageModelsRepository: ExternalLanguageModelsRepository,
) : ViewModel() {
    private val _broadcastManager = LocalBroadcastManager.getInstance(context)

    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState.asStateFlow()

    private var _downloadWorker: WorkRequest? = null
    private val _downloadTasks = MutableStateFlow<List<ExternalLanguageDownloadTask>>(
        emptyList()
    )
    val downloadTasks: StateFlow<List<ExternalLanguageDownloadTask>> = _downloadTasks.asStateFlow()

    private val _importedLanguages = languageRepository.getLanguages().distinctUntilChanged()
    val importedLanguagePairs = languageRepository.getLanguagePairs().distinctUntilChanged()

    val languageModels = externalLanguageModelsRepository.getDefinitions().distinctUntilChanged()
    val languageModelsByState =
        externalLanguageModelsRepository.getDefinitionsByState(_importedLanguages)
            .distinctUntilChanged()

    val sourceLanguage = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()
    val targetLanguage = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()

    val canSwapLanguages = combine(
        sourceLanguage, targetLanguage, importedLanguagePairs
    ) { source, target, pairs ->
        if (source == null || target == null) {
            return@combine false
        }

        pairs.any { it.source == target && it.target == source }
    }.distinctUntilChanged()

    val sourceLanguages = languageRepository.getSourceLanguages().distinctUntilChanged()
    val targetLanguages = sourceLanguage.flatMapLatest {
        if (it != null) {
            languageRepository.getTargetLanguagesBySource(it)
        } else {
            flowOf(emptyList())
        }
    }

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
                updateDownloadStatus(context, taskId, it)
            }
        }
    }

    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definitions of the
     * external language model for the given [LanguagePair].
     */
    fun getLanguageDefinition(pair: LanguagePair): Flow<ExternalLanguagePairDefinition> {
        return externalLanguageModelsRepository.getDefinition(pair).distinctUntilChanged()
    }

    /**
     * Sets the language selection state.
     */
    fun setLanguageSelectionState(state: LanguageType?) {
        _languageSelectionState.value = state
    }

    /**
     * Sets the source language.
     */
    fun setSourceLanguage(language: Language): Job {
        return viewModelScope.launch {
            val current = sourceLanguage.first()
            languagePreferenceRepository.setSourceLanguage(language)

            // If there is a target language available for the current source language, set it instead
            // of clearing the target language.
            if (current != null && targetLanguages.first().contains(current)) {
                languagePreferenceRepository.setTargetLanguage(current)
                return@launch
            }

            // If the current target language is not available for the new source language, clear the
            // current target language.
            languageRepository.getTargetLanguagesBySource(language).collectLatest { languages ->
                if (languages.none { it == targetLanguage.first() }) {
                    clearTargetLanguage()
                }
            }
        }
    }

    /**
     * Sets the target language.
     */
    fun setTargetLanguage(language: Language): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.setTargetLanguage(language)
        }
    }

    /**
     * Swaps the source and target languages.
     */
    fun swapLanguages(): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.swapLanguages()
        }
    }

    /**
     * Clears the target language.
     */
    private fun clearTargetLanguage(): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.clearTargetLanguage()
        }
    }

    /**
     * Clears the language selection if the language pair is the same as the current one.
     */
    fun removeLanguageModel(pair: LanguagePair, bidirectional: Boolean) {
        viewModelScope.launch {
            languageRepository.deleteLanguageModel(pair, bidirectional)
            languagePreferenceRepository.clearLanguageSelectionForPair(pair)
        }
    }

    /**
     * Queues a download for the given language model.
     */
    fun queueDownload(context: Context, model: ExternalLanguagePairDefinition) {
        var task = _downloadTasks.value.firstOrNull { it.model == model }

        if (task != null) {
            updateDownloadStatus(context, task.id, DownloadStatus.Queued)
        } else {
            task = ExternalLanguageDownloadTask(
                model = model,
                status = DownloadStatus.Queued,
            )
            _downloadTasks.value += task
        }

        val manager = WorkManager.getInstance(context)
        val worker = OneTimeWorkRequestBuilder<DownloadLanguageWorker>().setInputData(
            workDataOf(
                "taskId" to task.id.toString(),
                "name" to "${task.model.pair.source.name} - ${task.model.pair.target.name}",
                "uri" to task.model.bundleUri.toString(),
                "checksum" to task.model.checksumUri.toString()
            )
        ).build()

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
        context: Context, taskId: UUID, status: DownloadStatus
    ) {
        when (status) {
            is DownloadStatus.Completed -> {
                removeDownloadTask(taskId)
            }

            is DownloadStatus.Error -> {
                when (status.exception) {
                    is CancellationException -> {}

                    is SocketException,
                    is SocketTimeoutException, is UnknownHostException -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.download_error_no_internet),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.download_error_unknown),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                setDownloadStatus(taskId, status)
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
     * Sets the download status of a task.
     */
    private fun setDownloadStatus(
        taskId: UUID, status: DownloadStatus
    ) {
        _downloadTasks.value = _downloadTasks.value.map {
            if (it.id == taskId) {
                return@map it.copy(status = status)
            }

            it
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

    init {
        _broadcastManager.registerReceiver(
            downloadStatusReceiver, IntentFilter(DOWNLOAD_LANGUAGE_STATUS_INTENT)
        )
    }

    override fun onCleared() {
        super.onCleared()

        _broadcastManager.unregisterReceiver(downloadStatusReceiver)
    }
}