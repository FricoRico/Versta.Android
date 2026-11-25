package app.versta.translate.bridge.speech

import java.nio.file.Path

/**
 * The Supertonic 3 operations
 * [app.versta.translate.adapter.outbound.Supertonic3Inference] drives:
 * loading the four-model set, binding a voice style, and synthesizing one
 * sentence chunk at a time. Implemented by [Supertonic3]; defined as an
 * interface so the inference adapter can be tested against a fake that never
 * loads the native library.
 */
interface Supertonic3Engine : AutoCloseable {
    /** Engine output rate in Hz, read from the bundle's `tts.json`; 0 before [load]. */
    val sampleRate: Int

    /**
     * Loads the model stages keyed by name (`duration_predictor`,
     * `text_encoder`, `vector_estimator`, `vocoder`) and [config]. Loading on
     * a live engine replaces it, which unbinds the voice style.
     */
    fun load(
        models: Map<String, Path>,
        config: Path,
        threads: Int,
    )

    /**
     * Binds the voice style file at [path] for subsequent [synthesize] calls;
     * `null` unbinds it. A rejected file keeps the previous style.
     */
    fun setStyle(path: Path?)

    /**
     * Synthesizes one sentence chunk of `unicode_indexer` token ids into PCM
     * f32 at [sampleRate]; [speed] divides the predicted durations.
     */
    fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray

    override fun close()
}
