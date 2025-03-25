package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.AudioPlayer
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.SynthReadyCallback
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.TextToSpeechModelFiles
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.entity.VoiceGender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.LinkedBlockingQueue

class TextToSpeechViewModel(
    private val tokenizer: TextToSpeechTokenizer,
    private val model: TextToSpeechInference,
    private val audioPlayer: AudioPlayer,
    private val textToSpeechRepository: TextToSpeechRepository,
    private val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    val speed = textToSpeechPreferenceRepository.getSpeed().distinctUntilChanged()
    val gender = textToSpeechPreferenceRepository.getGender().distinctUntilChanged()
    val threadCount = textToSpeechPreferenceRepository.getThreadCount().distinctUntilChanged()

    private val _language = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()
    private val _textToSpeechModel =
        textToSpeechRepository.getTextToSpeechModel().distinctUntilChanged()

    val voiceAvailable = _language.map { language ->
        val files = _textToSpeechModel.first()
        val gender = gender.first()
        files != null && language != null && files.voices.getVoiceByLanguage(language, gender) != null
    }

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

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
    private val _speechCallback = object : SynthReadyCallback {
        override fun onSynthDataReady(audio: ByteArray) {
            _speechProgress.value = TextToSpeechSynthesisState.Synthesizing
            audioPlayer.play(audio)
        }

        override fun onSynthDataComplete() {
            _speechProgress.value = TextToSpeechSynthesisState.Idle
        }
    }

    suspend fun synthesize(text: String, language: Language) {
        try {
            if (voiceAvailable.first()) {
                highDefinitionSynthesize(text, language)
                return
            }

            lowDefinitionSynthesize(text, language)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            setTextToSpeechError(e)
        }
    }

    private fun highDefinitionSynthesize(text: String, language: Language) {
        val tokens = tokenizer.tokenize(text, language)

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

    private fun lowDefinitionSynthesize(text: String, language: Language) {
        _synthesizeScope.launch {
            ESpeakNG.getSession().setCallback(_speechCallback)
            ESpeakNG.getSession().synthesize(text, language)
        }
    }

    private fun play(audio: FloatArray) {
        _speechProgress.value = TextToSpeechSynthesisState.Synthesizing
        audioPlayer.play(audio)
    }

    fun cancelSynthesis() {
        ESpeakNG.getSession().stop()

        _speechInference.value = false
        _speechProgress.value = TextToSpeechSynthesisState.Idle

        _audioQueue.clear()
        audioPlayer.stop()
    }

    fun close() {
        audioPlayer.release()
    }

    /**
     * Sets the text-to-speech error.
     */
    fun setTextToSpeechError(throwable: Throwable) {
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
     * Sets the speech rate.
     */
    fun setSpeed(speed: Float): Job {
        return viewModelScope.launch {
            textToSpeechPreferenceRepository.setSpeed(speed)
        }
    }

    /**
     * Sets the voice gender.
     */
    fun setGender(gender: VoiceGender): Job {
        return viewModelScope.launch {
            textToSpeechPreferenceRepository.setGender(gender)
        }
    }

    /**
     * Sets the thread count.
     */
    fun setThreadCount(count: Int): Job {
        return viewModelScope.launch {
            textToSpeechPreferenceRepository.setThreadCount(count)
        }
    }

    /**
     * Loads the model from given files.
     */
    fun load(files: TextToSpeechModelFiles) {
        cancelSynthesis()

        viewModelScope.launch(Dispatchers.IO) {
            _loadMutex.withLock {
                _loadingProgress.value = LoadingProgress.InProgress

                try {
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
    fun setVoice(files: TextToSpeechModelFiles, language: Language) {
        cancelSynthesis()

        viewModelScope.launch(Dispatchers.IO) {
            model.setVoice(path = files.voices.getVoiceByLanguage(language, gender.first()))
        }
    }

    /**
     * Reloads the model and voice.
     */
    fun reload() {
        viewModelScope.launch {
            _textToSpeechModel.collect {
                if (it != null) {
                    load(it)
                }
            }
        }

        viewModelScope.launch {
            _language.conflate().collect {
                val files = _textToSpeechModel.first()

                if (it != null && files != null) {
                    setVoice(files, it)
                }
            }
        }
    }

    init {
        reload()
    }

    companion object {
        private val TAG: String = TextToSpeechViewModel::class.java.simpleName
    }
}