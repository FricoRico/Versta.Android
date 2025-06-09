package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.AudioPlayer
import app.versta.translate.adapter.outbound.JapaneseTransliterator
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.SynthReadyCallback
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.core.entity.TextToSpeechSynthesisState
import app.versta.translate.core.entity.VoiceGender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue

@OptIn(FlowPreview::class)
class TextToSpeechViewModel(
    private val tokenizer: TextToSpeechTokenizer,
    private val model: TextToSpeechInference,
    private val audioPlayer: AudioPlayer,
    private val voiceRepository: VoiceRepository,
    private val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    val speed = textToSpeechPreferenceRepository.getSpeed().distinctUntilChanged()
    val gender = textToSpeechPreferenceRepository.getGender().distinctUntilChanged()
    val threadCount = textToSpeechPreferenceRepository.getThreadCount().distinctUntilChanged()

    private val _japaneseTransliterator = JapaneseTransliterator()
    private val _language = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()
    private val _textToSpeechModel = _language.filterNotNull().map { data ->
        voiceRepository.getVoiceModelsByLanguage(data)
    }

    val voiceAvailable = _language.map { language ->
        val files = _textToSpeechModel.first()
        val gender = gender.first()
        files != null && language != null && files.voices.getVoiceByLanguage(
            language,
            gender
        ) != null
    }

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
    private val _speechCallback = object : SynthReadyCallback {
        override fun onSynthDataReady(audioData: ByteArray) {
            _speechProgress.value = TextToSpeechSynthesisState.Synthesizing
            audioPlayer.play(audioData)
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
        val transliterated = transliterate(text, language)

        _synthesizeScope.launch {
            ESpeakNG.getSession().setCallback(_speechCallback)
            ESpeakNG.getSession().synthesize(transliterated, language)
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

    private fun close() {
        model.close()
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
     * Transliterate text using the appropriate transliterator. Currently only supports Japanese
     * as it requires Kanji to Hiragana conversion as ESpeakNG does not support Kanji.
     */
    private fun transliterate(text: String, language: Language): String {
        return when (language.locale) {
            Locale.JAPANESE ->
                _japaneseTransliterator.convertToFurigana(text)
                    .replace("・", "")
                    .replace("ー", "")

            else -> text
        }
    }

    /**
     * Loads the model from given files.
     */
    fun load(files: VoiceWithModelFiles) {
        close()
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
    private fun setVoice(files: VoiceWithModelFiles, language: Language) {
        cancelSynthesis()

        viewModelScope.launch(Dispatchers.IO) {
            model.setVoice(path = files.voices.getVoiceByLanguage(language, gender.first()))
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
    fun reload() {
        viewModelScope.launch {
            _textToSpeechModel.collect {
                if (it == null) {
                    close()
                    return@collect
                }

                load(it)
            }
        }

        viewModelScope.launch {
            _language.combine(_textToSpeechModel) { language, files ->
                Pair(language, files)
            }.conflate().collect { (language, files) ->
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
                reload()
            }
        }
    }

    init {
        autoReload()
    }

    companion object {
        private val TAG: String = TextToSpeechViewModel::class.java.simpleName
    }
}