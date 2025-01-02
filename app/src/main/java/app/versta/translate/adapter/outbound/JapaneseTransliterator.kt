package app.versta.translate.adapter.outbound

import fr.free.nrw.jakaroma.Jakaroma

class JapaneseTransliterator : RomanizationTransliterator {
    private val _normalizer = MosesPunctuationNormalizer(
        lang = "ja",
        preReplaceUnicodePunctuation = true,
        postRemoveControlChars = true
    )
    private val _converter = Jakaroma()
    private val _pseudoSpacesRegex = Regex("([a-zA-Z]) ([.!:?;,])")

    override fun transliterate(text: String): String {
        val converted = _converter.convert(text, true, true)
        val normalized = _normalizer.normalize(converted)

        return fixPseudoSpaces(normalized)
    }

    private fun fixPseudoSpaces(text: String): String {
        return text.replace(_pseudoSpacesRegex, "$1$2 ").trim()
    }
}