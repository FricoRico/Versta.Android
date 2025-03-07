package app.versta.translate.utils

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
import timber.log.Timber


class TextRecognitionProcessor(
    textRecognizerOptions: TextRecognizerOptionsInterface,
    private val preProcessing: () -> Unit = {},
    private val onFrameProcessed: (Text, Long) -> Unit
) :
    ImageAnalysis.Analyzer {
    private val _textRecognizer: TextRecognizer = TextRecognition.getClient(textRecognizerOptions)
    private var _shouldUpdate = true

    private val bufferSize = 16
    private val stabilityThreshold = 3
    private val temporalBuffer = ArrayDeque<String>(bufferSize)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: return
        val image = InputImage.fromMediaImage(mediaImage, 0)

        if (!_shouldUpdate) {
            imageProxy.close()
            return
        }

        preProcessing()
        _textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                addToBuffer(visionText.text)

                if (isStable()) {
                    Timber.tag(TAG).d("Text recognition stable")
                    _shouldUpdate = false
                }

                onFrameProcessed(visionText, imageProxy.imageInfo.timestamp)
            }
            .addOnFailureListener { e ->
                Timber.tag(TAG).e(e, "Text recognition failed")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun sanitizeText(text: String): String {
        return text.trimIndent().replace("\\s+".toRegex(), " ").lowercase()
    }

    private fun addToBuffer(text: String) {
        if (temporalBuffer.size >= bufferSize) {
            temporalBuffer.removeFirst()
        }

        temporalBuffer.add(text)
    }

    private fun isStable(): Boolean {
        Timber.tag(TAG).d("Checking if text recognition is stable, ${temporalBuffer.size}")

        return temporalBuffer.count {
            sanitizeText(it) == sanitizeText(temporalBuffer.last())
        } >= stabilityThreshold
    }

    fun shouldUpdate() {
        temporalBuffer.clear()
        _shouldUpdate = true
    }

    companion object {
        private val TAG: String = TextRecognitionProcessor::class.java.simpleName
    }
}