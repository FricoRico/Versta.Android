package app.versta.translate.core.entity

import android.graphics.PointF
import androidx.camera.core.ImageProxy
import java.nio.Buffer

data class ObjectCharacterRecognizerDetectorInput(
    val imageProxy: ImageProxy,
    val inputBuffer: Buffer,
    val outputBuffer: Buffer,
    val detectWidth: Int,
    val detectHeight: Int
)

data class ObjectCharacterRecognizerDetectorOutput(
    val boxes: List<List<PointF>>,
    val detectResultBuffer: Buffer
)
