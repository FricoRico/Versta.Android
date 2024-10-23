package app.versta.translate.core.service

import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import app.versta.translate.core.entity.TranslationMemoryCache

interface ModelInterface {
    fun run(inputIds: Array<LongArray>, attentionMask: Array<LongArray>, eosId: Long, padId: Long, maxSequenceLength: Int = 96): Array<LongArray>
    fun load(files: LanguageModelInferenceFiles)
}

interface TokenizerInterface {
    val vocabSize: Long

    val padId: Long
    val eosId: Long
    val unknownId: Long

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

class DecoderMetadata(
    val batchSize: Int,
    val sequenceLength: Int,
) {
    private val completedBatches: BooleanArray = BooleanArray(batchSize) { false }
    private var completedBatchCount: Int = 0

    fun isBatchComplete(index: Int): Boolean {
        return completedBatches[index]
    }

    fun isDoneDecoding(): Boolean {
        return completedBatchCount == batchSize
    }

    fun markBatchComplete(index: Int) {
        completedBatches[index] = true
        completedBatchCount++
    }
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
        val sanitized = input.map { sanitize(it) }

        var cache = translationCache.get(sanitized)
        if (cache.missing.isEmpty()) {
            return cache.cached
        }

        val output = mutableListOf<String>().apply {
            addAll(cache.cached)
        }

        return queue.withLock {
            // Check to see if the translation is already in the cache, if so return it.
            cache = translationCache.get(sanitized)

            if (cache.missing.isEmpty()) {
                output.addAll(cache.cached)
                return output
            }

            val (inputIds, attentionMask) = tokenizer.encode(cache.missing)
            val tokenIds = model.run(inputIds, attentionMask, tokenizer.eosId, tokenizer.padId)

            val outputText = tokenizer.decode(tokenIds)

            translationCache.put(sanitized, outputText)

            outputText
        }
    }

    private fun sanitize(input: String): String {
        val filteredString = input.filter { it.isDefined() }

        val utf8Bytes = filteredString.toByteArray(Charsets.UTF_8)

        return String(utf8Bytes, Charsets.UTF_8)
    }

    fun load(files: LanguageModelFiles, languagePair: LanguagePair) {
        tokenizer.load(files.tokenizer, languagePair)
        model.load(files.inference)
    }

}