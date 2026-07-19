package app.versta.translate.bridge.leanmt

/**
 * Owns a native blocking leanmt service and translates text through a loaded
 * [LeanmtModel]. The service caches translation state sized by the
 * `cacheSize` passed to [create].
 */
class LeanmtService private constructor() : AutoCloseable {
    private var handle: Long = 0

    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    fun translate(
        model: LeanmtModel,
        texts: Array<String>,
        maxBeamWidth: Long,
        maxSequenceLength: Long,
    ): Array<String> {
        if (handle == 0L) {
            throw IllegalStateException("Service is not initialized")
        }
        return ntranslate(handle, model.getHandle(), texts, maxBeamWidth, maxSequenceLength)
    }

    override fun close() {
        if (handle != 0L) {
            ndestroy(handle)
            handle = 0L
        }
    }

    companion object {
        @JvmStatic
        private external fun ncreate(cacheSize: Long): Long

        @JvmStatic
        private external fun ndestroy(handle: Long)

        @JvmStatic
        private external fun ntranslate(
            serviceHandle: Long,
            modelHandle: Long,
            texts: Array<String>,
            maxBeamWidth: Long,
            maxSequenceLength: Long,
        ): Array<String>

        fun create(cacheSize: Long): LeanmtService {
            val service = LeanmtService()
            service.handle = ncreate(cacheSize)
            return service
        }
    }
}
