package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceModelInferenceFiles
import java.nio.file.Path

interface TextToSpeechInference {
    /**
     * Sample rate in Hz of the PCM f32 audio [synthesize] produces. The
     * Supertonic 3 engine reads the rate from the bundle's `tts.json`, so this
     * getter is the playback pipeline's source of truth.
     */
    val sampleRate: Int

    fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray

    fun setVoice(path: Path?)

    fun clearVoice()

    fun load(
        files: VoiceModelInferenceFiles,
        threads: Int = 8,
    )

    fun close()
}
