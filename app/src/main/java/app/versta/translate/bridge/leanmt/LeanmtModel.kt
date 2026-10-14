package app.versta.translate.bridge.leanmt

import timber.log.Timber

/**
 * Owns a native `leanmt::Model` instance. Loads the model in [init] and
 * releases it with [close].
 */
class LeanmtModel(
    pkg: LeanmtPackage,
    config: LeanmtModelConfig,
) : AutoCloseable {
    @Volatile
    private var handle: Long

    init {
        handle =
            create(
                config.encoderLayers,
                config.decoderLayers,
                config.feedForwardDepth,
                config.numHeads,
                config.threads,
                pkg.model,
                pkg.vocabulary,
                pkg.targetVocabulary,
                pkg.shortlist,
            )

        if (handle == 0L) {
            throw RuntimeException("Failed to load leanmt model from ${pkg.model}")
        }
    }

    fun getHandle(): Long {
        if (handle == 0L) {
            throw RuntimeException("Model is not loaded")
        }
        return handle
    }

    override fun close() {
        if (handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        destroy(handle)
        handle = 0L
    }

    private external fun create(
        encoderLayers: Long,
        decoderLayers: Long,
        feedForwardDepth: Long,
        numHeads: Long,
        threads: Long,
        model: String,
        vocabulary: String,
        targetVocabulary: String,
        shortlist: String,
    ): Long

    private external fun destroy(handle: Long)

    companion object {
        private val TAG: String = LeanmtModel::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}

/**
 * Model bundle descriptor forwarded to leanmt's `leanmt::Package`. Paths
 * point at the extracted model artifacts. `targetVocabulary` is left
 * empty for shared-vocabulary models; two-vocabulary models (e.g. en-zh)
 * require it.
 */
data class LeanmtPackage(
    val model: String,
    val vocabulary: String,
    val targetVocabulary: String,
    val shortlist: String,
)

/**
 * Native model configuration. Mirrors leanmt's `leanmt::Model::Config`
 * (encoder/decoder layers, feed-forward depth, attention heads and the
 * intra-op GEMM thread budget). The original `split_mode` option is not
 * part of the pristine leanmt API and is intentionally omitted.
 *
 * [threads] maps to `Model::Config::threads`: 0 keeps the library default
 * (currently 2, overridable process-wide via the `LEANMT_THREADS` env on
 * the native side); a positive value pins the ruy pool size for the model.
 * Output is bit-identical at any thread count — only latency/battery move.
 */
data class LeanmtModelConfig(
    val encoderLayers: Long,
    val decoderLayers: Long,
    val feedForwardDepth: Long,
    val numHeads: Long,
    val threads: Long = 0,
)
