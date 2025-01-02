package app.versta.translate.adapter.outbound

import android.icu.text.Transliterator
import java.util.Locale


private val scriptMapping = mapOf(
    Locale.CHINESE to "Han-Latin",
    Locale.KOREAN to "Hangul-Latin",
)

class GenericTransliterator(locale: Locale) : RomanizationTransliterator {
    private val _transliterator: Transliterator

    override fun transliterate(text: String): String {
        return _transliterator.transliterate(text)
    }

    private fun isSupported(locale: Locale): Boolean {
        return scriptMapping.containsKey(locale)
    }

    private fun mapInstance(locale: Locale): String {
        if (!isSupported(locale)) {
            return "Any-Latin"
        }

        return scriptMapping.getValue(locale)
    }

    init {
        _transliterator = Transliterator.getInstance(mapInstance(locale))
    }
}