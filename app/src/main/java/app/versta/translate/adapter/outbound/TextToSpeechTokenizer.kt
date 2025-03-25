package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language

interface TextToSpeechTokenizer {
    fun tokenize(text: String, language: Language, splitSentences: Boolean = true): List<LongArray>
}