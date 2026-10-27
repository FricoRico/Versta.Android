package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language

class TextToSpeechMockTokenizer : TextToSpeechTokenizer {
    override fun tokenize(
        text: String,
        language: Language,
        splitSentences: Boolean,
    ): List<LongArray> =
        if (text.isBlank()) {
            emptyList()
        } else {
            listOf(longArrayOf(0))
        }
}
