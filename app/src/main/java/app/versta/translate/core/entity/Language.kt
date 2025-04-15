package app.versta.translate.core.entity

import android.content.Context
import app.versta.translate.utils.LocaleUtils
import java.util.Locale

enum class WritingDirection(val value: Int) {
    LTR(0),
    RTL(1)
}

data class Language(val locale: Locale) {
    val name: String = locale.displayLanguage
    val isoCode: String = locale.language

    fun getFlagDrawable(context: Context): Int {
        return context.resources.getIdentifier(locale.language, "drawable", context.packageName)
    }

    fun getWritingDirection(): WritingDirection {
        return when (locale.language) {
            "ar" -> WritingDirection.RTL
            "he" -> WritingDirection.RTL
            else -> WritingDirection.LTR
        }
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

    /**
     * Returns a string representation of the language pair.
     */
    fun uniqueId(): String {
        return listOf(source.isoCode, target.isoCode)
            .sortedBy { it }
            .joinToString("-")
    }

    /**
     * Checks if the language pair is equal to another language pair based on the non-unique ID.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LanguagePair) return false

        return other.id == id
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    /**
     * Checks if the language pair is equal to another language pair based on unique ID.
     */
    fun uniqueEquals(other: LanguagePair): Boolean {
        return other.uniqueId() == uniqueId()
    }

    companion object {
        /**
         * Returns a language pair instance from the given ISO codes.
         */
        fun fromIsoCodes(sourceLanguage: String, targetLanguage: String): LanguagePair {
            return LanguagePair(
                source = Language.fromIsoCode(sourceLanguage),
                target = Language.fromIsoCode(targetLanguage)
            )
        }

        /**
         * Returns a language pair instance from the given ID.
         */
        fun fromId(uniqueId: String): LanguagePair {
            val languages = uniqueId.split("-")
            return LanguagePair(
                source = Language.fromIsoCode(languages[0]),
                target = Language.fromIsoCode(languages[1])
            )
        }
    }
}