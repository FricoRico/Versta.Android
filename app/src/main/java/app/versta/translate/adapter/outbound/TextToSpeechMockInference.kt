package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.TextToSpeechInferenceFiles
import java.nio.file.Path

class TextToSpeechMockInference : TextToSpeechInference {
    override fun synthesize(tokens: LongArray, speed: Float): FloatArray {
        return floatArrayOf(0.0f)
    }

    override fun setVoice(path: Path?) {
        return
    }

    override fun load(files: TextToSpeechInferenceFiles, threads: Int) {
        return
    }
}