package app.versta.translate.adapter.outbound

import java.util.Locale

interface RomanizationTransliterator {
    fun transliterate(text: String): String
}

class RomanizationAdapter(locale: Locale) {
    private var _transliterator: RomanizationTransliterator? = null
    private val _latinRegex = Regex("^[\\p{IsLatin}\\p{Punct}\\p{Digit}\\s]+$")

    fun transliterate(text: String): String {
        if (isLatin(text)) {
            return ""
        }

        return _transliterator?.transliterate(text) ?: ""
    }

    private fun isLatin(text: String): Boolean {
        return _latinRegex.matches(text)
    }

    init {
        _transliterator = when (locale) {
            Locale.JAPAN,
            Locale.JAPANESE -> JapaneseTransliterator()
            else -> GenericTransliterator(locale)
        }
    }
}