package app.versta.translate.bridge.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * JNI bridge to the Supertonic 3 native engine (`supertonic.cc`).
 *
 * Implements the NEU-70 seam shape (`Supertonic3Engine`): one call
 * synthesizes one sentence chunk into PCM f32 at [sampleRate] (read from the
 * bundle's `tts.json` by the native engine; 0 until [load] succeeds). The
 * `Supertonic3Engine` interface itself lands with the app layer; declaring
 * `: Supertonic3Engine` there is the intended final binding.
 *
 * Lifetime (per `app/native/AGENTS.md`): create → [load] →
 * ([setStyle]/[synthesize])* → [close]. [load] on a live instance frees the
 * previous engine first. [synthesize] blocks; call it from a background
 * dispatcher, never the main thread. Native entry points serialize on an
 * engine mutex, so concurrent calls are safe and run one at a time.
 *
 * The `external` declarations below are the fixed JNI surface: one method per
 * native entry point, with the native parameter lists (the four model paths
 * are part of the C++ engine contract, so they are not collapsed into config
 * objects). That makes this bridge function-count and parameter-list heavy by
 * construction, like the other JNI bridges.
 */
@Suppress("TooManyFunctions")
class Supertonic3 : AutoCloseable {
    private var _handle: MutableStateFlow<Long?> = MutableStateFlow(null)

    /** Engine output rate in Hz, 0 before [load] succeeds. */
    val sampleRate: Int
        get() = handleOrNull()?.let { sampleRate(it) } ?: 0

    fun isReady(): Boolean = handleOrNull() != null

    fun isReadyStateFlow(): Flow<Boolean> = _handle.map { it != null && it != 0L }

    /**
     * Loads the four model stages and `tts.json`. [models] is keyed by stage
     * name (`duration_predictor`, `text_encoder`, `vector_estimator`,
     * `vocoder`); [threads] applies to every stage's runtime.
     */
    fun load(
        models: Map<String, Path>,
        config: Path,
        threads: Int,
    ) {
        val names = listOf("duration_predictor", "text_encoder", "vector_estimator", "vocoder")
        val paths =
            names.map {
                requireNotNull(models[it]) { "Supertonic3 model files are missing stage '$it'" }
            }

        val existing = handleOrNull()
        val handle =
            existing ?: construct().also {
                // The native side returns 0 for an invalid handle; never store
                // it, so isReady() cannot report a dead engine.
                if (it == 0L) {
                    error("Supertonic3 failed to construct")
                }
                _handle.value = it
            }

        if (!load(
                handle,
                paths[0].pathString,
                paths[1].pathString,
                paths[2].pathString,
                paths[3].pathString,
                config.pathString,
                threads,
            )
        ) {
            // Release the failed engine so the instance is clean for a retry.
            close(handle)
            _handle.value = null
            error("Supertonic3 failed to load (see logcat)")
        }
    }

    /**
     * Binds a voice style file for subsequent [synthesize] calls; the file is
     * validated against the loaded models and the previous style is kept on
     * failure. `null` restores the unbound state — [synthesize] then fails
     * until a style is bound again. (Seam note: the "default style" is bound
     * by the app layer right after [load]; see the NEU-69 PR.)
     */
    fun setStyle(path: Path?) {
        val handle = requireHandle()

        require(setStyle(handle, path?.pathString)) {
            "Supertonic3 rejected style file: $path"
        }
    }

    /**
     * Synthesizes one sentence chunk of `unicode_indexer` token ids into PCM
     * f32. Ids < 0 (uncovered code points) are carried through with the
     * reference's embedding wrap; [speed] is pre-clamped to [0.5, 2.0] by the
     * caller and re-clamped natively. An empty [tokens] array returns an
     * empty array; overlong input truncates with a log warning.
     */
    fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray {
        val handle = requireHandle()

        return synthesize(handle, tokens, speed)
            ?: error("Supertonic3 synthesis failed (see logcat)")
    }

    /**
     * Test hook for golden-audio CI: reseeds the latent-sampling RNG. The
     * engine is deterministic for a fixed seed + inputs.
     */
    fun setSeed(seed: Long) {
        val handle = requireHandle()

        setSeed(handle, seed)
    }

    override fun close() {
        val handle = handleOrNull()
        if (handle == null) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        close(handle)
        _handle.value = null
    }

    /**
     * Current live native handle, or null when unloaded or dead (`0L` is the
     * native invalid-handle sentinel).
     */
    private fun handleOrNull(): Long? = _handle.value?.takeIf { it != 0L }

    private fun requireHandle(): Long = checkNotNull(handleOrNull()) { "Not initialized" }

    private external fun construct(): Long

    @Suppress("LongParameterList") // four model paths are the C++ engine contract
    private external fun load(
        handle: Long,
        durationPredictor: String,
        textEncoder: String,
        vectorEstimator: String,
        vocoder: String,
        config: String,
        threads: Int,
    ): Boolean

    private external fun setStyle(
        handle: Long,
        path: String?,
    ): Boolean

    private external fun synthesize(
        handle: Long,
        tokens: LongArray,
        speed: Float,
    ): FloatArray?

    private external fun sampleRate(handle: Long): Int

    private external fun setSeed(
        handle: Long,
        seed: Long,
    )

    private external fun close(handle: Long): Boolean

    companion object {
        private val TAG: String = Supertonic3::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
