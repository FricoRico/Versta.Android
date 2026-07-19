package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.BergamotTinyInference
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.bridge.leanmt.LeanmtService
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.PivotPairModelFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

// TODO: Move to generic entity class
sealed class LoadingProgress {
    data object Idle : LoadingProgress()

    data object InProgress : LoadingProgress()

    data object Completed : LoadingProgress()

    data class Error(val exception: Exception) : LoadingProgress()
}

sealed class ReadyState {
    data object NotReady : ReadyState()

    data object Ready : ReadyState()
}

@OptIn(FlowPreview::class)
class TranslationViewModel(
    private var intermediateModel: TranslationInference,
    private var outputModel: TranslationInference,
    private val translationPreferenceRepository: TranslationPreferenceRepository,
    private val languageViewModel: LanguageViewModel
) : ViewModel() {
    val cacheSize = translationPreferenceRepository.getCacheSize().distinctUntilChanged()
    val cacheEnabled = translationPreferenceRepository.getCacheEnabled().distinctUntilChanged()
    val beamSize = translationPreferenceRepository.getNumberOfBeams().distinctUntilChanged()
    val maxSequenceLength =
        translationPreferenceRepository.getMaxSequenceLength().distinctUntilChanged()

    private val _languages = languageViewModel.languageOptions.distinctUntilChanged()
    private val _languageModels = languageViewModel.languageModelFiles.distinctUntilChanged()

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow().sample(10)

    private val _languageReadyState = MutableStateFlow<ReadyState>(ReadyState.NotReady)
    val languageReadyState: StateFlow<ReadyState> = _languageReadyState.asStateFlow()

    private val _translationInProgress = MutableStateFlow(false)
    val translationInProgress: StateFlow<Boolean> = _translationInProgress.asStateFlow()

    private val _translationError = MutableStateFlow<Throwable?>(null)
    val translationError: StateFlow<Throwable?> = _translationError.asStateFlow()

    private val _loadMutex = Mutex()


    /**
     * Recreates the underlying leanmt inference engines with the effective cache
     * size. leanmt fixes the cache size at service construction, so a disabled
     * cache is applied as size 0 while the user's chosen [getCacheSize] is
     * preserved. The currently-selected model is reloaded into the new engines.
     */
    private fun reloadModel() {
        viewModelScope.launch {
            val files = _languageModels.first()
            val pair = _languages.first()?.toLanguagePair()

            _loadMutex.withLock {
                intermediateModel.close()
                outputModel.close()

                intermediateModel =
                    BergamotTinyInference(LeanmtService.create(getCacheSize().toLong()))
                outputModel =
                    BergamotTinyInference(LeanmtService.create(getCacheSize().toLong()))
            }

            if (files != null && pair != null) {
                load(files, pair)
            }
        }
    }

    /**
     * Sets the translation cache size (number of remembered translations). The
     * size is preserved even when the cache is disabled; the effective size
     * passed to the engine becomes 0 only while disabled.
     */
    fun setCacheSize(size: Int): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setCacheSize(size)
            reloadModel()
        }
    }

    /**
     * Sets whether the translation cache is enabled. The engine is recreated with
     * the effective cache size (0 while disabled, the chosen size while enabled).
     */
    fun setCacheEnabled(enabled: Boolean): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setCacheEnabled(enabled)
            reloadModel()
        }
    }

    /**
     * Gets the actual cache size if cache is enabled.
     */
    private suspend fun getCacheSize(): Int {
        return if (cacheEnabled.first()) cacheSize.first() else -2
    }

    /**
     * Sets the number of beams.
     */
    fun setBeamSize(beams: Int): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setNumberOfBeams(beams)
        }
    }

    /**
     * Sets the maximum sequence length.
     */
    fun setMaxSequenceLength(length: Int): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setMaxSequenceLength(length)
        }
    }

    /**
     * Sets the translation error.
     */
    fun setTranslationError(throwable: Throwable) {
        _translationInProgress.value = false
        _translationError.value = throwable
    }

    /**
     * Clears the translation error.
     */
    fun clearTranslationError() {
        _translationError.value = null
    }

    /**
     * Translates the input text to the target language.
     */
    suspend fun translate(input: String, languages: LanguagePair): String {
        try {
            val sanitized = sanitize(input)

            _translationInProgress.value = true

            try {
                val intermediate = _loadMutex.withLock { intermediateModel }
                val output = _loadMutex.withLock { outputModel }

                var text = sanitized

                if (_languageModels.first()?.intermediary != null) {
                    text = intermediate.translate(
                        text = text,
                        maxBeamWidth = beamSize.first(),
                        maxSequenceLength = maxSequenceLength.first()
                    )
                }

                val result = output.translate(
                    text = text,
                    maxBeamWidth = beamSize.first(),
                    maxSequenceLength = maxSequenceLength.first()
                )
                _translationInProgress.value = false

                return result
            } catch (e: Exception) {
                _translationInProgress.value = false
                setTranslationError(e)
                Timber.tag(TAG).e(e)

                return ""
            }
        } catch (e: Exception) {
            setTranslationError(e)
            Timber.tag(TAG).e(e)

            return ""
        }
    }

    /**
     * Cancels the current translation.
     */
    fun cancelTranslation() {
        outputModel.cancel()
        intermediateModel.cancel()
        _translationInProgress.value = false
    }

    /**
     * Sanitizes the input string by removing any undefined characters.
     */
    private fun sanitize(input: String): String {
        val filteredString = input.filter { it.isDefined() }
        val utf8Bytes = filteredString.toByteArray(Charsets.UTF_8)

        return String(utf8Bytes, Charsets.UTF_8)
    }

    /**
     * Loads the model from the given files.
     */
    suspend fun load(files: PivotPairModelFiles?, languages: LanguagePair) {
        cancelTranslation()

        viewModelScope.async(Dispatchers.IO) {
            _loadMutex.withLock {
                _languageReadyState.value = ReadyState.NotReady
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    if (files?.intermediary != null) {
                        intermediateModel.load(files.intermediary.files, files.intermediary.config)
                    } else {
                        intermediateModel.close()
                    }

                    if (files?.output != null) {
                        outputModel.load(files.output.files, files.output.config)
                    }

                    _languageReadyState.value = ReadyState.Ready
                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
            }
        }.await()
    }

    /**
     * Unloads the model.
     */
    private fun unload() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadMutex.withLock {
                intermediateModel.close()
                outputModel.close()

                _languageReadyState.value = ReadyState.NotReady
            }
        }
    }

    /**
     * Reloads the model.
     */
    fun reload() {
        viewModelScope.launch {
            _languageModels.combine(_languages) { files, pair ->
                Pair(files, pair)
            }
                .conflate()
                .collect { (files, pair) ->
                    if (pair?.source !is Language) {
                        unload()
                        return@collect
                    }

                    pair.toLanguagePair()?.let { load(files, it) }
                }
        }

        viewModelScope.launch {
            cacheEnabled.combine(cacheSize) { enabled, size -> if (enabled) size else 0 }
                .conflate()
                .collect { reloadModel() }
        }
    }

    init {
        reload()
    }

    companion object {
        private val TAG: String = TranslationViewModel::class.java.simpleName
    }
}
