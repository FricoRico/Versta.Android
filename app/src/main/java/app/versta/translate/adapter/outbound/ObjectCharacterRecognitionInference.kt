package app.versta.translate.adapter.outbound

import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.CombinedOcrResult
import app.versta.translate.core.entity.DetectResult
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import app.versta.translate.core.entity.RecognizeResult

interface ObjectCharacterRecognitionInference {
    fun detect(input: ImageProxy): DetectResult

    fun recognize(input: ImageProxy, detectResult: DetectResult): RecognizeResult

    fun run(input: ImageProxy): CombinedOcrResult

    fun cancel()

    fun load(detect: ObjectCharacterRecognitionDetectorWithFiles, recognize: ObjectCharacterRecognitionRecognizerWithFiles, threads: Int)

    fun close()
}
