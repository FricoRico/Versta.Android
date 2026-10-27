package app.versta.translate.core.entity

import java.util.Locale

private val ZH_TRADITIONAL_SUBTAGS = setOf("hant", "tw", "hk", "mo")

/**
 * Normalizes a language code to the registry form that keys bundles and pair
 * identity (NEU-133). Kotlin port of `normalize_language` in Versta.Models
 * `translation/versta/language_codes.py`; the two must stay in lockstep.
 *
 * The catalog lists BCP-47 display codes ("zh-Hans") while bundle manifests
 * declare registry-normalized codes ("zh", "zh_hant"). Catalog and bundle
 * codes must only ever meet through this function, on both sides of a
 * comparison — never by string equality.
 */
fun normalizeLanguageCode(code: String): String {
    val parts = code.lowercase().split('-', '_')
    val base = parts.first()

    if (base == "zh") {
        return if (parts.drop(1).any { it in ZH_TRADITIONAL_SUBTAGS }) "zh_hant" else "zh"
    }

    return base
}

/**
 * Returns a locale for a registry language code. The Chinese-script
 * distinction survives as a script subtag, which
 * [Locale.forLanguageTag] cannot parse from the registry's underscore form.
 */
fun localeForLanguageCode(code: String): Locale = Locale.forLanguageTag(normalizeLanguageCode(code).replace('_', '-'))
