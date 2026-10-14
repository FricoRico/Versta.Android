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
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import java.util.concurrent.LinkedBlockingQueue

@OptIn(FlowPreview::class)
class TextToSpeechViewModel(
    private val tokenizer: TextToSpeechTokenizer,
    private val model: TextToSpeechInference,
    private val audioPlayer: AudioPlayer,
    private val voiceRepository: VoiceRepository,
    private val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
) : ViewModel() {
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

    val voiceAvailable =
        combine(
            _language,
            _voiceModel,
            gender,
        ) { language, files, gender ->
            language != null && files != null && files.getVoice(language, gender) != null
        }.distinctUntilChanged()

    /**
     * True when text-to-speech can actually run: the feature is enabled and a
     * voice for the target language is installed. Languages without Supertonic
     * 3 coverage (ca, fa, he, zh) never become available.
     */
    val textToSpeechReady =
        combine(enabled, voiceAvailable) { enabled, available ->
            enabled && available
        }.distinctUntilChanged()

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow().sample(10)

    private val _speechProgress =
        MutableStateFlow<TextToSpeechSynthesisState>(TextToSpeechSynthesisState.Idle)
    val speechProgressState: StateFlow<TextToSpeechSynthesisState> = _speechProgress.asStateFlow()

    private val _textToSpeechError = MutableStateFlow<Throwable?>(null)
    val textToSpeechError: StateFlow<Throwable?> = _textToSpeechError.asStateFlow()

    private val _loadMutex = Mutex()

    private val _audioScope = CoroutineScope(Dispatchers.IO)
    private val _audioQueue = LinkedBlockingQueue<FloatArray>()
    private val _synthesizeScope = CoroutineScope(Dispatchers.Default)

    private val _speechInference = MutableStateFlow(false)

    suspend fun synthesize(
        text: String,
        language: Language,
    ) {
        try {
            if (!textToSpeechReady.first()) {
                Timber.tag(TAG).w("No voice available for ${language.isoCode}, skipping synthesis")
                return
            }

            synthesizeInChunks(text, language)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            setTextToSpeechError(e)
        }
    }

    private fun synthesizeInChunks(
        text: String,
        language: Language,
    ) {
        val tokens = tokenizer.tokenize(text, language)
        if (tokens.isEmpty()) {
            return
        }

        _speechInference.value = true
        _speechProgress.value = TextToSpeechSynthesisState.Preparing

        _audioQueue.clear()

        _synthesizeScope.launch {
            for (token in tokens) {
                if (!_speechInference.value) {
                    break
                }

                val audio = model.synthesize(token, speed.first())
                _audioQueue.put(audio)
            }

            _speechInference.value = false
        }

        _audioScope.launch {
            while (_speechInference.value || _audioQueue.isNotEmpty()) {
                play(_audioQueue.take())
            }

            _speechProgress.value = TextToSpeechSynthesisState.Idle
        }
    }

    private fun play(audio: FloatArray) {
        try {
            _speechProgress.value = TextToSpeechSynthesisState.Synthesizing
            audioPlayer.play(audio, Supertonic3.SAMPLE_RATE)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to play audio")
            setTextToSpeechError(e)
        }
    }

    fun cancelSynthesis() {
        try {
            _speechInference.value = false
            _speechProgress.value = TextToSpeechSynthesisState.Idle

            _audioQueue.clear()
            audioPlayer.stop()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to cancel synthesis")
        }
    }

    private fun close() {
        try {
            model.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to close model")
        }
    }

    /**
     * Sets the text-to-speech error.
     */
    private fun setTextToSpeechError(throwable: Throwable) {
        _speechInference.value = false
        _speechProgress.value = TextToSpeechSynthesisState.Idle

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
        close()
        cancelSynthesis()

        viewModelScope.launch(Dispatchers.IO) {
            _loadMutex.withLock {
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    tokenizer.load(files.tokenizer)
                    model.load(files.inference, threadCount.first())
                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
            }
        }
    }

    /**
     * Sets the voice from given files and language.
     */
    private fun setVoice(
        files: VoiceWithModelFiles,
        language: Language,
    ) {
        cancelSynthesis()

        viewModelScope.launch(Dispatchers.IO) {
            model.setVoice(path = files.getVoice(language, gender.first()))
        }
    }

    /**
     * Clears the voice.
     */
    private fun clearVoice() {
        cancelSynthesis()
        model.clearVoice()
    }

    /**
     * Reloads the model and voice.
     */
    fun reloadVoice() {
        viewModelScope.launch {
            _voiceModel.collect {
                if (it == null) {
                    close()
                    return@collect
                }

                loadVoice(it)
            }
        }

        viewModelScope.launch {
            combine(_language, _voiceModel) { language, files ->
                Pair(language, files)
            }.collect { (language, files) ->
                if (language == null || files == null) {
                    clearVoice()
                    return@collect
                }

                setVoice(files, language)
            }
        }
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
        close()
    }

    companion object {
        private val TAG: String = TextToSpeechViewModel::class.java.simpleName
    }
}
