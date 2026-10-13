package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
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
import timber.log.Timber

// TODO: Move to generic entity class
sealed class LoadingProgress {
    data object Idle : LoadingProgress()

    data object InProgress : LoadingProgress()

    data object Completed : LoadingProgress()

    data class Error(
        val exception: Exception,
    ) : LoadingProgress()
}

sealed class ReadyState {
    data object NotReady : ReadyState()

    data object Ready : ReadyState()
}

@OptIn(FlowPreview::class)
class TranslationViewModel(
    intermediateModel: TranslationInference,
    outputModel: TranslationInference,
    createInference: (cacheSize: Long) -> TranslationInference,
    private val translationPreferenceRepository: TranslationPreferenceRepository,
    private val languageViewModel: LanguageViewModel,
) : ViewModel(),
    TranslationRunner {
    private val orchestrator =
        TranslationOrchestrator(
            intermediateModel = intermediateModel,
            outputModel = outputModel,
            translationPreferenceRepository = translationPreferenceRepository,
            createInference = createInference,
        )
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

    /**
     * Recreates the inference engines with the effective cache size and
     * reloads the currently-selected model into them. leanmt fixes the cache
     * size at engine construction, so recreation is required to apply changes
     * while the user's chosen [cacheSize] is preserved.
     */
    private fun reloadModel() {
        viewModelScope.launch {
            val files = _languageModels.first()
            val pair = _languages.first()?.toLanguagePair()

            orchestrator.recreateEngines()

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
    fun setCacheSize(size: Int): Job =
        viewModelScope.launch {
            translationPreferenceRepository.setCacheSize(size)
            reloadModel()
        }

    /**
     * Sets whether the translation cache is enabled. The engine is recreated with
     * the effective cache size (0 while disabled, the chosen size while enabled).
     */
    fun setCacheEnabled(enabled: Boolean): Job =
        viewModelScope.launch {
            translationPreferenceRepository.setCacheEnabled(enabled)
            reloadModel()
        }

    /**
     * Sets the number of beams.
     */
    fun setBeamSize(beams: Int): Job =
        viewModelScope.launch {
            translationPreferenceRepository.setNumberOfBeams(beams)
        }

    /**
     * Sets the maximum sequence length.
     */
    fun setMaxSequenceLength(length: Int): Job =
        viewModelScope.launch {
            translationPreferenceRepository.setMaxSequenceLength(length)
        }

    /**
     * Sets the translation error.
     */
    override fun setTranslationError(throwable: Throwable) {
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
    override suspend fun translate(
        input: String,
        languages: LanguagePair,
    ): String {
        _translationInProgress.value = true

        return try {
            val result = orchestrator.translate(input, _languageModels.first())
            _translationInProgress.value = false

            result
        } catch (e: Exception) {
            setTranslationError(e)
            Timber.tag(TAG).e(e)

            ""
        }
    }

    /**
     * Cancels the current translation.
     */
    fun cancelTranslation() {
        _translationInProgress.value = false
    }

    /**
     * Loads the model from the given files.
     */
    override suspend fun load(
        files: PivotPairModelFiles?,
        languages: LanguagePair,
    ) {
        cancelTranslation()

        viewModelScope
            .async(Dispatchers.IO) {
                _languageReadyState.value = ReadyState.NotReady
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    orchestrator.load(files)

                    _languageReadyState.value = ReadyState.Ready
                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
            }.await()
    }

    /**
     * Unloads the model.
     */
    private fun unload() {
        viewModelScope.launch(Dispatchers.IO) {
            orchestrator.unload()

            _languageReadyState.value = ReadyState.NotReady
        }
    }

    /**
     * Reloads the model.
     */
    fun reload() {
        viewModelScope.launch {
            _languageModels
                .combine(_languages) { files, pair ->
                    Pair(files, pair)
                }.conflate()
                .collect { (files, pair) ->
                    if (pair?.source !is Language) {
                        unload()
                        return@collect
                    }

                    pair.toLanguagePair()?.let { load(files, it) }
                }
        }

        viewModelScope.launch {
            cacheEnabled
                .combine(cacheSize) { enabled, size -> if (enabled) size else 0 }
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
