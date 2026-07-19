package app.versta.translate.bridge.leanmt

/**
 * Owns a native `leanmt::Model` instance. Created via [create] and released
 * with [close].
 */
class LeanmtModel private constructor() : AutoCloseable {
    private var handle: Long = 0

    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    fun getHandle(): Long {
        if (handle == 0L) {
            throw IllegalStateException("Model is not loaded")
        }
        return handle
    }

    override fun close() {
        if (handle != 0L) {
            ndestroy(handle)
            handle = 0L
        }
    }

    companion object {
        @JvmStatic
        private external fun ncreate(
            encoderLayers: Long,
            decoderLayers: Long,
            feedForwardDepth: Long,
            numHeads: Long,
            model: String,
            vocabulary: String,
            targetVocabulary: String,
            shortlist: String,
        ): Long

        @JvmStatic
        private external fun ndestroy(handle: Long)

        fun create(config: LeanmtModelConfig, pkg: LeanmtPackage): LeanmtModel {
            val model = LeanmtModel()
            model.handle = ncreate(
                config.encoderLayers,
                config.decoderLayers,
                config.feedForwardDepth,
                config.numHeads,
                pkg.model,
                pkg.vocabulary,
                pkg.targetVocabulary,
                pkg.shortlist,
            )
            return model
        }
    }
}
