package name.ricardoismy.translate.utils

import android.content.Context
import com.github.google.sentencepiece.SentencePieceProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
) {
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

    val vocabSize: Int
        get() = sourceVocabulary.size

    val eosId: Int
        get() = sourceVocabulary.indexOf(eosToken)

    val unknownId: Int
        get() = sourceVocabulary.indexOf(unknownToken)

    val padId: Int
        get() = sourceVocabulary.indexOf(padToken)

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

    fun tokenize(text: String): List<String> {
        try {
            val (code, cleanText) = removeLanguageCode(text)
            val pieces = encoder.encodeAsPieces(cleanText)
            return code + pieces
        } catch (e: Exception) {
            throw IllegalArgumentException("Tokenizing text: $text", e)
        }
    }

    fun encode(text: String, padTokens: Boolean = false): Pair<IntArray, IntArray> {
        try {
            val tokens = tokenize(text)
            var inputIds = tokens.map { convertTokenToId(it) }.toIntArray()

            // TODO: improve handling of EOS token
            val inputIdsWithEos = inputIds.toMutableList()
            inputIdsWithEos.add(eosId)

            inputIds = inputIdsWithEos.toIntArray()

            val truncatedInputIds = if (!padTokens && inputIds.size < maxInputLength) {
                inputIds
            } else if (inputIds.size > maxInputLength) {
                inputIds.copyOfRange(0, maxInputLength)
            } else {
                inputIds.copyOf(maxInputLength)
            }

            val attentionMask = IntArray(truncatedInputIds.size) { i -> if (i < inputIds.size) 1 else 0 }

            return Pair(truncatedInputIds, attentionMask)
        } catch (e: Exception) {
            throw IllegalArgumentException("Encoding text: $text", e)
        }
    }

    fun convertTokensToString(tokens: List<String>): String {
        try {
            var outputStr = ""

            val currentTokens = mutableListOf<String>()
            for (token in tokens) {
                if (token in specialTokens) {
                    outputStr += decoder.decodePieces(currentTokens) + token + " "
                    currentTokens.clear()
                    continue
                }

                currentTokens.add(token)
            }

            outputStr += decoder.decodePieces(currentTokens)
            outputStr = outputStr.replace(SENTENCE_PIECE_UNDERLINE, " ")

            return outputStr.trim()
        } catch (e: Exception) {
            throw IllegalArgumentException("Converting tokens to string: $tokens", e)
        }
    }

    fun decode(ids: IntArray, filterSpecialTokens: Boolean = true): String {
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

    private fun convertTokenToId(token: String): Int {
        val id = sourceVocabulary.indexOf(token)

        if (id == -1) {
            return sourceVocabulary.indexOf(unknownToken)
        }

        return id
    }

    private fun convertIdToToken(id: Int): String {
        if (id < 0 || id >= vocabSize) {
            return unknownToken
        }

        return targetVocabulary[id]
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