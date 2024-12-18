package app.versta.translate.core.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.TranslationMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ModelInterface {
    suspend fun run(inputIds: LongArray, attentionMask: LongArray, eosId: Long, padId: Long, beams: Int = 6, maxSequenceLength: Int = 128): LongArray
    fun load(files: LanguageModelInferenceFiles)
}

interface TokenizerInterface {
    val vocabSize: Long

    val padId: Long
    val eosId: Long
    val unknownId: Long

    fun tokenize(text: String): List<String>
    fun encode(text: String, padTokens: Boolean = false): Pair<LongArray, LongArray>
//    fun encode(
//        texts: List<String>,
//        padTokens: Boolean = false
//    ): Pair<Array<LongArray>, Array<LongArray>>

    fun decode(ids: LongArray, filterSpecialTokens: Boolean = true): String
//    fun decode(ids: Array<LongArray>, filterSpecialTokens: Boolean = true): List<String>
    fun splitSentences(text: String, groupLength: Int = 192): List<String>
    fun load(files: LanguageModelTokenizerFiles, languages: LanguagePair)
}

sealed class LoadingProgress {
    data object Idle : LoadingProgress()

    data object InProgress : LoadingProgress()

    data object Completed : LoadingProgress()
    data class Error(val exception: Exception) : LoadingProgress()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModel(
    private val tokenizer: TokenizerInterface,
    private val model: ModelInterface,
    private val languageDatabaseRepository: LanguageDatabaseRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
) : ViewModel() {
    // TODO: Make this a configuration option
    private val cacheSize: Int = 64
    private val sentenceBatching = false
    private val numOfBeams = 3

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    private val translationCache = TranslationMemoryCache(cacheSize)
    private val queue = Mutex()

    private val languages = languagePreferenceRepository.languagePair
    private val loadModelFlow = languages.filterNotNull().mapLatest { data ->
        languageDatabaseRepository.getLanguageModel(data).first()
    }

    suspend fun translate(input: String): String {
        val sanitized = sanitize(input)

        var cache = translationCache.get(sanitized)
        if (cache != null) {
            return cache
        }

        return queue.withLock {
            // Check to see if the translation is already in the cache, if so return it.
            cache = translationCache.get(sanitized)

            if (cache != null) {
                return cache!!
            }

            val (inputIds, attentionMask) = tokenizer.encode(sanitized)
            val tokenIds = model.run(inputIds, attentionMask, tokenizer.eosId, tokenizer.padId, numOfBeams)

            val startTimestamp = System.currentTimeMillis()
            val outputText = tokenizer.decode(tokenIds)
            Log.d("TranslationViewModel", "Decoding took ${System.currentTimeMillis() - startTimestamp}ms")

            translationCache.put(sanitized, outputText)

            outputText
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