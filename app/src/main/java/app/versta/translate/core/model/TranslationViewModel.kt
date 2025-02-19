package app.versta.translate.core.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.TranslationMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO: Move to generic entity class
sealed class LoadingProgress {
    data object Idle : LoadingProgress()

    data object InProgress : LoadingProgress()

    data object Completed : LoadingProgress()
    data class Error(val exception: Exception) : LoadingProgress()
}

class TranslationViewModel(
    private val tokenizer: TranslationTokenizer,
    private val model: TranslationInference,
    private val languageRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
    private val translationPreferenceRepository: TranslationPreferenceRepository,
) : ViewModel() {
    val cacheSize = translationPreferenceRepository.getCacheSize().distinctUntilChanged()
    val cacheEnabled = translationPreferenceRepository.getCacheEnabled().distinctUntilChanged()
    val beamSize = translationPreferenceRepository.getNumberOfBeams().distinctUntilChanged()
    val maxSequenceLength =
        translationPreferenceRepository.getMaxSequenceLength().distinctUntilChanged()
    val minProbability = translationPreferenceRepository.getMinProbability().distinctUntilChanged()
    val threadCount = translationPreferenceRepository.getThreadCount().distinctUntilChanged()

    private lateinit var _cache: TranslationMemoryCache
    private val _queue = Mutex()

    val languages = languagePreferenceRepository.getLanguagePair().distinctUntilChanged()
    private val _languageModels = languages.filterNotNull().map { data ->
        languageRepository.getLanguageModel(data)
    }

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    private val _translationInProgress = MutableStateFlow(false)
    val translationInProgress: StateFlow<Boolean> = _translationInProgress.asStateFlow()

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
     * Sets the thread count.
     */
    fun setThreadCount(count: Int): Job {
        return viewModelScope.launch {
            translationPreferenceRepository.setThreadCount(count)
        }
    }

    /**
     * Translates the input text to the target language, returning the result as a flow. If the
     * translation is already in the cache, it will be returned immediately.
     */
    @OptIn(FlowPreview::class)
    suspend fun translateAsFlow(input: String, languages: LanguagePair): Flow<String> {
        val sanitized = sanitize(input)

        var cache = _cache.get(sanitized, languages)
        if (cache != null) {
            return flowOf(cache)
        }

        _queue.withLock {
            // Check to see if the translation is already in the cache, if so return it.
            cache = _cache.get(sanitized, languages)

            if (cache != null) {
                return flowOf(cache!!)
            }

            val (inputIds, attentionMask) = tokenizer.encode(sanitized)
            val minP = minProbability.first() * 100 / tokenizer.vocabSize

            _translationInProgress.value = true
            return model.runAsFlow(
                inputIds = inputIds,
                attentionMask = attentionMask,
                eosId = tokenizer.eosId,
                padId = tokenizer.padId,
                minP = minP,
                beamSize = beamSize.first(),
                maxSequenceLength = maxSequenceLength.first(),
            )
                .debounce(1000L/120)
                .conflate()
                .map { tokenIds ->
                val outputText = tokenizer.decode(tokenIds)

                if (tokenIds.last() == tokenizer.eosId) {
                    _translationInProgress.value = false

                    if (cacheEnabled.first()) {
                        _cache.put(sanitized, outputText, languages)
                    }
                }

                outputText
            }
        }
    }

    /**
     * Translates the input text to the target language. If the translation is already in the cache,
     * it will be returned immediately.
     */
    suspend fun translate(input: String, languages: LanguagePair): String {
        val sanitized = sanitize(input)

        var cache = _cache.get(sanitized, languages)
        if (cache != null) {
            return cache
        }

        return _queue.withLock {
            // Check to see if the translation is already in the cache, if so return it.
            cache = _cache.get(sanitized, languages)

            if (cache != null) {
                return cache!!
            }

            val (inputIds, attentionMask) = tokenizer.encode(sanitized)
            val minP = minProbability.first() * 100 / tokenizer.vocabSize

            _translationInProgress.value = true
            val tokenIds = model.run(
                inputIds = inputIds,
                attentionMask = attentionMask,
                eosId = tokenizer.eosId,
                padId = tokenizer.padId,
                minP = minP,
                beamSize = beamSize.first(),
                maxSequenceLength = maxSequenceLength.first(),
            )
            _translationInProgress.value = false

            tokenizer.decode(tokenIds)

        }
    }

    /**
     * Cancels the current translation.
     */
    fun cancelTranslation() {
        model.cancel()
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
    fun load(files: LanguageModelFiles, languages: LanguagePair) {
        cancelTranslation()

        viewModelScope.launch(Dispatchers.IO) {
            _loadMutex.withLock {
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    tokenizer.load(files.tokenizer, languages)
                    model.load(files.inference, threadCount.first())

                    _loadingProgress.value = LoadingProgress.Completed
                } catch (e: Exception) {
                    e.printStackTrace()
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
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
            _languageModels.conflate().collect {
                val pair = languages.first()

                if (it != null && pair != null) {
                    load(it, pair)
                }
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