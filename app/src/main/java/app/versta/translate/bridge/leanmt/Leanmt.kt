package app.versta.translate.bridge.leanmt

import timber.log.Timber

/**
 * Owns a native blocking leanmt service and translates text through a loaded
 * [LeanmtModel]. The service caches translation state sized by [cacheSize];
 * [loadModel] may be called again to swap pairs while keeping the cache.
 */
class Leanmt(cacheSize: Long) : TranslationEngine {
    @Volatile
    private var handle: Long

    @Volatile
    private var model: LeanmtModel? = null

    init {
        handle = create(cacheSize)

        if (handle == 0L) {
            throw RuntimeException("Failed to create Leanmt")
        }
    }

    override fun loadModel(pkg: LeanmtPackage, config: LeanmtModelConfig) {
        if (handle == 0L) {
            throw RuntimeException("Service is not initialized")
        }

        model?.close()
        model = LeanmtModel(pkg, config)
    }

    override fun translate(
        texts: Array<String>,
        maxBeamWidth: Long,
        maxSequenceLength: Long,
    ): Array<String> {
        if (handle == 0L) {
            throw RuntimeException("Service is not initialized")
        }

        val current = model
            ?: throw IllegalStateException("Translation model is not loaded")

        return translate(handle, current.getHandle(), texts, maxBeamWidth, maxSequenceLength)
            ?: throw RuntimeException("Translation failed")
    }

    override fun close() {
        model?.close()
        model = null

        if (handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        destroy(handle)
        handle = 0L
    }

    private external fun create(cacheSize: Long): Long

    private external fun destroy(handle: Long)

    private external fun translate(
        handle: Long,
        modelHandle: Long,
        texts: Array<String>,
        maxBeamWidth: Long,
        maxSequenceLength: Long,
    ): Array<String>?

    companion object {
        private val TAG: String = Leanmt::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
