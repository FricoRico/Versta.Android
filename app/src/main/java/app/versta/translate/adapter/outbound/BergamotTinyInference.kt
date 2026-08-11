package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.leanmt.TranslationEngine
import app.versta.translate.bridge.leanmt.LeanmtModelConfig
import app.versta.translate.bridge.leanmt.LeanmtPackage
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles

/**
 * Translation inference backed by the leanmt C++ library, accessed through the
 * app-provided JNI bindings (app.versta.translate.bridge.leanmt).
 *
 * leanmt performs tokenisation and detokenisation internally, so this adapter only
 * forwards raw text and returns the translated text. Pivot translation (when an
 * intermediary model is configured) is handled by the [TranslationViewModel] by
 * chaining two [translate] calls.
 */
class BergamotTinyInference(
    private val engine: TranslationEngine,
) : TranslationInference {
    private var _loadedKey: String? = null

    override fun translate(text: String, maxBeamWidth: Int, maxSequenceLength: Int): String {
        if (text.isBlank()) {
            return ""
        }

        // leanmt expects one translation unit per element; translate paragraph by
        // paragraph (newline-delimited) so we preserve structure without
        // inserting language-sensitive separators.
        val paragraphs = text.split("\n+".toRegex()).filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) {
            return ""
        }

        return engine.translate(
            paragraphs.toTypedArray(),
            maxBeamWidth.toLong(),
            maxSequenceLength.toLong(),
        ).joinToString("\n")
    }

    override fun cancel() {
        return
    }

    override fun load(files: LanguageModelFiles, config: LanguageModelConfiguration) {
        val key = files.model.toString()
        if (key == _loadedKey) {
            return
        }

        val pkg = LeanmtPackage(
            files.model.toString(),
            files.vocabulary.toString(),
            files.targetVocabulary?.toString() ?: "",
            files.shortlist.toString(),
        )

        val modelConfig = LeanmtModelConfig(
            config.encoderLayers,
            config.decoderLayers,
            config.ffnDepth,
            config.numHeads
        )

        engine.loadModel(pkg, modelConfig)
        _loadedKey = key
    }

    override fun close() {
        _loadedKey = null
        engine.close()
    }
}
