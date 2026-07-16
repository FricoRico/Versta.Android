package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.leanmt.LeanmtModel
import app.versta.translate.bridge.leanmt.LeanmtModelConfig
import app.versta.translate.bridge.leanmt.LeanmtPackage
import app.versta.translate.bridge.leanmt.LeanmtService
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles
import timber.log.Timber

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
    private val service: LeanmtService,
) : TranslationInference {
    private var _model: LeanmtModel? = null
    private var _loadedKey: String? = null

    override fun translate(text: String, maxBeamWidth: Int, maxSequenceLength: Int): String {
        val model = _model
            ?: throw IllegalStateException("Translation model is not loaded")

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

        val targets = service.translate(
            model,
            paragraphs.toTypedArray(),
            maxBeamWidth.toLong(),
            maxSequenceLength.toLong(),
        )
        return targets.joinToString("\n")
    }

    override fun cancel() {
        return
    }

    override fun load(files: LanguageModelFiles, config: LanguageModelConfiguration) {
        val key = files.model.toString()
        if (key == _loadedKey) {
            return
        }

        _model?.close()
        _model = null

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

        _model = LeanmtModel.create(modelConfig, pkg)
        _loadedKey = key
    }

    override fun close() {
        try {
            _model?.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        }
        try {
            service.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        } finally {
            _model = null
            _loadedKey = null
        }
    }

    companion object {
        private val TAG: String = BergamotTinyInference::class.java.simpleName
    }
}
