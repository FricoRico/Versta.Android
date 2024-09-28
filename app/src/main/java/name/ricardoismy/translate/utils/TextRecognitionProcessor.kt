package name.ricardoismy.translate.utils

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.TextRecognizerOptionsInterface


class TextRecognitionProcessor(textRecognizerOptions: TextRecognizerOptionsInterface, private val frameProcessor: (ImageProxy, Text) -> Unit):
    ImageAnalysis.Analyzer {
    private val textRecognizer: TextRecognizer = TextRecognition.getClient(textRecognizerOptions)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    frameProcessor(imageProxy, visionText)

                    imageProxy.close()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Text recognition failed", e)
                }
        }
    }

    companion object {
        private val TAG: String = Translator::class.java.simpleName
    }
}