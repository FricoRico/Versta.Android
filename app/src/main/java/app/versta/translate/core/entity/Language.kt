package app.versta.translate.core.entity

import android.content.Context
import app.versta.translate.ApplicationModule
import app.versta.translate.MainApplication
import app.versta.translate.R
import app.versta.translate.utils.LocaleUtils
import java.util.Locale

enum class WritingDirection(val value: Int) {
    LTR(0),
    RTL(1)
}

interface LanguageOption {
    val locale: Locale?
    val name: String
    val isoCode: String
}

const val AUTO_DETECT_UNKNOWN_CODE = "un"
const val AUTO_DETECT_ISO_CODE = "auto-detect"

class AutoDetectLanguage : LanguageOption {
    override val locale: Locale? = null
    override val name: String = MainApplication.context.getString(R.string.detect_language)
    override val isoCode: String = AUTO_DETECT_ISO_CODE

    val detectedLanguage: Language? = null
}

data class Language(override val locale: Locale): LanguageOption {
    override val name: String = locale.displayLanguage
    override val isoCode: String = locale.language

    fun getFlagDrawable(context: Context): Int {
        val code = when (isoCode) {
            "iw" -> "he"
            "in" -> "id"
            else -> isoCode
        }

        return context.resources.getIdentifier(code, "drawable", context.packageName)
    }

    fun getWritingDirection(): WritingDirection {
        return when (isoCode) {
            "ar" -> WritingDirection.RTL
            "iw",
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

data class LanguageOptionPair(val source: LanguageOption, val target: Language) {
    fun toLanguagePair(): LanguagePair? {
        if (source !is Language) {
            return null
        }

        val pair = LanguagePair(
            source = source,
            target = target
        )

        if (!pair.isValid()) {
            return null
        }

        return pair
    }
}

data class LanguagePair(val source: Language, val target: Language) {
    val id: String = "${source.locale.language}-${target.locale.language}"

    fun isValid(): Boolean {
        return source.locale.language != target.locale.language
    }

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

data class PivotPair(
    val intermediary: LanguagePair,
    val output: LanguagePair
)