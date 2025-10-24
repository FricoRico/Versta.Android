package app.versta.translate.adapter.outbound

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.ObjectCharacterRecognizerDetectorInput
import app.versta.translate.core.entity.ObjectCharacterRecognizerDetectorOutput
import app.versta.translate.core.entity.ObjectCharacterRecognizerRecognizerInput
import app.versta.translate.core.entity.ObjectCharacterRecognizerRecognizerOutput

interface ObjectCharacterRecognizerInference {
    fun detect(input: ObjectCharacterRecognizerDetectorInput): ObjectCharacterRecognizerDetectorOutput

    fun recognize(input: ObjectCharacterRecognizerRecognizerInput): ObjectCharacterRecognizerRecognizerOutput

    fun load()

    fun close()
}
