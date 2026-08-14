package app.versta.translate.core.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.DOWNLOAD_SPEECH_RECOGNITION_STATUS_INTENT
import app.versta.translate.adapter.inbound.DownloadSpeechRecognitionWorker
import app.versta.translate.adapter.outbound.ExternalSpeechRecognitionModelsRepository
import app.versta.translate.adapter.outbound.MicrophoneCaptureException
import app.versta.translate.adapter.outbound.SpeechRecognitionInference
import app.versta.translate.adapter.outbound.SpeechRecognitionRepository
import app.versta.translate.adapter.outbound.WhisperSpeechRecognition
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalSpeechRecognitionDownloadTask
import app.versta.translate.core.entity.ExternalSpeechRecognitionModelDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.SpeechRecognitionSegment
import app.versta.translate.core.entity.supportedLanguageIsoCodes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Outcome of [SpeechRecognitionViewModel.start]. [NotLoaded] means a reload
 * was kicked off and the caller may queue a retry; [MicrophoneUnavailable]
 * means the recognizer is fine but the mic could not be captured — retrying
 * immediately is pointless, so callers should surface this instead of
 * queueing.
 */
enum class StartResult {
    Started,
    NotLoaded,
    MicrophoneUnavailable,
}

class SpeechRecognitionViewModel(
    context: Context,
    private val speechRecognitionRepository: SpeechRecognitionRepository,
    private val externalSpeechRecognitionModelsRepository: ExternalSpeechRecognitionModelsRepository,
    private val speechRecognitionInference: SpeechRecognitionInference,
    private val languageViewModel: LanguageViewModel,
) : ViewModel() {

    val speechRecognitionModels = externalSpeechRecognitionModelsRepository.getDefinitions().distinctUntilChanged()

    val translationLanguageIsoCodes: Flow<Set<String>> =
        languageViewModel.languageModels
            .map { it.supportedLanguageIsoCodes() }
            .distinctUntilChanged()

    private val importedModels = speechRecognitionRepository.getSpeechRecognitionModels().distinctUntilChanged()

    val speechRecognitionModelsByState = externalSpeechRecognitionModelsRepository.getDefinitionsByState(
        importedModels
    ).distinctUntilChanged()

    private val downloadManager = DownloadManager<ExternalSpeechRecognitionDownloadTask>(
        context = context,
        statusIntentAction = DOWNLOAD_SPEECH_RECOGNITION_STATUS_INTENT,
        workerClass = DownloadSpeechRecognitionWorker::class.java
    )
    val downloadTasks: StateFlow<List<ExternalSpeechRecognitionDownloadTask>> =
        downloadManager.downloadTasks.asStateFlow()

    // Dictation session state, forwarded from the inference so screens depend
    // only on this ViewModel rather than reaching into the adapter layer
    // directly.
    val segments: Flow<List<SpeechRecognitionSegment>> = speechRecognitionInference.segments
    val listening: Flow<Boolean> = speechRecognitionInference.listening
    val finalizing: Flow<Boolean> = speechRecognitionInference.finalizing
    val rtf: Flow<Float?> = speechRecognitionInference.rtf
    val spectrum: Flow<FloatArray> = speechRecognitionInference.spectrum

    /**
     * Starts a dictation session. Recovers rather than throwing if the
     * inference turns out not to be loaded despite [speechRecognitionReadyState]
     * saying otherwise — triggers a reload via [invalidate] and returns false
     * so the caller can queue the start instead.
     */
    fun start(scope: CoroutineScope): StartResult {
        return try {
            speechRecognitionInference.start(scope)
            StartResult.Started
        } catch (e: MicrophoneCaptureException) {
            Timber.tag(TAG).e(e, "start: microphone unavailable")
            StartResult.MicrophoneUnavailable
        } catch (e: IllegalStateException) {
            Timber.tag(TAG).e(e, "start: inference reported not loaded, forcing a reload")
            invalidate()
            StartResult.NotLoaded
        }
    }

    /**
     * Cuts the microphone; buffered audio keeps transcribing until
     * [finalizing] clears.
     */
    fun stop() {
        speechRecognitionInference.stop()
    }

    // Serializes load() attempts against each other. Mirrors
    // TranslationViewModel's own _loadMutex: reload()'s source flow can fire
    // again (a rapid language swap) while a previous native load — up to an
    // 811 MB mmap + KleidiAI repack — is still in flight.
    private val _loadMutex = Mutex()

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow()

    // Whether the recognizer is loaded and ready for start() to be called
    // immediately. Eager and reactive rather than driven by the dictation
    // button — mirrors how TranslationViewModel loads translation models as
    // soon as the language pair + files are available, so the FIRST
    // dictation attempt does not pay the full load latency. See
    // WhisperSpeechRecognition.load()'s own internal caching for why a
    // language-only change here is much cheaper than an installed-model
    // change.
    private val _speechRecognitionReadyState = MutableStateFlow<ReadyState>(ReadyState.NotReady)
    val speechRecognitionReadyState: StateFlow<ReadyState> = _speechRecognitionReadyState.asStateFlow()

    // Bumped by [invalidate] to force the reactive loader to re-run even when
    // its natural inputs (installed model, source language) are unchanged.
    // Without this a load can only ever be re-triggered by changing one of
    // those, so any state that leaves the inference unloaded behind the
    // ViewModel's back is unrecoverable for the rest of the process.
    private val _reloadTrigger = MutableStateFlow(0)

    /**
     * Forces a reload on the next collector pass. Call when the inference is
     * observed to be unloaded despite [speechRecognitionReadyState] claiming
     * otherwise — the reload itself is cheap when the model is genuinely
     * still resident, since [WhisperSpeechRecognition.load] early-returns.
     */
    fun invalidate() {
        _reloadTrigger.value += 1
    }

    fun getSpeechRecognitionModelDefinition(id: String): Flow<ExternalSpeechRecognitionModelDefinition> {
        return externalSpeechRecognitionModelsRepository.getDefinition(id).distinctUntilChanged()
    }

    fun queueDownload(model: ExternalSpeechRecognitionModelDefinition) {
        val task = ExternalSpeechRecognitionDownloadTask(
            model = model,
            status = DownloadStatus.Queued
        )
        downloadManager.queueDownload(task)
    }

    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    fun deleteSpeechRecognitionModel(id: String) {
        viewModelScope.launch {
            speechRecognitionRepository.deleteSpeechRecognitionModel(id)
        }
    }

    /**
     * Reactively (re)loads the speech recognition model whenever the
     * installed model or the source language changes. [WhisperSpeechRecognition.load]
     * itself decides how much work that actually is — reusing the loaded
     * model when only the language differs, reusing everything when neither
     * does — so this does not need to distinguish those cases itself, unlike
     * [TranslationViewModel.reload]'s two-collector split.
     */
    private data class LoadRequest(
        val id: String?,
        val sourceLanguageIsoCode: String?,
        val trigger: Int,
    )

    private fun reload() {
        viewModelScope.launch {
            combine(
                speechRecognitionModelsByState,
                languageViewModel.sourceLanguage
                    .map { (it as? Language)?.isoCode }
                    .distinctUntilChanged(),
                _reloadTrigger,
            ) { modelsByState, sourceLanguageIsoCode, trigger ->
                LoadRequest(
                    id = modelsByState.installed.firstOrNull()?.definition?.id,
                    sourceLanguageIsoCode = sourceLanguageIsoCode,
                    trigger = trigger,
                )
            }
                .distinctUntilChanged()
                .conflate()
                .collect { request ->
                    if (request.id == null) {
                        // No installed model — the loaded one was deleted or
                        // never loaded. Release the native model + recognizer
                        // (they would otherwise stay resident until process
                        // death). close() is cheap when nothing is loaded.
                        _speechRecognitionReadyState.value = ReadyState.NotReady
                        _loadingProgress.value = LoadingProgress.Idle
                        withContext(Dispatchers.IO) { speechRecognitionInference.close() }
                        return@collect
                    }
                    load(request.id, request.sourceLanguageIsoCode)
                }
        }
    }

    private suspend fun load(id: String, sourceLanguageIsoCode: String?) {
        withContext(Dispatchers.IO) {
            _loadMutex.withLock {
                _speechRecognitionReadyState.value = ReadyState.NotReady
                _loadingProgress.value = LoadingProgress.InProgress
                try {
                    val files = speechRecognitionRepository.getSpeechRecognitionModel(id)
                    if (files == null || !files.inference.isValid()) {
                        Timber.tag(TAG).w("reload: model '%s' files not found on disk", id)
                        _loadingProgress.value = LoadingProgress.Idle
                        return@withLock
                    }
                    speechRecognitionInference.setSourceLanguage(sourceLanguageIsoCode)
                    speechRecognitionInference.load(files.inference, threads = SPEECH_RECOGNITION_THREADS)
                    _speechRecognitionReadyState.value = ReadyState.Ready
                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "reload: failed to load model '%s'", id)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
            }
        }
    }

    init {
        downloadManager.register()
        reload()
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.unregister()
        // This ViewModel owns the native model's lifetime (it is what loads
        // it, in reload()), so it is also what releases it. Screens that use
        // the recognizer only stop() it — see the DisposableEffect in
        // TextTranslation.kt for why closing it from there breaks the ready
        // state. Being app-scoped, this effectively runs at process death,
        // which is the intent of preloading: the model stays resident so
        // dictation is instant on every visit.
        speechRecognitionInference.close()
    }

    companion object {
        private val TAG: String = SpeechRecognitionViewModel::class.java.simpleName
        private const val SPEECH_RECOGNITION_THREADS = 4
    }
}
