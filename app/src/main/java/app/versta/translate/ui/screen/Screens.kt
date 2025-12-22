package app.versta.translate.ui.screen

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed class Screens : NavKey {
    @Serializable
    data object TextTranslation : Screens()

    @Serializable
    data object Vision : Screens()

    @Serializable
    data object Settings : Screens()

    @Serializable
    data object LanguageSettings : Screens()

    @Serializable
    data class LanguageDetails(val id: String) : Screens()

    @Serializable
    data object LanguageAttributions : Screens()

    @Serializable
    data object TextTranslationLegacy : Screens()

    @Serializable
    data object TranslationSettings : Screens()

    @Serializable
    data object TextToSpeechSettings : Screens()

    @Serializable
    data object VoiceSettings : Screens()

    @Serializable
    data class VoiceDetails(val id: String) : Screens()

    @Serializable
    data object VoiceAttributions : Screens()

    @Serializable
    data object ObjectCharacterRecognitionSettings : Screens()

    @Serializable
    data class ObjectCharacterRecognitionDetails(val id: String) : Screens()

    @Serializable
    data object ObjectCharacterRecognitionAttributions : Screens()

    @Serializable
    data object About : Screens()

    @Serializable
    data object ThirdParty : Screens()

    @Serializable
    data object PrivacyPolicy : Screens()

    @Serializable
    data object Troubleshooting : Screens()

    @Serializable
    data object ApplicationLogs : Screens()
}