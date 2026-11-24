package app.versta.translate.bridge.utils

import timber.log.Timber

data class LanguageDetectResult(
    val language: String,
    val isReliable: Boolean,
    val confidence: Int,
)

/**
 * CLD2-backed language detector. [close] runs on the main thread from
 * ViewModel cleanup while [detectLanguage] runs on Dispatchers.Default, so
 * [handle] is `@Volatile`: a `0L` read short-circuits here, and a handle
 * that still crosses JNI after close is rejected by the guarded native
 * registry, which keeps the instance alive until the in-flight call returns.
 */
class LanguageDetect : AutoCloseable {
    @Volatile
    private var handle: Long

    init {
        handle = construct()
        if (handle == 0L) {
            throw RuntimeException("Failed to initialize LanguageDetect")
        }
    }

    fun detectLanguage(text: String): LanguageDetectResult? {
        val currentHandle = handle
        if (currentHandle == 0L) {
            return null
        }
        return detectLanguage(currentHandle, text)
    }

    override fun close() {
        val currentHandle = handle
        if (currentHandle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        handle = 0L
        if (!close(currentHandle)) {
            Timber.tag(TAG).w("Native registry rejected close of live handle %d", currentHandle)
        }
    }

    private external fun construct(): Long

    private external fun detectLanguage(
        handle: Long,
        text: String,
    ): LanguageDetectResult?

    private external fun close(handle: Long): Boolean

    companion object {
        private val TAG: String = LanguageDetect::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }

        /**
         * Registry occupancy for regression tests. Must stay `@JvmStatic` and
         * non-internal: Kotlin mangles `internal` JVM names and the symbol
         * would never link.
         */
        @JvmStatic
        external fun instanceCount(): Int
    }
}
