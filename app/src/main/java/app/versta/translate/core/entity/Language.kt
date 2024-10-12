package app.versta.translate.core.entity

import android.content.Context
import app.versta.translate.utils.LocaleUtils
import java.util.Locale

data class Language(val locale: Locale) {
    val name: String = locale.displayLanguage

    fun getFlagDrawable(context: Context): Int {
        return context.resources.getIdentifier(locale.language, "drawable", context.packageName)
    }

    companion object {
        /**
         * Returns a language instance from the given locale.
         */
        fun fromLocale(locale: Locale): Language {
            return Language(locale = locale)
        }

        /**
         * Returns a language instance from the given ISO code.
         */
        fun fromIsoCode(isoCode: String): Language {
            return Language(locale = LocaleUtils.getLocale(isoCode))
        }
    }
}

data class LanguagePair(val source: Language, val target: Language) {
    val id: String = "${source.locale.language}-${target.locale.language}"
}