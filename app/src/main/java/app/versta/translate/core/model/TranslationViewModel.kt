package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.PivotPairModelFiles
import app.versta.translate.core.entity.TranslationMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
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

@OptIn(FlowPreview::class)
class TranslationViewModel(
    private val intermediateTokenizer: TranslationTokenizer,
    private val intermediateModel: TranslationInference,
    private val outputTokenizer: TranslationTokenizer,
    private val outputModel: TranslationInference,
    private val translationPreferenceRepository: TranslationPreferenceRepository,
    private val languageViewModel: LanguageViewModel
) : ViewModel() {
    val cacheSize = translationPreferenceRepository.getCacheSize().distinctUntilChanged()
    val cacheEnabled = translationPreferenceRepository.getCacheEnabled().distinctUntilChanged()
    val beamSize = translationPreferenceRepository.getNumberOfBeams().distinctUntilChanged()
    val maxSequenceLength =
        translationPreferenceRepository.getMaxSequenceLength().distinctUntilChanged()
    val minProbability = translationPreferenceRepository.getMinProbability().distinctUntilChanged()
    val repetitionPenalty =
        translationPreferenceRepository.getRepetitionPenalty().distinctUntilChanged()
    val threadCount = translationPreferenceRepository.getThreadCount().distinctUntilChanged()

    private lateinit var _cache: TranslationMemoryCache
    private val _queue = Mutex()

    private val _languages = languageViewModel.languageOptions.distinctUntilChanged()
    private val _languageModels = languageViewModel.languageModelFiles.distinctUntilChanged()

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow().sample(10)

    private val _translationInProgress = MutableStateFlow(false)
    val translationInProgress: StateFlow<Boolean> = _translationInProgress.asStateFlow()

    private val _translationError = MutableStateFlow<Throwable?>(null)
    val translationError: StateFlow<Throwable?> = _translationError.asStateFlow()

    private val _loadMutex = Mutex()

    /**
     * Sets the cache size.
     */
    fun setCacheSize(size: Int): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setCacheSize(size)
        }
    }

    /**
     * Sets the cache enabled state.
     */
    fun setCacheEnabled(enabled: Boolean): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setCacheEnabled(enabled)
        }
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
     * Sets the minimum probability.
     */
    fun setMinProbability(probability: Float): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setMinProbability(probability)
        }
    }

    /**
     * Sets the penalty for repeating tokens.
     */
    fun setRepetitionPenalty(penalty: Float): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setRepetitionPenalty(penalty)
        }
    }

    /**
     * Sets the thread count.
     */
    fun setThreadCount(count: Int): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setThreadCount(count)
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
     * Translates the input text to the target language, returning the result as a flow. If the
     * translation is already in the cache, it will be returned immediately.
     */
    suspend fun translateAsFlow(
        input: String,
        languages: LanguagePair,
        onCompletion: (suspend (output: String) -> Unit)? = null
    ): Pair<Flow<String>?, Flow<String>> {
        val sanitized = sanitize(input)
        var cache = _cache.get(sanitized, languages)
        if (cache != null) {
            return Pair(null, flowOf(cache))
        }

        return _queue.withLock {
            cache = _cache.get(sanitized, languages)

            if (cache != null) {
                return@withLock Pair(null, flowOf(cache.toString()))
            }

            _translationInProgress.value = true
            if (_languageModels.first()?.intermediary != null) {
                val intermediateFlow = intermediateTranslateAsFlow(sanitized)
                val outputFlow = flow {
                    val finalIntermediateResult = intermediateFlow.last()

                    emitAll(outputTranslateAsFlow(finalIntermediateResult) { output ->
                        onTranslationCompletion(sanitized, output, languages, onCompletion)

                        _translationInProgress.value = false
                    })
                }

                return@withLock Pair(intermediateFlow, outputFlow)
            }

            return@withLock Pair(null, outputTranslateAsFlow(sanitized) { output ->
                onTranslationCompletion(input, output, languages, onCompletion)

                _translationInProgress.value = false
            })
        }
    }

    private suspend fun outputTranslateAsFlow(
        input: String,
        onCompletion: (suspend (output: String) -> Unit)? = null
    ): Flow<String> {
        val sanitized = sanitize(input)
        val (inputIds, attentionMask) = outputTokenizer.encode(sanitized)
        val minP = minProbability.first() * 100 / outputTokenizer.vocabSize

        return outputModel.runAsFlow(
            inputIds = inputIds,
            attentionMask = attentionMask,
            eosId = outputTokenizer.eosId,
            padId = outputTokenizer.padId,
            minP = minP,
            repetitionPenalty = repetitionPenalty.first(),
            beamSize = beamSize.first(),
            maxSequenceLength = maxSequenceLength.first(),
        )
            .conflate()
            .catch { e ->
                setTranslationError(e)
                Timber.tag(TAG).e(e)
            }
            .map { tokenIds ->
                val output = outputTokenizer.decode(tokenIds)

                if (tokenIds.last() == outputTokenizer.eosId) {
                    onCompletion?.invoke(output)
                }

                output
            }
            .flowOn(Dispatchers.Default)
    }

    private suspend fun intermediateTranslateAsFlow(
        input: String,
        onCompletion: (suspend (intermediate: String) -> Unit)? = null
    ): Flow<String> {
        val sanitized = sanitize(input)
        val (inputIds, attentionMask) = intermediateTokenizer.encode(sanitized)
        val minP = minProbability.first() * 100 / intermediateTokenizer.vocabSize

        return intermediateModel.runAsFlow(
            inputIds = inputIds,
            attentionMask = attentionMask,
            eosId = intermediateTokenizer.eosId,
            padId = intermediateTokenizer.padId,
            minP = minP,
            repetitionPenalty = repetitionPenalty.first(),
            beamSize = beamSize.first(),
            maxSequenceLength = maxSequenceLength.first(),
        )
            .conflate()
            .catch { e ->
                setTranslationError(e)
                Timber.tag(TAG).e(e)
            }
            .map { tokenIds ->
                val output = intermediateTokenizer.decode(tokenIds)

                if (tokenIds.last() == intermediateTokenizer.eosId) {
                    onCompletion?.invoke(output)
                }

                output
            }
            .flowOn(Dispatchers.Default)
    }

    private suspend fun onTranslationCompletion(
        input: String,
        output: String,
        languages: LanguagePair,
        onCompletion: (suspend (output: String) -> Unit)?
    ) {
        if (cacheEnabled.first()) {
            _cache.put(input, output, languages)
        }

        onCompletion?.invoke(output)
    }

    private suspend fun intermediateTranslate(input: String): String {
        try {
            val sanitized = sanitize(input)
            val (inputIds, attentionMask) = intermediateTokenizer.encode(sanitized)
            val minP = minProbability.first() * 100 / intermediateTokenizer.vocabSize

            val tokenIds = intermediateModel.run(
                inputIds = inputIds,
                attentionMask = attentionMask,
                eosId = intermediateTokenizer.eosId,
                padId = intermediateTokenizer.padId,
                minP = minP,
                repetitionPenalty = repetitionPenalty.first(),
                beamSize = beamSize.first(),
                maxSequenceLength = maxSequenceLength.first(),
            )

            return intermediateTokenizer.decode(tokenIds)
        } catch (e: Exception) {
            setTranslationError(e)
            Timber.tag(TAG).e(e)

            return ""
        }
    }

    /**
     * Translates the input text to the target language. If the translation is already in the cache,
     * it will be returned immediately.
     */
    suspend fun translate(input: String, languages: LanguagePair): String {
        try {
            var sanitized = sanitize(input)

            var cache = _cache.get(sanitized, languages)
            if (cache != null) {
                return cache
            }

            _queue.withLock {
                // Check to see if the translation is already in the cache, if so return it.
                cache = _cache.get(sanitized, languages)

                if (cache != null) {
                    return cache.toString()
                }

                _translationInProgress.value = true

                if (_languageModels.first()?.intermediary != null) {
                    sanitized = intermediateTranslate(sanitized)
                }

                val (inputIds, attentionMask) = outputTokenizer.encode(sanitized)
                val minP = minProbability.first() * 100 / outputTokenizer.vocabSize

                val tokenIds = outputModel.run(
                    inputIds = inputIds,
                    attentionMask = attentionMask,
                    eosId = outputTokenizer.eosId,
                    padId = outputTokenizer.padId,
                    minP = minP,
                    repetitionPenalty = repetitionPenalty.first(),
                    beamSize = beamSize.first(),
                    maxSequenceLength = maxSequenceLength.first(),
                )
                _translationInProgress.value = false

                return outputTokenizer.decode(tokenIds)
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
     * Loads the model and tokenizer from the given files.
     */
    suspend fun load(files: PivotPairModelFiles?, languages: LanguagePair) {
        cancelTranslation()

        viewModelScope.async(Dispatchers.IO) {
            _loadMutex.withLock {
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    if (files?.intermediary != null) {
                        intermediateTokenizer.load(files.intermediary.tokenizer, languages)
                        intermediateModel.load(files.intermediary.inference, threadCount.first())
                    } else {
                        intermediateModel.close()
                    }

                    if (files?.output != null) {
                        outputTokenizer.load(files.output.tokenizer, languages)
                        outputModel.load(files.output.inference, threadCount.first())
                    }

                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }

            }
        }.await()
    }

    /**
     * Unloads the model and tokenizer.
     */
    private fun unload() {
        viewModelScope.launch(Dispatchers.IO) {
            _loadMutex.withLock {
                intermediateModel.close()
                outputModel.close()
            }
        }
    }

    /**
     * Reloads the model and tokenizer.
     */
    fun reload() {
        viewModelScope.launch {
            cacheSize.conflate().collect { size ->
                _cache = TranslationMemoryCache(size)
            }
        }

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
    }

    init {
        reload()
    }

    companion object {
        private val TAG: String = TranslationViewModel::class.java.simpleName
    }
}