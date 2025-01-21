package app.versta.translate.adapter.outbound

import android.icu.text.Transliterator
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.TokenStream
import org.apache.lucene.analysis.ja.JapaneseTokenizer
import org.apache.lucene.analysis.ja.tokenattributes.ReadingAttribute
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

class KuromojiAnalyzer : Analyzer() {
    override fun createComponents(fieldName: String): TokenStreamComponents {
        val tokenizer = JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL)
        return TokenStreamComponents(tokenizer)
    }
}

class JapaneseTransliterator : Transliteration {
    private val _transliterator = Transliterator.getInstance("Hiragana-Latin; Katakana-Latin")
    private val _analyzer = KuromojiAnalyzer()

    override fun transliterate(text: String): String {
        val converted = convertToFurigana(text)

        return _transliterator.transliterate(converted)
    }

    private fun convertToFurigana(text: String): String {
        val tokenStream: TokenStream = _analyzer.tokenStream("", text)
        val tokens = mutableListOf<String>()

        val termAttr = tokenStream.addAttribute(
            CharTermAttribute::class.java
        )
        val readingAttr = tokenStream.addAttribute(
            ReadingAttribute::class.java
        )

        tokenStream.reset()
        while (tokenStream.incrementToken()) {
            val surface = termAttr.toString()
            val reading = readingAttr.reading

            tokens.add(reading ?: surface)
        }
        tokenStream.end()
        tokenStream.close()

        return combineTokens(tokens)
    }

    private fun combineTokens(tokens: List<String>): String {
        val output = StringBuilder()

        for ((index, token) in tokens.withIndex()) {
            // If this token is punctuation, append without a space
            if (token.matches(Regex("""[\p{Punct}・【】…などの日本語記号]*"""))) {
                output.append(token)
                continue
            }

            // If it's the first token or previous was punctuation, no leading space
            if (index > 0 && !tokens[index - 1].matches(Regex("""[・【】…などの日本語記号]*]*"""))) {
                output.append(" ")
            }
            output.append(token)
        }

        return output.toString()
    }

    init {
        // Warm up the analyzer so that the vocabulary is loaded and optimised, this is a workaround
        // for the first translation being slow and the analyzer not having a warm up method.
        _analyzer.tokenStream("", "")
    }
}