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
import app.versta.translate.bridge.utils.LanguageDetect
import app.versta.translate.core.entity.AUTO_DETECT_UNKNOWN_CODE
import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalLanguageDownloadTask
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

enum class LanguageType {
    Source, Target
}

class LanguageViewModel(
    context: Context,
    private val languageRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
    private val externalLanguageModelsRepository: ExternalLanguageModelsRepository,
) : ViewModel() {
    val pivotTranslationEnabled =
        languagePreferenceRepository.getPivotTranslation().distinctUntilChanged()

    private val _broadcastManager = LocalBroadcastManager.getInstance(context)
    private val _languageDetector = LanguageDetect()

    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState.asStateFlow()

    private val _autoDetectInput = MutableStateFlow("")
    val autoDetectLanguage = MutableStateFlow<Language?>(null)

    private var _downloadWorker: WorkRequest? = null
    private val _downloadTasks = MutableStateFlow<List<ExternalLanguageDownloadTask>>(
        emptyList()
    )
    val downloadTasks: StateFlow<List<ExternalLanguageDownloadTask>> = _downloadTasks.asStateFlow()

    private val _importedLanguages = languageRepository.getLanguages().distinctUntilChanged()
    val importedLanguagePairs = languageRepository.getLanguagePairs().distinctUntilChanged()

    val sourceLanguage = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()
    val targetLanguage = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()

    val languageModels = externalLanguageModelsRepository.getDefinitions().distinctUntilChanged()
    val languageModelsByState =
        externalLanguageModelsRepository.getDefinitionsByState(_importedLanguages)
            .distinctUntilChanged()

    val languageOptions = languagePreferenceRepository.getLanguagePair().distinctUntilChanged()
    val languagePair =
        combine(sourceLanguage, targetLanguage, autoDetectLanguage) { source, target, detected ->
            if (source == null || target == null) {
                return@combine null
            }

            when (source) {
                is AutoDetectLanguage -> {
                    if (detected == null) {
                        return@combine null
                    }

                    return@combine LanguagePair(
                        source = detected,
                        target = target
                    )
                }

                is Language -> {
                    return@combine LanguagePair(
                        source = source,
                        target = target
                    )
                }

                else -> {
                    return@combine null
                }
            }
        }.distinctUntilChanged()
    val languageModelFiles = combine(languagePair, pivotTranslationEnabled) { pair, pivot ->
        if (pair == null) {
            return@combine null
        }

        languageRepository.getLanguageModel(pair, pivot)
    }.distinctUntilChanged()

    val sourceLanguages = combine(importedLanguagePairs, targetLanguage) { pairs, target ->
        pairs.asSequence()
            .map { it.source }
            .distinctBy { it.isoCode }.plus(
                AutoDetectLanguage()
            )
            .sortedBy { language -> if (language is AutoDetectLanguage) 0 else 1 }.toList()
    }.distinctUntilChanged()

    val targetLanguages = combine(importedLanguagePairs, sourceLanguage) { pairs, source ->
        if (pivotTranslationEnabled.first()) {
            return@combine pairs.filter { it.target != source }
                .map { it.target }.distinctBy { it.isoCode }
        }

        if (source is Language) {
            return@combine languageRepository.getTargetLanguagesBySource(source)
        }

        pairs.map { it.target }
    }.distinctUntilChanged()

    val canSwapLanguages = combine(
        sourceLanguage, targetLanguage, importedLanguagePairs
    ) { source, target, pairs ->
        if (source == null || target == null) {
            return@combine false
        }

        if (pivotTranslationEnabled.first()) {
            return@combine pairs.any { it.source == target } && pairs.any { it.target == source }
        }

        pairs.any { it.source == target && it.target == source }
    }.distinctUntilChanged()

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
    fun setSourceLanguage(language: LanguageOption): Job {
        return viewModelScope.launch {
            val currentSourceLanguage = sourceLanguage.first()
            val currentTargetLanguage = targetLanguage.first()
            val currentCanSwapLanguages = canSwapLanguages.first()

            languagePreferenceRepository.setSourceLanguage(language)


            // If there is a target language available for the current source language, set it instead
            // of clearing the target language.
            if (currentSourceLanguage is Language && currentCanSwapLanguages && currentTargetLanguage == language) {
                languagePreferenceRepository.setTargetLanguage(currentSourceLanguage)
                return@launch
            }

            if (language !is Language) {
                return@launch
            }

            // If the current target language is not available for the new source language, clear the
            // current target language.
            if (!currentCanSwapLanguages && currentTargetLanguage == language) {
                clearTargetLanguage()
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
     * Sets the pivot translation option based on the given boolean value.
     */
    fun setPivotTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            languagePreferenceRepository.setPivotTranslation(enabled)

            languagePreferenceRepository.clearSourceLanguage()
            languagePreferenceRepository.clearTargetLanguage()
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

    /**
     * Set the input.
     */
    fun setAutoDetectInput(text: String) {
        _autoDetectInput.value = text
    }

    /**
     * Detect the language of the given text.
     */
    fun detectLanguage(text: String) {
        val result = _languageDetector.detectLanguage(text)

        if (result == null || !result.isReliable) {
            return
        }

        viewModelScope.launch {
            if (sourceLanguages.first().any { it.isoCode == result.language } || languageModels.first().any { it.pair.source.isoCode == result.language }) {
                setAutoDetectLanguage(result.language)
            }
        }
    }

    /**
     * Set the detected language.
     */
    private fun setAutoDetectLanguage(isoCode: String) {
        if (isoCode == AUTO_DETECT_UNKNOWN_CODE) {
            autoDetectLanguage.value = null
            return
        }

        autoDetectLanguage.value = Language.fromIsoCode(isoCode)
    }

    @OptIn(FlowPreview::class)
    private fun autoDetectLanguage() {
        viewModelScope.launch(Dispatchers.Default) {
            _autoDetectInput.debounce(300).collect {
                detectLanguage(it)
            }
        }
    }

    init {
        _broadcastManager.registerReceiver(
            downloadStatusReceiver, IntentFilter(DOWNLOAD_LANGUAGE_STATUS_INTENT)
        )

        autoDetectLanguage()
    }

    override fun onCleared() {
        super.onCleared()

        _broadcastManager.unregisterReceiver(downloadStatusReceiver)
    }
}