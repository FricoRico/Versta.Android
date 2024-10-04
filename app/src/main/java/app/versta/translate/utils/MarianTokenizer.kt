package app.versta.translate.utils

import android.content.Context
import com.github.google.sentencepiece.SentencePieceProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import app.versta.translate.core.service.TokenizerInterface

class MarianTokenizer(
    context: Context,
    encoderFilePath: String,
    decoderFilePath: String,
    vocabularyFilePath: String,
    targetVocabularyFilePath: String? = null,
    private val sourceLanguage: String,
    private val targetLanguage: String,
    private val maxInputLength: Int = 512,
    private val unknownToken: String = "<unk>",
    private val eosToken: String = "</s>",
    private val padToken: String = "<pad>",
    private val separatedVocabularies: Boolean = false
): TokenizerInterface {
    companion object {
        private const val SENTENCE_PIECE_UNDERLINE = "▁"
        private val languageCodeRegex = Regex(">>.+<<")
    }

    private val encoder = SentencePieceProcessor()
    private val decoder = SentencePieceProcessor()

    private val sourceVocabulary: List<String>
    private var targetVocabulary: List<String>

    private val normalizer = MosesPunctuationNormalizer(lang = sourceLanguage)

    private val supportedLanguageCodes = mutableListOf<String>()

    init {
        sourceVocabulary = loadVocabulary(context, vocabularyFilePath)
        if (!validateVocabulary(sourceVocabulary, eosToken, padToken, unknownToken)) {
            throw IllegalArgumentException("Vocabulary does not contain the provided tokens")
        }

        if (separatedVocabularies) {
            if (targetVocabularyFilePath == null) {
                throw IllegalArgumentException("Target vocabulary file path must be provided when using separated vocabularies")
            }

            targetVocabulary = loadVocabulary(context, targetVocabularyFilePath)
            if (!validateVocabulary(targetVocabulary, eosToken, padToken, unknownToken)) {
                throw IllegalArgumentException("Target vocabulary does not contain the provided tokens")
            }
        } else {
            supportedLanguageCodes.addAll(extractLanguageCodes(sourceVocabulary))
            targetVocabulary = sourceVocabulary
        }

        val encoderModel = loadSentencePieceModel(context, encoderFilePath)
        encoder.loadFromSerializedProto(encoderModel)

        val decoderModel = loadSentencePieceModel(context, decoderFilePath)
        decoder.loadFromSerializedProto(decoderModel)
    }

    val vocabSize: Long
        get() = sourceVocabulary.size.toLong()

    val eosId: Long
        get() = sourceVocabulary.indexOf(eosToken).toLong()

    val unknownId: Long
        get() = sourceVocabulary.indexOf(unknownToken).toLong()

    val padId: Long
        get() = sourceVocabulary.indexOf(padToken).toLong()

    private val specialTokens: List<String>
        get() = listOf(unknownToken, eosToken, padToken)

    fun normalize(text: String): String {
        return normalizer.normalize(text)
    }

    private fun removeLanguageCode(text: String): Pair<List<String>, String> {
        val match = languageCodeRegex.matchEntire(text)
        val code = if (match != null) listOf(match.groupValues[0]) else emptyList()
        return Pair(code, languageCodeRegex.replace(text, ""))
    }

    override fun tokenize(text: String): List<String> {
        try {
            val (code, cleanText) = removeLanguageCode(text)
            val pieces = encoder.encodeAsPieces(cleanText)
            return code + pieces
        } catch (e: Exception) {
            throw IllegalArgumentException("Tokenizing text: $text", e)
        }
    }

    override fun encode(text: String, padTokens: Boolean): Pair<LongArray, LongArray> {
        try {
            val tokens = tokenize(text)
            val inputIds = tokens.map { convertTokenToId(it) }.toLongArray().plus(eosId)

            val truncatedInputIds = if (!padTokens && inputIds.size < maxInputLength) {
                inputIds
            } else if (inputIds.size > maxInputLength) {
                inputIds.copyOfRange(0, maxInputLength)
            } else {
                inputIds.copyOf(maxInputLength)
            }

            val attentionMask =
                LongArray(truncatedInputIds.size) { i -> if (i < inputIds.size) 1 else 0 }

            return Pair(truncatedInputIds, attentionMask)
        } catch (e: Exception) {
            throw IllegalArgumentException("Encoding text: $text", e)
        }
    }

    override fun encode(
        texts: List<String>,
        padTokens: Boolean
    ): Pair<Array<LongArray>, Array<LongArray>> {
        try {
            val inputIds = mutableListOf<LongArray>()
            val attentionMasks = mutableListOf<LongArray>()

            for (text in texts) {
                val (ids, mask) = encode(text, padTokens)
                inputIds.add(ids)
                attentionMasks.add(mask)
            }

            return padBatchSequences(inputIds, attentionMasks)
        } catch (e: Exception) {
            throw IllegalArgumentException("Batch encoding texts: $texts", e)
        }
    }

    override fun decode(ids: LongArray, filterSpecialTokens: Boolean): String {
        try {
            var tokens = ids.map { convertIdToToken(it) }

            if (filterSpecialTokens) {
                tokens = tokens.filter { it !in specialTokens }
            }

            return tokens.joinToString("").replace(SENTENCE_PIECE_UNDERLINE, " ").trim()
        } catch (e: Exception) {
            throw IllegalArgumentException("Decoding ids: $ids", e)
        }
    }

    override fun decode(
        ids: Array<LongArray>,
        filterSpecialTokens: Boolean,
    ): List<String> {
        try {
            return ids.map { decode(it, filterSpecialTokens) }
        } catch (e: Exception) {
            throw IllegalArgumentException("Batch decoding ids: $ids", e)
        }
    }

    override fun splitSentences(text: String, groupLength: Int): List<String> {
        val sentences = text.trimIndent().split("(?<=[.!?。！？])\\s+".toRegex())

        // Step 3: Group short sentences together, keep long ones separate
        val result = mutableListOf<String>()
        val currentGroup = StringBuilder()

        for (sentence in sentences) {
            if (currentGroup.length + sentence.length > groupLength) {
                if (currentGroup.isNotEmpty()) {
                    result.add(currentGroup.toString().trim())
                    currentGroup.clear()
                }

                result.add(sentence.trim())
                continue
            }

            currentGroup.append(sentence).append(" ")
        }

        if (currentGroup.isNotEmpty()) {
            result.add(currentGroup.toString().trim())
        }

        return result
    }

    private fun padBatchSequences(
        inputIdsBatch: MutableList<LongArray>,
        attentionMaskBatch: MutableList<LongArray>
    ): Pair<Array<LongArray>, Array<LongArray>> {
        val maxLength = inputIdsBatch.maxOf { it.size }

        for (i in inputIdsBatch.indices) {
            val inputIds = inputIdsBatch[i]
            val attentionMask = attentionMaskBatch[i]

            if (inputIds.size < maxLength) {
                inputIdsBatch[i] = inputIds + LongArray(maxLength - inputIds.size) { padId }
            }

            if (attentionMask.size < maxLength) {
                attentionMaskBatch[i] =
                    attentionMask + LongArray(maxLength - attentionMask.size) { 0 }
            }
        }

        return Pair(inputIdsBatch.toTypedArray(), attentionMaskBatch.toTypedArray())
    }

    private fun convertTokenToId(token: String): Long {
        val id = sourceVocabulary.indexOf(token).toLong()

        if (id == -1L) {
            return sourceVocabulary.indexOf(unknownToken).toLong()
        }

        return id
    }

    private fun convertIdToToken(id: Long): String {
        if (id < 0L || id >= vocabSize) {
            return unknownToken
        }

        return targetVocabulary[id.toInt()]
    }

    private fun loadVocabulary(context: Context, filePath: String): List<String> {
        val vocabBuffer = context.assets.open(filePath).readBytes().toString(Charsets.UTF_8)
        val vocab = Json.decodeFromString<JsonObject>(vocabBuffer).keys.toList()

        return vocab
    }

    private fun loadSentencePieceModel(context: Context, filePath: String): ByteArray {
        return context.assets.open(filePath).readBytes()
    }

    private fun extractLanguageCodes(vocabulary: List<String>): List<String> {
        if (!separatedVocabularies) {
            return emptyList()
        }

        return vocabulary.filter { it.startsWith(">>") && it.endsWith("<<") }.toList()
    }

    private fun validateVocabulary(
        vocabulary: List<String>,
        eosToken: String,
        padToken: String,
        unknownToken: String
    ): Boolean {
        return vocabulary.contains(eosToken) && vocabulary.contains(padToken) && vocabulary.contains(
            unknownToken
        )
    }
}