package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair

interface TranslationTokenizer {
    val vocabSize: Long

    val padId: Long
    val eosId: Long
    val unknownId: Long

    fun tokenize(text: String): List<String>
    fun encode(text: String, padTokens: Boolean = false): Pair<LongArray, LongArray>
    fun decode(ids: LongArray, filterSpecialTokens: Boolean = true): String
    fun splitSentences(text: String, groupLength: Int = 192): List<String>
    fun load(files: LanguageModelTokenizerFiles, languages: LanguagePair)
}