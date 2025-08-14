package app.versta.translate.core.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.DOWNLOAD_EXTERNAL_DATA_STATUS_INTENT
import app.versta.translate.adapter.inbound.DownloadExternalDataWorker
import app.versta.translate.adapter.outbound.AudioPlayer
import app.versta.translate.adapter.outbound.DataRepository
import app.versta.translate.adapter.outbound.ExternalDataRepository
import app.versta.translate.adapter.outbound.JapaneseTransliterator
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.bridge.speech.SynthReadyCallback
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalDataDefinition
import app.versta.translate.core.entity.ExternalDataDownloadTask
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.TextToSpeechDataFiles
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
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import kotlin.io.path.exists

@OptIn(FlowPreview::class)
class TextToSpeechViewModel(
    context: Context,
    private val espeakNG: ESpeakNG,
    private val openJTalk: OpenJTalk,
    private val tokenizer: TextToSpeechTokenizer,
    private val model: TextToSpeechInference,
    private val audioPlayer: AudioPlayer,
    private val dataRepository: DataRepository,
    private val voiceRepository: VoiceRepository,
    private val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository,
    private val externalDataRepository: ExternalDataRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    val enabled = textToSpeechPreferenceRepository.getTextToSpeechEnabled().distinctUntilChanged()
    val speed = textToSpeechPreferenceRepository.getSpeed().distinctUntilChanged()
    val gender = textToSpeechPreferenceRepository.getGender().distinctUntilChanged()
    val threadCount = textToSpeechPreferenceRepository.getThreadCount().distinctUntilChanged()

    private val _japaneseTransliterator = JapaneseTransliterator()
    private val _language = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()
    private val _textToSpeechData = dataRepository.getDataByType(type = DataType.TTS).mapNotNull {
        it.firstOrNull()
    }.distinctUntilChanged()

    private val _voiceModel = _language.filterNotNull().map { language ->
        voiceRepository.getVoiceModelsByLanguage(language = language)
    }.distinctUntilChanged()

    val textToSpeechReady = combine(
        enabled,
        espeakNG.isReadyStateFlow(),
        openJTalk.isReadyStateFlow()
    ) { enabled, espeakReady, openJTalkReady ->
        enabled && espeakReady && openJTalkReady
    }.distinctUntilChanged()

    val voiceAvailable = _language.map { language ->
        val files = _voiceModel.first()
        val gender = gender.first()
        files != null && language != null && files.voices.getVoiceByLanguage(
            language, gender
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

    private val downloadManager = DownloadManager<ExternalDataDownloadTask>(
        context = context,
        statusIntentAction = DOWNLOAD_EXTERNAL_DATA_STATUS_INTENT,
        workerClass = DownloadExternalDataWorker::class.java,
    )
    val downloadTasks: StateFlow<List<ExternalDataDownloadTask>> =
        downloadManager.downloadTasks.asStateFlow()

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
            espeakNG.setCallback(_speechCallback)
            espeakNG.synthesize(transliterated, language)
        }
    }

    private fun play(audio: FloatArray) {
        try {
            _speechProgress.value = TextToSpeechSynthesisState.Synthesizing
            audioPlayer.play(audio)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to play audio")
            setTextToSpeechError(e)
        }
    }

    fun cancelSynthesis() {
        try {
            espeakNG.stop()

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
        if (!enabled) {
            disableTextToSpeech()
            return
        }

        enableTextToSpeech()
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
     * Enables the text to speech feature by downloading the data.
     */
    private fun enableTextToSpeech() {
        viewModelScope.launch {
            cancelDownload()

            textToSpeechPreferenceRepository.setTextToSpeechEnabled(true)
            externalDataRepository.getDefinitions(DataType.TTS)
                .collect { definitions ->
                    if (definitions.isEmpty()) {
                        Timber.tag(TAG).e("No text to speech data definitions found")
                        return@collect
                    }

                    definitions.forEach { definition ->
                        if (!definition.isValid()) {
                            Timber.tag(TAG).e("Invalid text to speech data definition: $definition")
                            return@forEach
                        }

                        queueDownload("Text to Speech Data", definition)
                    }
                }
        }
    }

    /**
     * Disables the text to speech feature by deleting the downloaded data.
     */
    private fun disableTextToSpeech() {
        viewModelScope.launch {
            textToSpeechPreferenceRepository.setTextToSpeechEnabled(false)
            dataRepository.deleteDataByType(type = DataType.TTS)
        }
    }

    /**
     * Transliterate text using the appropriate transliterator. Currently only supports Japanese
     * as it requires Kanji to Hiragana conversion as ESpeakNG does not support Kanji.
     */
    private fun transliterate(text: String, language: Language): String {
        return when (language.locale) {
            Locale.JAPANESE -> _japaneseTransliterator.convertToFurigana(text).replace("・", "")
                .replace("ー", "")

            else -> text
        }
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
     * Reloads the text-to-speech data.
     */
    private fun reloadData() {
        viewModelScope.launch {
            _textToSpeechData.collect { data ->
                loadData(data)
            }
        }
    }

    /**
     * Load the text-to-speech data from the given [DataWithFiles].
     */
    private fun loadData(data: DataWithFiles) {
        if (data.files !is TextToSpeechDataFiles) {
            return
        }

        if (data.files.espeak.exists()) {
            espeakNG.load(data.files.espeak)
        }

        if (data.files.openJTalk.exists()) {
            openJTalk.load(data.files.openJTalk)
        }
    }

    /**
     * Automatically reloads the voice model when a new one is added or removed
     */
    private fun autoReload() {
        viewModelScope.launch {
            dataRepository.getDataByType(DataType.TTS).collect {
                reloadData()
            }
        }

        viewModelScope.launch {
            voiceRepository.getVoiceModels().collect {
                reloadVoice()
            }
        }
    }

    /**
     * Queues a download for TextToSpeech data.
     */
    private fun queueDownload(
        name: String,
        definition: ExternalDataDefinition,
        onComplete: (ExternalDataDefinition) -> Unit = {}
    ) {
        val task = ExternalDataDownloadTask(
            downloadName = name,
            definition = definition,
            status = DownloadStatus.Queued,
            onComplete = onComplete
        )
        downloadManager.queueDownload(task)
    }

    /**
     * Cancels all pending downloads.
     */
    private fun cancelDownload() {
        downloadManager.cancelDownload()
        downloadManager.clearDownloadTasks()
    }

    init {
        downloadManager.register()
        autoReload()
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.unregister()
        close()
    }

    companion object {
        private val TAG: String = TextToSpeechViewModel::class.java.simpleName
    }
}