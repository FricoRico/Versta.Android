package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.TranslationMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
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

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModel(
    private val tokenizer: TranslationTokenizer,
    private val model: TranslationInference,
    private val languageRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
) : ViewModel() {
    // TODO: Make this a configuration option
    private val _cacheSize: Int = 64
    private val _sentenceBatching = false
    private val _numOfBeams = 3
    private val _cacheEnabled = false

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    private val translationCache = TranslationMemoryCache(_cacheSize)
    private val queue = Mutex()

    val languages = languagePreferenceRepository.getLanguagePair()

    private val loadModelFlow = languages.filterNotNull().mapLatest { data ->
        languageRepository.getLanguageModel(data)
    }

    fun translateAsFlow(input: String, languages: LanguagePair): Flow<String> {
        val sanitized = sanitize(input)

        var cache = translationCache.get(sanitized, languages)
        if (cache != null) {
            return flowOf(cache)
        }

        return flow {
            queue.withLock {
                // Check to see if the translation is already in the cache, if so return it.
                cache = translationCache.get(sanitized, languages)

                if (cache != null) {
                    emit(cache!!)
                }

                val (inputIds, attentionMask) = tokenizer.encode(sanitized)
                model.runAsFlow(
                    inputIds,
                    attentionMask,
                    tokenizer.eosId,
                    tokenizer.padId,
                    _numOfBeams
                ).collect { tokenIds ->
                    val outputText = tokenizer.decode(tokenIds)
                    emit(outputText)

                    if (_cacheEnabled && tokenIds.last() == tokenizer.eosId) {
                        translationCache.put(sanitized, outputText, languages)
                    }
                }
            }
        }
    }

    suspend fun translate(input: String, languages: LanguagePair): String {
        val sanitized = sanitize(input)

        var cache = translationCache.get(sanitized, languages)
        if (cache != null) {
            return cache
        }

        return queue.withLock {
            // Check to see if the translation is already in the cache, if so return it.
            cache = translationCache.get(sanitized, languages)

            if (cache != null) {
                return cache!!
            }

            val (inputIds, attentionMask) = tokenizer.encode(sanitized)
            val tokenIds = model.run(
                inputIds,
                attentionMask,
                tokenizer.eosId,
                tokenizer.padId,
                _numOfBeams
            )
            tokenizer.decode(tokenIds)
        }
    }

    private fun sanitize(input: String): String {
        val filteredString = input.filter { it.isDefined() }
        val utf8Bytes = filteredString.toByteArray(Charsets.UTF_8)

        return String(utf8Bytes, Charsets.UTF_8)
    }

    fun load(files: LanguageModelFiles, languages: LanguagePair) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = LoadingProgress.InProgress

            try {
                tokenizer.load(files.tokenizer, languages)
                model.load(files.inference)

                _loadingProgress.value = LoadingProgress.Completed
            } catch (e: Exception) {
                e.printStackTrace()
                _loadingProgress.value = LoadingProgress.Error(e)
            }
        }
    }

    init {
        viewModelScope.launch {
            loadModelFlow.collect {
                val pair = languages.first()

                if (it != null && pair != null) {
                    load(it, pair)
                }
            }
        }
    }
}