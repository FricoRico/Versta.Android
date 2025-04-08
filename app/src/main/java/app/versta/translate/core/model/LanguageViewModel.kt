package app.versta.translate.core.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.DownloadListener
import app.versta.translate.adapter.inbound.HttpDownloadClient
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalLanguageDownloadTask
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.ArrayDeque
import java.util.Queue

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
    private val _extractionDirectory = context.filesDir
    private val _downloadDirectory = context.cacheDir.resolve("downloads")

    private val _languageExtractor = TarballExtractor(context)
    private val _languageDownloadClient = HttpDownloadClient(_downloadDirectory)

    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState.asStateFlow()

    private var _languageDownloading = false
    private val _languageModelDownloads: Queue<ExternalLanguageDownloadTask> = ArrayDeque()
    private val _languageModelDownloadTasks = MutableStateFlow<List<ExternalLanguageDownloadTask>>(
        emptyList()
    )
    val languageModelDownloadTasks: StateFlow<List<ExternalLanguageDownloadTask>> =
        _languageModelDownloadTasks.asStateFlow()

    // TODO: Make this private so that it will only be used for mapping definitions
    val availableLanguages = languageRepository.getLanguages().distinctUntilChanged()
    val availableLanguagePairs = languageRepository.getLanguagePairs().distinctUntilChanged()

    val languageModels =
        externalLanguageModelsRepository.getDefinitions().distinctUntilChanged()
    val languageModelsByState =
        externalLanguageModelsRepository.getDefinitionsByState(availableLanguages).distinctUntilChanged()

    val sourceLanguage = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()
    val targetLanguage = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()

    val canSwapLanguages = combine(
        sourceLanguage, targetLanguage, availableLanguagePairs
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
    fun deleteBySource(language: Language) {
        viewModelScope.launch {
            languageRepository.deleteLanguageModelsBySourceLanguage(language).forEach {
                languagePreferenceRepository.clearLanguageSelectionForPair(it)
            }
        }
    }

    /**
     * Adds a language model to the download queue.
     */
    fun queueDownload(model: ExternalLanguagePairDefinition) {
        val task = ExternalLanguageDownloadTask(
            model = model,
            status = DownloadStatus.Queued,
        )

        _languageModelDownloads.add(task)
        _languageModelDownloadTasks.value += task

        handleDownloadQueue()
    }

    /**
     * Handles the download queue, allowing to download models one, by one.
     */
    private fun handleDownloadQueue() {
        if (_languageDownloading || _languageModelDownloads.isEmpty()) {
            return
        }

        _languageDownloading = true
        _languageModelDownloads.poll()?.let {
            viewModelScope.launch {
                downloadLanguageModel(it)
            }
        }
    }

    /**
     * Downloads the language model and extracts it.
     */
    private suspend fun downloadLanguageModel(task: ExternalLanguageDownloadTask) {
        withContext(Dispatchers.IO) {
            _languageDownloadClient.download(
                uri = task.model.bundleUri,
                checksum = task.model.checksumUri,
                listener = object : DownloadListener {
                    override fun onProgressUpdate(downloaded: Long, total: Long) {
                        updateDownloadStatus(
                            task, DownloadStatus.Progress(
                                downloaded = downloaded,
                                total = total
                            )
                        )
                    }

                    override fun onCompletion(file: File) {
                        updateDownloadStatus(task, DownloadStatus.Processing)
                        extractDownload(task, file)
                        updateDownloadStatus(task, DownloadStatus.Completed)
                        remoteDownloadTask(task)

                        _languageDownloading = false
                        handleDownloadQueue()
                    }

                    override fun onError(exception: Exception) {
                        updateDownloadStatus(
                            task,
                            DownloadStatus.Error(exception)
                        )

                        _languageDownloading = false
                        handleDownloadQueue()
                    }
                })
        }
    }

    /**
     * Extracts the downloaded language model.
     */
    private fun extractDownload(task: ExternalLanguageDownloadTask, file: File) {
        var output: File? = null

        try {
            output = _languageExtractor.extract(
                file = file,
                outputDir = _extractionDirectory,
            )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            val metadata = readMetadata(output)
            languageRepository.upsertLanguageModels(metadata)
        } catch (e: Exception) {
            output?.deleteRecursively()
            updateDownloadStatus(task, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File?): LanguageModel {
        if (output == null) {
            throw Exception("Output file is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val languageBundleMetadata =
            _serializer.decodeFromString<LanguageBundleMetadata>(bundleMetadataFile.readText())

        if (!languageBundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val languageModelMetadata = languageBundleMetadata.metadata.map {
            val languageMetadataFile = File(output.resolve(it.directory), "metadata.json")

            _serializer.decodeFromString<LanguageModelMetadata>(languageMetadataFile.readText())
                .setRootPath(
                    path = output.resolve(it.directory).toPath()
                )
        }

        if (languageModelMetadata.any { !it.isValid() }) {
            throw Exception("Invalid language metadata file")
        }

        return LanguageModel(
            bundle = languageBundleMetadata,
            languages = languageModelMetadata
        )
    }

    /**
     * Updates the download status of a task.
     */
    private fun updateDownloadStatus(
        task: ExternalLanguageDownloadTask,
        status: DownloadStatus
    ) {
        _languageModelDownloadTasks.value = _languageModelDownloadTasks.value.map {
            if (it.model == task.model) {
                return@map it.copy(status = status)
            }

            it
        }
    }

    /**
     * Removes the download task from the queue.
     */
    private fun remoteDownloadTask(task: ExternalLanguageDownloadTask) {
        _languageModelDownloadTasks.value = _languageModelDownloadTasks.value.filter {
            it.model != task.model
        }
    }

    companion object {
        private val _serializer = Json { ignoreUnknownKeys = true }

        private val TAG = LanguageViewModel::class.java.simpleName
    }
}