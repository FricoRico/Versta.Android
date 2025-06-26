package app.versta.translate.bridge.speech

import app.versta.translate.core.entity.Language
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.pathString

interface SynthReadyCallback {
    fun onSynthDataReady(audioData: ByteArray)
    fun onSynthDataComplete()
}

class ESpeakNG() : AutoCloseable {
    private var _callback: SynthReadyCallback? = null
    private var _instance: MutableStateFlow<ESpeakNG?> = MutableStateFlow(null)

    fun load(data: Path) {
        if (_instance.value != null) {
            close()
        }

        ESpeakNG().also { _instance.value = it }.construct(data.pathString)
    }

    fun isReady(): Boolean {
        return _instance.value != null
    }

    fun isReadyStateFlow(): Flow<Boolean> {
        return _instance.map { it != null }
    }

    override fun close() {
        if (_instance.value == null) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        terminate()
        _instance.value = null
    }

    fun phoneme(text: String, language: String): String {
        if (_instance.value == null) {
            throw IllegalStateException("Not initialized")
        }

        return phonemize(text, language)
    }

    fun synthesize(text: String, language: Language) {
        if (_instance.value == null) {
            throw IllegalStateException("Not initialized")
        }

        synthesize(text, language.isoCode)
    }

    fun setCallback(callback: SynthReadyCallback) {
        if (_instance.value == null) {
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
        if (_instance.value == null) {
            throw IllegalStateException("Not initialized")
        }

        _callback = null
        cancel()
    }

    private external fun construct(path: String)
    private external fun terminate()
    private external fun phonemize(text: String, language: String): String
    private external fun synthesize(text: String, language: String)
    private external fun cancel()

    companion object {
        private val TAG: String = ESpeakNG::class.java.simpleName

        @JvmStatic
        private external fun initialize(): Boolean

        init {
            System.loadLibrary("app_versta_translate_bridge")

            initialize()
        }
    }
}