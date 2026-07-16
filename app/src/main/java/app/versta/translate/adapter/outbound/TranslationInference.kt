package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles

interface TranslationInference {
    fun translate(text: String, maxBeamWidth: Int, maxSequenceLength: Int): String

    fun cancel()

    fun load(files: LanguageModelFiles, config: LanguageModelConfiguration)

    fun close()
}
