package app.versta.translate.utils

import android.util.Log
import java.util.Locale
import java.util.MissingResourceException

object LocaleUtils {
    private const val TAG = "LocaleUtils"

    private val languages = Locale.getISOLanguages()
    private val localeMap: MutableMap<String, Locale> = HashMap(languages.size)

    init {
        for (language in languages) {
            val locale = Locale(language)
            localeMap[language] = locale

            try {
                localeMap[locale.isO3Language] = locale
            } catch (e: MissingResourceException) {
                Log.e(TAG, "Failed to get ISO 639-2 code for language $language")
            }
        }
    }

    /**
     * Get a Locale object for the given language code. The code can be either ISO 639-1 or ISO 639-2.
     */
    fun getLocale(code: String): Locale {
        val twoLetterCode = code.substring(0, 2)
        return localeMap.getOrDefault(code, Locale(twoLetterCode))
    }
}

