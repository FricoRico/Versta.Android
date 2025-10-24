package app.versta.translate.adapter.outbound

import ai.onnxruntime.OnnxJavaType
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.MainApplication
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.core.entity.ObjectCharacterRecognitionResult
import app.versta.translate.core.entity.ObjectCharacterRecognizerDetectorInput
import app.versta.translate.core.entity.ObjectCharacterRecognizerRecognizerInput
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        try {
            // Allocate buffers for detection
            val detectInputBuffer = ByteBuffer.allocateDirect(
                3 * _paddleOCR.detectWidth * _paddleOCR.detectHeight * OnnxJavaType.FLOAT.size
            ).order(ByteOrder.nativeOrder())

            val detectOutputBuffer = ByteBuffer.allocateDirect(
                _paddleOCR.detectWidth * _paddleOCR.detectHeight * OnnxJavaType.FLOAT.size
            ).order(ByteOrder.nativeOrder())

            // Detect text regions
            val detectorInput = ObjectCharacterRecognizerDetectorInput(
                imageProxy = imageProxy,
                inputBuffer = detectInputBuffer,
                outputBuffer = detectOutputBuffer,
                detectWidth = _paddleOCR.detectWidth,
                detectHeight = _paddleOCR.detectHeight
            )

            val detectorOutput = _paddleOCR.detect(detectorInput)

            if (detectorOutput.boxes.isEmpty()) {
                onFrameProcessed(emptyList(), null, imageProxy.imageInfo.timestamp)
                imageProxy.close()
                return
            }

            // Allocate buffers for recognition
            val count = detectorOutput.boxes.size
            val recognizeInputBuffer = ByteBuffer.allocateDirect(
                count * 3 * 48 * _paddleOCR.cropWidth * OnnxJavaType.FLOAT.size
            ).order(ByteOrder.nativeOrder())

            val recognizeOutputBuffer = ByteBuffer.allocateDirect(
                count * (_paddleOCR.cropWidth / 8) * _tokenizer.vocabSize.toInt() * OnnxJavaType.FLOAT.size
            ).order(ByteOrder.nativeOrder())

            // Recognize text
            val recognizerInput = ObjectCharacterRecognizerRecognizerInput(
                imageProxy = imageProxy,
                detectResultBuffer = detectorOutput.detectResultBuffer,
                recognizeInputBuffer = recognizeInputBuffer,
                recognizeOutputBuffer = recognizeOutputBuffer,
                recognizeWidth = _paddleOCR.recognizeWidth,
                recognizeHeight = _paddleOCR.recognizeHeight,
                cropWidth = _paddleOCR.cropWidth,
                maxBatchSize = _paddleOCR.recognizeMaxBatchSize
            )

            val recognizerOutput = _paddleOCR.recognize(recognizerInput)

            onFrameProcessed(
                recognizerOutput.results,
                null,
                imageProxy.imageInfo.timestamp
            )
        } catch (e: Exception) {
            onFrameProcessed(emptyList(), null, imageProxy.imageInfo.timestamp)
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        _paddleOCR.close()
    }

    companion object {
        private val TAG: String = ObjectCharacterRecognizerAnalyzer::class.java.simpleName
    }
}

