package app.versta.translate.core.model

import app.versta.translate.core.entity.LanguageOptionPair
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.PivotPairModelFiles
import kotlinx.coroutines.flow.Flow

/**
 * The selected-language surface the translation view models need: the chosen
 * pair, its installed model files, and auto-detect input propagation.
 * Implemented by [LanguageViewModel]; fakes back the JVM unit tests.
 */
interface TranslationLanguageProvider {
    val languageOptions: Flow<LanguageOptionPair?>
    val languagePair: Flow<LanguagePair?>
    val languageModelFiles: Flow<PivotPairModelFiles?>

    fun setAutoDetectInput(text: String)
}
