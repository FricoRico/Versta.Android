package app.versta.translate.core.entity

import android.content.Context
import app.versta.translate.ApplicationModule
import app.versta.translate.MainApplication
import app.versta.translate.R
import app.versta.translate.utils.LocaleUtils
import java.util.Locale

enum class WritingDirection(
    val value: Int,
) {
    LTR(0),
    RTL(1),
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
}

data class Language(
    override val locale: Locale,
    val normalizedCode: String = normalizeLanguageCode(locale.toLanguageTag()),
) : LanguageOption {
    override val name: String = locale.displayLanguage
    override val isoCode: String = locale.language

    fun getFlagDrawable(context: Context): Int {
        val code =
            when (isoCode) {
                "iw" -> "he"
                "in" -> "id"
                else -> isoCode
            }

        return context.resources.getIdentifier(code, "drawable", context.packageName)
    }

    fun getWritingDirection(): WritingDirection =
        when (isoCode) {
            "ar" -> WritingDirection.RTL

            "iw",
            "he",
            -> WritingDirection.RTL

            else -> WritingDirection.LTR
        }

    companion object {
        /**
         * Returns a language instance from the given locale.
         */
        fun fromLocale(locale: Locale): Language = Language(locale = locale)

        /**
         * Returns a language instance from the given ISO code. The raw code is
         * normalized before the locale funnel drops script and region subtags,
         * so pair identity keeps the registry distinction (zh vs zh_hant).
         */
        fun fromIsoCode(isoCode: String): Language =
            Language(
                locale = LocaleUtils.getLocale(isoCode),
                normalizedCode = normalizeLanguageCode(isoCode),
            )
    }
}

data class LanguageOptionPair(
    val source: LanguageOption,
    val target: Language,
) {
    fun toLanguagePair(): LanguagePair? {
        if (source !is Language) {
            return null
        }

        val pair =
            LanguagePair(
                source = source,
                target = target,
            )

        if (!pair.isValid()) {
            return null
        }

        return pair
    }
}

data class LanguagePair(
    val source: Language,
    val target: Language,
) {
    /**
     * Directional pair identity in registry-normalized codes (NEU-133): catalog
     * BCP-47 codes and bundle registry codes meet through
     * [normalizeLanguageCode], never string equality.
     */
    val id: String = "${source.normalizedCode}-${target.normalizedCode}"

    fun isValid(): Boolean = source.normalizedCode != target.normalizedCode

    /**
     * Returns a string representation of the language pair.
     */
    fun uniqueId(): String =
        listOf(source.normalizedCode, target.normalizedCode)
            .sortedBy { it }
            .joinToString("-")

    /**
     * Checks if the language pair is equal to another language pair based on the non-unique ID.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LanguagePair) return false

        return other.id == id
    }

    override fun hashCode(): Int = javaClass.hashCode()

    /**
     * Checks if the language pair is equal to another language pair based on unique ID.
     */
    fun uniqueEquals(other: LanguagePair): Boolean = other.uniqueId() == uniqueId()

    companion object {
        /**
         * Returns a language pair instance from the given ISO codes.
         */
        fun fromIsoCodes(
            sourceLanguage: String,
            targetLanguage: String,
        ): LanguagePair =
            LanguagePair(
                source = Language.fromIsoCode(sourceLanguage),
                target = Language.fromIsoCode(targetLanguage),
            )

        /**
         * Returns a language pair instance from the given ID.
         */
        fun fromId(uniqueId: String): LanguagePair {
            val languages = uniqueId.split("-")
            return LanguagePair(
                source = Language.fromIsoCode(languages[0]),
                target = Language.fromIsoCode(languages[1]),
            )
        }
    }
}

data class PivotPair(
    val intermediary: LanguagePair,
    val output: LanguagePair,
)
