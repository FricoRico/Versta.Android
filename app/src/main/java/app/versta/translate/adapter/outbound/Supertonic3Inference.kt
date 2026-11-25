package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.speech.Supertonic3Engine
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import java.nio.file.Path

internal const val MIN_SUPERTONIC_SPEED = 0.5f
internal const val MAX_SUPERTONIC_SPEED = 2.0f

/**
 * Text-to-speech over the Supertonic 3 four-model MNN pipeline.
 *
 * The selected voice style outlives the engine: [setVoice] may arrive before
 * [load] finishes (the view model drives both concurrently), and every [load]
 * replaces the native engine, which drops its style. The adapter remembers
 * the selection and binds it whenever an engine is live, so synthesis never
 * runs against an unstyled engine because of call ordering.
 */
class Supertonic3Inference(
    private val engine: Supertonic3Engine,
) : TextToSpeechInference {
    private val lock = Any()

    @Volatile
    private var loaded = false

    private var style: Path? = null

    // The engine's getter (read from the bundle's `tts.json`) is the source of
    // truth once loaded; it returns 0 before load and the published rate stands
    // in until then.
    override val sampleRate: Int
        get() = engine.sampleRate.takeIf { it > 0 } ?: Supertonic3.SAMPLE_RATE

    override fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray {
        check(loaded) { "Supertonic3 voice model is not loaded" }

        return engine.synthesize(tokens, speed.coerceIn(MIN_SUPERTONIC_SPEED, MAX_SUPERTONIC_SPEED))
    }

    override fun setVoice(path: Path?) =
        synchronized(lock) {
            style = path
            if (loaded) {
                engine.setStyle(path)
            }
        }

    override fun clearVoice() = setVoice(null)

    override fun load(
        files: VoiceModelInferenceFiles,
        threads: Int,
    ) {
        val config = requireNotNull(files.config) { "Supertonic3 model files require tts.json" }
        require(files.models.keys == Supertonic3.MODEL_STAGES) {
            "Supertonic3 model files require the four-model set, got ${files.models.keys}"
        }

        synchronized(lock) {
            loaded = false
            engine.load(files.models, config, threads)
            loaded = true

            style?.let { engine.setStyle(it) }
        }
    }

    override fun close() =
        synchronized(lock) {
            if (!loaded) {
                return@synchronized
            }

            loaded = false
            engine.close()
        }
}
