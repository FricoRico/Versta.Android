package app.versta.translate.core.entity

/**
 * Per-language decoder priming text passed to whisper as `initial_prompt`
 * (used only as a fallback when there is no carried context to prime with —
 * see [app.versta.translate.bridge.whisper.Whisper.setCarriedContext]).
 * Each entry models the punctuation and formatting conventions we want
 * reflected in transcripts (sentence capitalization, times, currency, a
 * possessive apostrophe, a contraction, a question mark) without reading
 * like something a user of a translation app would plausibly dictate: on
 * quiet or low-confidence audio, whisper can regurgitate its own prompt text
 * verbatim, and a prompt phrased as first-person conversational speech
 * ("I'd like to ask you something about...") is then indistinguishable from
 * a real utterance. These are deliberately third-person and narrative
 * instead — the same formatting signal, a much smaller collision surface
 * with anything a user actually says.
 *
 * Keyed by ISO 639-1 language code. Kept as a Kotlin map rather than
 * `strings.xml`: this text is a decoder-tuning parameter, not UI copy, and it
 * must be keyed by the recognition source language, which is independent of
 * (and may differ from) the device/app UI locale that `strings.xml` resolves
 * against.
 */
internal object SpeechRecognitionInitialPrompts {
    private val prompts = mapOf(
        "en" to "The museum's clock tower chimed at 9:15, and by 9:30 a small " +
            "crowd had gathered near the fountain. Tickets for the afternoon " +
            "tour cost 25 euros, though the tour hadn't actually started yet. " +
            "Was it running late, one visitor wondered, or had the rain " +
            "simply held everyone up?",
        "nl" to "De klokkentoren van het museum sloeg om 9:15 uur, en om 9:30 " +
            "uur had zich al een kleine groep verzameld bij de fontein. " +
            "Kaarten voor de middagrondleiding kostten 25 euro, hoewel de " +
            "rondleiding nog niet was begonnen. Liep het uit, vroeg een " +
            "bezoeker zich af, of had de regen gewoon iedereen opgehouden?",
    )

    /**
     * Returns the priming prompt for [isoCode], or null if none is defined
     * (including when [isoCode] is null/blank, e.g. under language
     * auto-detection where no language is known yet to pick one).
     */
    fun forLanguage(isoCode: String?): String? {
        if (isoCode.isNullOrBlank()) {
            return null
        }
        return prompts[isoCode.lowercase()]
    }
}
