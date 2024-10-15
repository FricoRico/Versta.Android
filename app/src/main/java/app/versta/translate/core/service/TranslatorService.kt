package app.versta.translate.core.service

import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.versta.translate.core.entity.TranslationMemoryCache

interface ModelInterface {
    fun encode(inputIds: Array<LongArray>, attentionMask: Array<LongArray>): Array<*>
    fun decode(encoderHiddenStates: Array<*>, attentionMask: Array<*>): Array<LongArray>
    fun load(files: LanguageModelInferenceFiles)
}

interface TokenizerInterface {
    fun tokenize(text: String): List<String>
    fun encode(text: String, padTokens: Boolean = false): Pair<LongArray, LongArray>
    fun encode(
        texts: List<String>,
        padTokens: Boolean = false
    ): Pair<Array<LongArray>, Array<LongArray>>

    fun decode(ids: LongArray, filterSpecialTokens: Boolean = true): String
    fun decode(ids: Array<LongArray>, filterSpecialTokens: Boolean = true): List<String>
    fun splitSentences(text: String, groupLength: Int = 192): List<String>
    fun load(files: LanguageModelTokenizerFiles, languages: LanguagePair)
}

class TranslatorService(
    private val tokenizer: TokenizerInterface,
    private val model: ModelInterface,
    cacheSize: Int = 64
) {
    private val translationCache = TranslationMemoryCache(cacheSize)
    private val queue = Mutex()

    // TODO: Make this a configuration option
    private val sentenceBatching = true

    suspend fun translate(input: String): String {
        val batchedInput = if (sentenceBatching) tokenizer.splitSentences(input) else listOf(input)

        val translations = translate(batchedInput)

        return translations.joinToString(" ")
    }

    suspend fun translate(input: List<String>): List<String> {
        // Check to see if the translation is already in the cache, if so return it.
        var cache = translationCache.get(input)
        if (cache.missing.isEmpty()) {
            return cache.cached
        }

        val output = mutableListOf<String>().apply {
            addAll(cache.cached)
        }

        return queue.withLock {
            // Check to see if the translation is already in the cache, if so return it.
            cache = translationCache.get(cache.missing)

            if (cache.missing.isEmpty()) {
                output.addAll(cache.cached)
                return output
            }

            val (inputIds, attentionMask) = tokenizer.encode(cache.missing)
            val encoderHiddenStates = model.encode(inputIds, attentionMask)

            val tokenIds =
                model.decode(encoderHiddenStates, attentionMask)

            val outputText = tokenizer.decode(tokenIds)

            translationCache.put(input, outputText)

            outputText
        }
    }

    fun load(files: LanguageModelFiles, languagePair: LanguagePair) {
        tokenizer.load(files.tokenizer, languagePair)
        model.load(files.inference)
    }

}