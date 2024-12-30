package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair

class MockTokenizer(
    override val vocabSize: Long = 0,
    override val padId: Long = 0,
    override val eosId: Long = 0,
    override val unknownId: Long = 0
) : TranslationTokenizer {
    override fun tokenize(text: String): List<String> {
        return emptyList()
    }

    override fun encode(text: String, padTokens: Boolean): Pair<LongArray, LongArray> {
        return Pair(LongArray(0), LongArray(0))
    }

    override fun decode(ids: LongArray, filterSpecialTokens: Boolean): String {
        return ""
    }

    override fun splitSentences(text: String, groupLength: Int): List<String> {
        return emptyList()
    }

    override fun load(files: LanguageModelTokenizerFiles, languages: LanguagePair) {
        return
    }
}