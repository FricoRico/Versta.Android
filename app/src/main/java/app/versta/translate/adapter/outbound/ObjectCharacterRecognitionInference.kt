package app.versta.translate.adapter.outbound

import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import java.nio.ByteBuffer

interface ObjectCharacterRecognitionInference {
    fun process(
        input: ImageProxy,
    ): List<ObjectCharacterRecogniserResult>

    fun cancel()

    fun load(detect: ObjectCharacterRecognitionDetectorWithFiles, recognize: ObjectCharacterRecognitionRecognizerWithFiles, threads: Int)

    fun close()

    /**
     * Get the detect result buffer containing bounding boxes
     * Used by post-processors for font metrics extraction
     */
    fun getCachedDetectResultBuffer(): ByteBuffer
}