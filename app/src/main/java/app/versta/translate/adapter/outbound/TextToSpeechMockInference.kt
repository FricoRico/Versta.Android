package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import java.nio.file.Path

class TextToSpeechMockInference : TextToSpeechInference {
    override val sampleRate: Int = Supertonic3.SAMPLE_RATE

    override fun synthesize(
        tokens: LongArray,
        speed: Float,
    ): FloatArray = floatArrayOf(0.0f)

    override fun setVoice(path: Path?) {
        return
    }

    override fun clearVoice() {
        return
    }

    override fun load(
        files: VoiceModelInferenceFiles,
        threads: Int,
    ) {
        return
    }

    override fun close() {
        return
    }
}
