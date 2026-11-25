package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.AudioPlayer
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.nio.file.Path

@OptIn(FlowPreview::class)
class TextToSpeechViewModel(
    private val tokenizer: TextToSpeechTokenizer,
    private val model: TextToSpeechInference,
    private val audioPlayer: AudioPlayer,
    private val voiceRepository: VoiceRepository,
    private val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
) : ViewModel() {
    /**
     * The voice the current preferences ask for: the installed model files for
     * the target language and the style file for the preferred gender, or
     * null when the language has no installed voice.
     */
    private data class VoiceSelection(
        val files: VoiceWithModelFiles?,
        val voice: Path?,
    )

    val enabled = textToSpeechPreferenceRepository.getTextToSpeechEnabled().distinctUntilChanged()
    val speed = textToSpeechPreferenceRepository.getSpeed().distinctUntilChanged()
    val gender = textToSpeechPreferenceRepository.getGender().distinctUntilChanged()
    val threadCount = textToSpeechPreferenceRepository.getThreadCount().distinctUntilChanged()

    private val _language = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()

    private val _voiceModel =
        _language
            .filterNotNull()
            .map { language ->
                voiceRepository.getVoiceModelsByLanguage(language = language)
            }.distinctUntilChanged()

    private val _selection =
        combine(_language, _voiceModel, gender) { language, files, gender ->
            VoiceSelection(files, language?.let { files?.getVoice(it, gender) })
        }.distinctUntilChanged()

    // What the engine actually holds: set only after a load or voice binding
    // succeeds, reset as soon as either is replaced, closed or fails.
    private val _loadedModel = MutableStateFlow<VoiceWithModelFiles?>(null)
    private val _boundVoice = MutableStateFlow<Path?>(null)

    val voiceAvailable = _selection.map { it.voice != null }.distinctUntilChanged()

    /**
     * True when text-to-speech can actually run: the feature is enabled, a
     * voice for the target language is installed, and that model and voice
     * have finished loading and binding successfully. Languages without
     * Supertonic 3 coverage (ca, fa, he, zh) never become available.
     */
    val textToSpeechReady =
        combine(enabled, _selection, _loadedModel, _boundVoice) { enabled, selection, loaded, bound ->
            enabled && selection.voice != null && loaded == selection.files && bound == selection.voice
        }.distinctUntilChanged()

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow().sample(10)

    private val _speechProgress =
        MutableStateFlow<TextToSpeechSynthesisState>(TextToSpeechSynthesisState.Idle)
    val speechProgressState: StateFlow<TextToSpeechSynthesisState> = _speechProgress.asStateFlow()

    private val _textToSpeechError = MutableStateFlow<Throwable?>(null)
    val textToSpeechError: StateFlow<Throwable?> = _textToSpeechError.asStateFlow()

    private val _loadMutex = Mutex()

    // Native model lifecycle calls (load/close/voice) block and must stay off
    // the main thread; one lane keeps them in call order.
    private val _modelDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var _reloadJobs: List<Job> = emptyList()

    // Speech requests: each one owns its job and chunk queue. The generation
    // identifies the current request; a superseded request may still be inside
    // a blocking native call, and whatever it returns afterwards is dropped.
    private val _speechScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _speechLock = Any()
    private var _speechGeneration = 0L
    private var _speechJob: Job? = null

    /**
     * Speaks [text] in [language], replacing any request in progress. Does
     * nothing unless [textToSpeechReady].
     */
    suspend fun synthesize(
        text: String,
        language: Language,
    ) {
        try {
            if (!textToSpeechReady.first()) {
                Timber.tag(TAG).w("Text-to-speech not ready for ${language.isoCode}, skipping synthesis")
                return
            }

            val tokens = tokenizer.tokenize(text, language)
            if (tokens.isEmpty()) {
                return
            }

            startSpeech(tokens)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            setTextToSpeechError(e)
        }
    }

    private fun startSpeech(tokens: List<LongArray>) {
        synchronized(_speechLock) {
            stopSpeech()

            val generation = _speechGeneration
            _speechProgress.value = TextToSpeechSynthesisState.Preparing
            _speechJob = _speechScope.launch { speak(generation, tokens) }
        }
    }

    private suspend fun speak(
        generation: Long,
        tokens: List<LongArray>,
    ) {
        try {
            coroutineScope {
                val chunks = Channel<FloatArray>(Channel.UNLIMITED)

                launch {
                    try {
                        for (token in tokens) {
                            val audio = model.synthesize(token, speed.first())
                            if (!isCurrent(generation)) {
                                return@launch
                            }
                            chunks.send(audio)
                        }
                    } finally {
                        chunks.close()
                    }
                }

                for (audio in chunks) {
                    if (!isCurrent(generation)) {
                        break
                    }
                    updateProgress(generation, TextToSpeechSynthesisState.Synthesizing)
                    audioPlayer.play(audio, model.sampleRate)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCurrent(generation)) {
                Timber.tag(TAG).e(e, "Failed to synthesize speech")
                setTextToSpeechError(e)
            }
        } finally {
            updateProgress(generation, TextToSpeechSynthesisState.Idle)
        }
    }

    private fun isCurrent(generation: Long) = synchronized(_speechLock) { generation == _speechGeneration }

    private fun updateProgress(
        generation: Long,
        state: TextToSpeechSynthesisState,
    ) = synchronized(_speechLock) {
        if (generation == _speechGeneration) {
            _speechProgress.value = state
        }
    }

    /**
     * Supersedes the current request: its job is cancelled, its pending and
     * late audio is dropped, and playback stops. Call with [_speechLock] held.
     */
    private fun stopSpeech() {
        _speechGeneration++
        _speechJob?.cancel()
        _speechJob = null
        _speechProgress.value = TextToSpeechSynthesisState.Idle

        try {
            audioPlayer.stop()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to stop audio")
        }
    }

    fun cancelSynthesis() = synchronized(_speechLock) { stopSpeech() }

    private fun close() {
        _loadedModel.value = null

        try {
            model.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to close model")
        }
    }

    /**
     * Sets the text-to-speech error and ends the current request.
     */
    private fun setTextToSpeechError(throwable: Throwable) {
        cancelSynthesis()
        _textToSpeechError.value = throwable
    }

    /**
     * Clears the text-to-speech error.
     */
    fun clearTextToSpeechError() {
        _textToSpeechError.value = null
    }

    /**
     * Sets whether text-to-speech is enabled.
     */
    fun setTextToSpeechEnabled(enabled: Boolean) {
        viewModelScope.launch {
            textToSpeechPreferenceRepository.setTextToSpeechEnabled(enabled)
        }
    }

    /**
     * Sets the speech rate.
     */
    fun setSpeed(speed: Float): Job =
        viewModelScope.launch {
            textToSpeechPreferenceRepository.setSpeed(speed)
        }

    /**
     * Sets the voice gender.
     */
    fun setGender(gender: VoiceGender): Job =
        viewModelScope.launch {
            textToSpeechPreferenceRepository.setGender(gender)
        }

    /**
     * Sets the thread count.
     */
    fun setThreadCount(count: Int): Job =
        viewModelScope.launch {
            textToSpeechPreferenceRepository.setThreadCount(count)
        }

    /**
     * Loads the voice model from given files.
     */
    private fun loadVoice(files: VoiceWithModelFiles) {
        cancelSynthesis()
        _loadedModel.value = null

        viewModelScope.launch(_modelDispatcher) {
            _loadMutex.withLock {
                close()
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    tokenizer.load(files.tokenizer)
                    model.load(files.inference, threadCount.first())
                    _loadedModel.value = files
                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
            }
        }
    }

    /**
     * Unloads the voice model and stops any speech.
     */
    private fun unloadVoice() {
        cancelSynthesis()
        _loadedModel.value = null

        viewModelScope.launch(_modelDispatcher) { close() }
    }

    /**
     * Binds the [voice] style for subsequent synthesis.
     */
    private fun setVoice(voice: Path) {
        cancelSynthesis()
        _boundVoice.value = null

        viewModelScope.launch(_modelDispatcher) {
            try {
                model.setVoice(path = voice)
                _boundVoice.value = voice
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to set voice")
                setTextToSpeechError(e)
            }
        }
    }

    /**
     * Clears the voice.
     */
    private fun clearVoice() {
        cancelSynthesis()
        _boundVoice.value = null

        viewModelScope.launch(_modelDispatcher) {
            try {
                model.clearVoice()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to clear voice")
            }
        }
    }

    /**
     * Reloads the model and voice.
     */
    fun reloadVoice() {
        // Each call restarts the collectors; keeping the old ones would load
        // the model once more per voice-repository change.
        _reloadJobs.forEach { it.cancel() }

        val modelJob =
            viewModelScope.launch {
                // The model only occupies memory while text-to-speech is enabled.
                combine(_voiceModel, enabled) { files, isEnabled ->
                    files.takeIf { isEnabled }
                }.distinctUntilChanged().collect {
                    if (it == null) {
                        unloadVoice()
                        return@collect
                    }

                    loadVoice(it)
                }
            }

        val voiceJob =
            viewModelScope.launch {
                _selection.collect { selection ->
                    val voice = selection.voice
                    if (voice == null) {
                        clearVoice()
                        return@collect
                    }

                    setVoice(voice)
                }
            }

        _reloadJobs = listOf(modelJob, voiceJob)
    }

    /**
     * Automatically reloads the voice model when a new one is added or removed
     */
    private fun autoReload() {
        viewModelScope.launch {
            voiceRepository.getVoiceModels().collect {
                reloadVoice()
            }
        }
    }

    init {
        autoReload()
    }

    override fun onCleared() {
        super.onCleared()
        cancelSynthesis()
        _speechScope.cancel()
        // viewModelScope is already cancelled here; release off the main thread.
        CoroutineScope(_modelDispatcher).launch { close() }
    }

    companion object {
        private val TAG: String = TextToSpeechViewModel::class.java.simpleName
    }
}
