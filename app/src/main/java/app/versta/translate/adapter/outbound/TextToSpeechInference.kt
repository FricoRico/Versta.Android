package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.TextToSpeechInferenceFiles
import java.nio.file.Path

interface TextToSpeechInference {
    fun synthesize(tokens: LongArray, speed: Float): FloatArray

    fun setVoice(path: Path?)

    fun load(files: TextToSpeechInferenceFiles, threads: Int = 8)
}