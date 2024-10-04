package name.ricardoismy.translate.core.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import name.ricardoismy.translate.core.entity.TranslationCache

interface ModelInterface {
    fun encode(inputIds: Array<LongArray>, attentionMask: Array<LongArray>): Array<*>
    fun decode(encoderHiddenStates: Array<*>, attentionMask: Array<*>): Array<LongArray>
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
}

class TranslatorService(
    private val tokenizer: TokenizerInterface,
    private val model: ModelInterface,
    cacheSize: Int = 100
) {
    private val translationCache = TranslationCache(cacheSize)
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

}