package app.versta.translate.core.entity

/**
 * Constants for the Supertonic 3 text-to-speech bundle shipped as `supertonic3`
 * in the voice-model feed. The values mirror the reference implementation at
 * https://github.com/supertone-inc/supertonic and the bundle layout produced by
 * the Versta.Models exporter.
 *
 * Languages without a Supertonic 3 model (ca, fa, he, zh) have no [VOICES]
 * mapping, so voice lookup returns nothing and TTS stays unavailable for them.
 */
object Supertonic3 {
    /**
     * Identifier of the bundle in the voice-model feed and the database.
     */
    const val ID = "supertonic3"

    /**
     * Sample rate of the vocoder output (`ae.sample_rate` in `tts.json`).
     */
    const val SAMPLE_RATE = 44_100

    /**
     * Subdirectory of the model root holding the voice-style files.
     */
    const val STYLES_DIRECTORY = "voice_styles"

    /**
     * Languages with Supertonic 3 coverage; thirty-one languages, two voice
     * genders each.
     */
    val LANGUAGES: Set<String> =
        setOf(
            "ar",
            "bg",
            "cs",
            "da",
            "de",
            "el",
            "en",
            "es",
            "et",
            "fi",
            "fr",
            "hi",
            "hr",
            "hu",
            "id",
            "it",
            "ja",
            "ko",
            "lt",
            "lv",
            "nl",
            "pl",
            "pt",
            "ro",
            "ru",
            "sk",
            "sl",
            "sv",
            "tr",
            "uk",
            "vi",
        )

    /**
     * The canonical voice mappings of the bundle: every language twice, once per
     * gender. Stored in the `Voice` table so availability follows the database
     * and not bundle file naming.
     */
    val VOICES: List<ExternalVoice> =
        LANGUAGES.sorted().flatMap { language ->
            VoiceGender.entries.map { gender -> ExternalVoice(gender, language) }
        }

    private val femaleStyles = (1..5).map { "F$it" }
    private val maleStyles = (1..5).map { "M$it" }

    /**
     * Returns true when Supertonic 3 ships a model for [language].
     */
    fun supports(language: Language): Boolean = language.isoCode in LANGUAGES

    /**
     * Fixed default style for a language and gender, spreading languages evenly
     * over the five styles per gender. Exposing all ten styles in the UI is a
     * later design pass.
     */
    fun defaultStyle(
        language: Language,
        gender: VoiceGender,
    ): String {
        val styles =
            when (gender) {
                VoiceGender.Female -> femaleStyles
                VoiceGender.Male -> maleStyles
            }

        val index = LANGUAGES.sorted().indexOf(language.isoCode)
        return styles[Math.floorMod(index, styles.size)]
    }

    /**
     * File name of a style JSON inside the model root, e.g. `F3.json`.
     */
    fun styleFileName(style: String): String = "$STYLES_DIRECTORY/$style.json"
}
