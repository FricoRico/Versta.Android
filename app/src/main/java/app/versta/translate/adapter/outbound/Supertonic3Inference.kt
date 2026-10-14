package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import java.nio.file.Path

internal const val MIN_SUPERTONIC_SPEED = 0.5f
internal const val MAX_SUPERTONIC_SPEED = 2.0f

/**
 * Seam to the Supertonic 3 native engine (the NEU-69 JNI bridge). Declared so
 * the inference adapter can be driven by fakes that never load native
 * libraries; the real implementation binds `bridge/speech/Supertonic3.kt`.
 *
 * One call synthesizes one sentence chunk into PCM f32 at [sampleRate]
 * (44.1 kHz). The engine applies [speed] by dividing the predicted durations,
 * matching the reference implementation (`duration /= speed`).
 */
interface Supertonic3Engine {
    val sampleRate: Int

    fun load(
        models: Map<String, Path>,
        config: Path,
        threads: Int,
    )

    fun setStyle(path: Path?)

    fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray

    fun close()
}

/**
 * Text-to-speech over the Supertonic 3 four-model MNN pipeline. Until the
 * NEU-69 bridge contract lands the engine is absent: loading reports the model
 * as unusable and synthesis fails loudly into the existing TTS error surface.
 */
class Supertonic3Inference(
    private val engine: Supertonic3Engine? = null,
) : TextToSpeechInference {
    // The engine's getter (read from the bundle's `tts.json`) is the source of
    // truth once bound; the published rate stands in until then.
    override val sampleRate: Int
        get() = engine?.sampleRate ?: Supertonic3.SAMPLE_RATE

    override fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray {
        val engine = engineUnavailable()

        return engine.synthesize(tokens, speed.coerceIn(MIN_SUPERTONIC_SPEED, MAX_SUPERTONIC_SPEED))
    }

    override fun setVoice(path: Path?) {
        engine?.setStyle(path)
    }

    override fun clearVoice() {
        engine?.setStyle(null)
    }

    override fun load(
        files: VoiceModelInferenceFiles,
        threads: Int,
    ) {
        close()

        val engine = engineUnavailable()
        val config = requireNotNull(files.config) { "Supertonic3 model files require tts.json" }
        require(files.models.isNotEmpty()) { "Supertonic3 model files require the four-model set" }

        engine.load(files.models, config, threads)
    }

    override fun close() {
        engine?.close()
    }

    private fun engineUnavailable(): Supertonic3Engine =
        checkNotNull(engine) {
            "Supertonic3 native engine is not available yet; it binds with the NEU-69 bridge contract"
        }
}
