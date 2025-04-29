package app.versta.translate.bridge.utils

import timber.log.Timber

data class LanguageDetectResult(
    val language: String,
    val isReliable: Boolean,
    val confidence: Int
)

class LanguageDetect : AutoCloseable {
    private var handle: Long

    init {
        handle = construct()
        if (handle == 0L) {
            throw RuntimeException("Failed to initialize LanguageDetect")
        }
    }

    fun detectLanguage(text: String): LanguageDetectResult? {
        return detectLanguage(handle, text)
    }

    override fun close() {
        if (handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        close(handle)
        handle = 0L
    }

    private external fun construct(): Long
    private external fun detectLanguage(handle: Long, text: String): LanguageDetectResult?
    private external fun close(handle: Long): Boolean

    companion object {
        private val TAG: String = LanguageDetect::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}