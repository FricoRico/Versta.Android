package app.versta.translate.adapter.outbound

import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import java.nio.ByteBuffer

/**
 * Context object passed through OCR post-processor pipeline
 * Contains image reference and OCR results
 *
 * ImageProxy is kept alive through the pipeline to allow post-processors
 * to access the original image data without re-copying
 */
data class OcrPostProcessorContext(
    val imageProxy: ImageProxy,
    val results: List<ObjectCharacterRecogniserResult>,
    val detectResultBuffer: ByteBuffer
)

