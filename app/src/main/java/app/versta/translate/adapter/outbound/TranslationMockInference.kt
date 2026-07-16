package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles

class TranslationMockInference : TranslationInference {
    override fun translate(text: String, maxBeamWidth: Int, maxSequenceLength: Int): String {
        return ""
    }

    override fun cancel() {
        return
    }

    override fun load(files: LanguageModelFiles, config: LanguageModelConfiguration) {
        return
    }

    override fun close() {
        return
    }
}
