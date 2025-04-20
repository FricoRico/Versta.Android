package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceModelInferenceFiles
import java.nio.file.Path

class TextToSpeechMockInference : TextToSpeechInference {
    override fun synthesize(tokens: LongArray, speed: Float): FloatArray {
        return floatArrayOf(0.0f)
    }

    override fun setVoice(path: Path?) {
        return
    }

    override fun load(files: VoiceModelInferenceFiles, threads: Int) {
        return
    }
}