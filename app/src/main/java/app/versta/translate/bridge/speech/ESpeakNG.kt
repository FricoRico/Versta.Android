package app.versta.translate.bridge.speech

import android.content.Context
import app.versta.translate.R
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.FileHashValidator
import app.versta.translate.core.entity.Language
import timber.log.Timber
import kotlin.io.path.pathString

const val EXTERNAL_DATA_DIR = "external-data"
const val EXTERNAL_DATA_HASH_FILE = "external-data.sha256"
const val ESPEAK_NG_DATA_DIR = "espeak-ng-data"

interface SynthReadyCallback {
    fun onSynthDataReady(audioData: ByteArray)
    fun onSynthDataComplete()
}

class ESpeakNG private constructor() : AutoCloseable {
    private var _callback: SynthReadyCallback? = null
    private var _initialized = false

    override fun close() {
        if (!_initialized) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        terminate()
        _initialized = false
    }

    fun phonemize(text: String, language: String): String {
        if (!_initialized) {
            throw IllegalStateException("Not initialized")
        }

        return phenomize(text, language)
    }

    fun synthesize(text: String, language: Language) {
        if (!_initialized) {
            throw IllegalStateException("Not initialized")
        }

        synthesize(text, language.isoCode)
    }

    fun setCallback(callback: SynthReadyCallback) {
        if (!_initialized) {
            throw IllegalStateException("Not initialized")
        }

        _callback = callback
    }

    private fun callback(audioData: ByteArray?) {
        if (audioData == null) {
            _callback?.onSynthDataComplete()
            return
        }

        _callback?.onSynthDataReady(audioData)
    }

    fun stop() {
        if (!_initialized) {
            throw IllegalStateException("Not initialized")
        }

        _callback = null
        cancel()
    }

    private external fun construct(path: String)
    private external fun terminate()
    private external fun phenomize(text: String, language: String): String
    private external fun synthesize(text: String, language: String)
    private external fun cancel()

    companion object {
        private val TAG: String = ESpeakNG::class.java.simpleName

        private var _instance: ESpeakNG? = null

        @JvmStatic
        private external fun initialize(): Boolean

        init {
            System.loadLibrary("app_versta_translate_bridge")

            initialize()
        }

        fun createSession(
            context: Context,
            extractor: CompressedFileExtractor,
            validator: FileHashValidator
        ) {
            val instance = getSession()
            if (instance._initialized == true) {
                return
            }

            if (!validateData(context, validator)) {
                extractData(context, extractor, validator)
            }

            instance.construct(
                context.filesDir.resolve(EXTERNAL_DATA_DIR).resolve(ESPEAK_NG_DATA_DIR)
                    .toPath().pathString
            )
            instance._initialized = true
        }

        fun getSession(): ESpeakNG {
            return _instance ?: ESpeakNG().also { _instance = it }
        }

        private fun validateData(context: Context, validator: FileHashValidator): Boolean {
            return validator.validate(
                context.resources.openRawResource(R.raw.versta_data_hash),
                context.filesDir.resolve(EXTERNAL_DATA_HASH_FILE)
            )
        }

        private fun extractData(
            context: Context,
            extractor: CompressedFileExtractor,
            validator: FileHashValidator
        ) {
            extractor.extract(
                context.resources.openRawResource(R.raw.versta_data),
                context.filesDir.resolve(EXTERNAL_DATA_DIR)
            )
            validator.archive(
                context.resources.openRawResource(R.raw.versta_data_hash),
                context.filesDir.resolve(EXTERNAL_DATA_HASH_FILE)
            )
        }
    }
}