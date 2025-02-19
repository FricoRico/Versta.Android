package app.versta.translate.adapter.outbound

import android.icu.lang.UScript
import android.icu.text.Transliterator
import java.util.Locale


private val scriptMapping = mapOf(
    UScript.CYRILLIC to "Cyrillic-Latin",
    UScript.GREEK to "Greek-Latin",
    UScript.HAN to "Han-Latin",
    UScript.HANGUL to "Hangul-Latin",
    UScript.ARABIC to "Arabic-Latin",
    UScript.HEBREW to "Hebrew-Latin",
)

private val localeToScriptMapping = mapOf(
    UScript.CYRILLIC to listOf(
        Locale("be"),
        Locale("bg"),
        Locale("kk"),
        Locale("ky"),
        Locale("mk"),
        Locale("mn"),
        Locale("ru"),
        Locale("sr"),
        Locale("tg"),
        Locale("uk"),
        Locale("uz"),
    ),
    UScript.GREEK to listOf(
        Locale("el"),
    ),
    UScript.HAN to listOf(
        Locale("zh"),
    ),
    UScript.HANGUL to listOf(
        Locale("ko"),
    ),
    UScript.ARABIC to listOf(
        Locale("ar"),
        Locale("fa"),
        Locale("ps"),
        Locale("ur"),
    ),
    UScript.HEBREW to listOf(
        Locale("he"),
        Locale("yi"),
    ),
)

class GenericTransliterator(locale: Locale) : Transliteration {
    private var _transliterator: Transliterator? = null

    override fun transliterate(text: String): String {
        return _transliterator?.transliterate(text) ?: ""
    }

    private fun isSupported(script: Int): Boolean {
        return scriptMapping.containsKey(script)
    }

    private fun scriptForLocale(locale: Locale): Int {
        return localeToScriptMapping.entries.find { it.value.contains(locale) }?.key
            ?: UScript.INVALID_CODE
    }

    private fun mapInstance(locale: Locale): String? {
        val script = scriptForLocale(locale)

        if (!isSupported(script)) {
            return null
        }

        return scriptMapping.getValue(script)
    }

    init {
        mapInstance(locale)?.let {
            _transliterator = Transliterator.getInstance(it)
        }
    }
}