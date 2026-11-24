package app.versta.translate.core.model

import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.PivotPairModelFiles

/**
 * Runs and loads translations on behalf of [TextTranslationViewModel] and
 * surfaces translation failures. Implemented by [TranslationViewModel]; fakes
 * back the JVM unit tests.
 */
interface TranslationRunner {
    suspend fun load(
        files: PivotPairModelFiles?,
        languages: LanguagePair,
    )

    suspend fun translate(
        input: String,
        languages: LanguagePair,
    ): String

    fun setTranslationError(throwable: Throwable)
}
