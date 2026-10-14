package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceModelTokenizerFiles

interface TextToSpeechTokenizer {
    /**
     * Loads the tokenizer files of the active voice model bundle. No-op for
     * tokenizers with a compiled-in vocabulary.
     */
    fun load(files: VoiceModelTokenizerFiles) {}

    fun tokenize(
        text: String,
        language: Language,
        splitSentences: Boolean = true,
    ): List<LongArray>
}
