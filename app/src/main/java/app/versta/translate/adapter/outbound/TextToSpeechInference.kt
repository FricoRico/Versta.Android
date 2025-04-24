package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceModelInferenceFiles
import java.nio.file.Path

interface TextToSpeechInference {
    fun synthesize(tokens: LongArray, speed: Float): FloatArray

    fun setVoice(path: Path?)

    fun clearVoice()

    fun load(files: VoiceModelInferenceFiles, threads: Int = 8)

    fun close()
}