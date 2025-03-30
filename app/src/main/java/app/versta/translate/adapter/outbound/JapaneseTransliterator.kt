package app.versta.translate.adapter.outbound

import android.icu.text.Transliterator
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

class JapaneseTransliterator : Transliteration {
    private val _transliterator = Transliterator.getInstance("Hiragana-Latin; Katakana-Latin")
    private val _analyzer = Tokenizer.Builder().build()

    override fun transliterate(text: String): String {
        val converted = convertToFurigana(text)

        return _transliterator.transliterate(converted)
    }

    fun convertToFurigana(text: String): String {
        val tokens = _analyzer.tokenize(text)

        return combineTokens(tokens)
    }

    private fun combineTokens(tokens: List<Token>): String {
        val output = StringBuilder()

        for ((index, token) in tokens.withIndex()) {
            if (!token.isKnown) {
                // If the token is not known, append it as is
                output.append(token.surface)
                continue
            }

            // If this token is punctuation, append without a space
            if (token.reading.matches(Regex("""[\p{Punct}・【】…などの日本語記号]*"""))) {
                output.append(token.reading)
                continue
            }

            // If it's the first token or previous was punctuation, no leading space
            if (index > 0 && !tokens[index - 1].reading.matches(Regex("""[・【】…などの日本語記号]*]*"""))) {
                output.append(" ")
            }
            output.append(token.reading)
        }

        return normalizeWhitespaces(output.toString())

    }

    private fun normalizeWhitespaces(text: String): String {
        return text
            .replace("[^\\S \\n]".toRegex(), " ")
            .replace("  +".toRegex(), " ")
            .replace("(?<=\\n) +(?=\\n)".toRegex(), "")
    }

    init {
        // Warm up the analyzer so that the vocabulary is loaded and optimised, this is a workaround
        // for the first translation being slow and the analyzer not having a warm up method.
        _analyzer.tokenize("")
    }
}