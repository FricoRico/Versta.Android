package app.versta.translate.adapter.outbound

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.MainApplication
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.core.entity.ObjectCharacterRecognitionResult

/**
 * Image analyzer that performs optical character recognition on camera frames.
 * This analyzer focuses solely on detecting and recognizing text in images,
 * without handling translation or other business logic.
 */
class ObjectCharacterRecognizerAnalyzer(
    private val onFrameProcessed: (List<ObjectCharacterRecognitionResult>, Bitmap?, Long) -> Unit
) : ImageAnalysis.Analyzer {
    private val _tokenizer = PaddleTokenizer()
    private val _paddleOCR = PaddleOCR(
        ortEnvironment = MainApplication.module.ortEnvironment,
        tokenizer = _tokenizer
    )

    init {
        _paddleOCR.load()
    }

    override fun analyze(imageProxy: ImageProxy) {
        @Suppress("DEPRECATION")
        val (results, bitmap) = _paddleOCR.processCameraFrame(imageProxy)

        val recognitionResults = results.map { oldResult ->
            ObjectCharacterRecognitionResult(
                points = oldResult.points,
                score = oldResult.score,
                tokens = oldResult.tokens,
                text = oldResult.text,
                translated = oldResult.translated,
                colors = app.versta.translate.core.entity.ObjectCharacterRecognitionColors(
                    background = oldResult.colors.background,
                    foreground = oldResult.colors.foreground
                )
            )
        }

        onFrameProcessed(
            recognitionResults,
            bitmap,
            imageProxy.imageInfo.timestamp
        )

        imageProxy.close()
    }

    fun close() {
        _paddleOCR.close()
    }

    companion object {
        private val TAG: String = ObjectCharacterRecognizerAnalyzer::class.java.simpleName
    }
}
