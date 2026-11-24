package app.versta.translate.adapter.outbound

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi
import com.atilika.kuromoji.ipadic.Tokenizer

class JapaneseTransliterator : Transliteration {
    @RequiresApi(Build.VERSION_CODES.Q)
    private val _transliterator = Transliterator.getInstance("Hiragana-Latin; Katakana-Latin")
    private val _analyzer = Tokenizer.Builder().build()

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun transliterate(text: String): String {
        val converted = convertToFurigana(text)

        return _transliterator.transliterate(converted)
    }

    fun convertToFurigana(text: String): String {
        val tokens = _analyzer.tokenize(text)
        val parts = tokens.map { TransliterationPart(it.surface, it.reading ?: it.surface, it.isKnown) }

        return combineTransliterationParts(parts)
    }

    init {
        // Warm up the analyzer so that the vocabulary is loaded and optimised, this is a workaround
        // for the first translation being slow and the analyzer not having a warm up method.
        _analyzer.tokenize("")
    }

    internal data class TransliterationPart(
        val surface: String,
        val reading: String,
        val isKnown: Boolean,
    )

    companion object {
        private val punctuationPattern = Regex("""[\p{Punct}・【】…などの日本語記号]*""")
        private val precedingPunctuationPattern = Regex("""[・【】…などの日本語記号]*]*""")
        private val nonStandardWhitespacePattern = Regex("[^\\S \\n]")
        private val repeatedWhitespacePattern = Regex("  +")
        private val whitespaceBetweenNewlinesPattern = Regex("(?<=\\n) +(?=\\n)")

        internal fun combineTransliterationParts(parts: List<TransliterationPart>): String {
            val output = StringBuilder()

            for ((index, part) in parts.withIndex()) {
                if (!part.isKnown) {
                    // If the token is not known, append it as is
                    output.append(part.surface)
                    continue
                }

                // If this token is punctuation, append without a space
                if (part.reading.matches(punctuationPattern)) {
                    output.append(part.reading)
                    continue
                }

                // If it's the first token or previous was punctuation, no leading space
                if (index > 0 && !parts[index - 1].reading.matches(precedingPunctuationPattern)) {
                    output.append(" ")
                }
                output.append(part.reading)
            }

            return normalizeWhitespaces(output.toString())
        }

        internal fun normalizeWhitespaces(text: String): String =
            text
                .replace(nonStandardWhitespacePattern, " ")
                .replace(repeatedWhitespacePattern, " ")
                .replace(whitespaceBetweenNewlinesPattern, "")
    }
}
