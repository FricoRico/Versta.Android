package app.versta.translate.bridge.inference

import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color
import app.versta.translate.core.entity.FontWeight
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import timber.log.Timber
import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Text metrics for a detected text region
 */
data class OcrTextMetrics(
    val colors: ObjectCharacterRecogniserColors,
    val fontSize: Float,
    val lineHeight: Float,  // NEW: Line height for better text layout
    val fontWeight: FontWeight
)

/**
 * Object Character Recognition Text Analyzer - Extracts visual properties from text regions
 * Separate from PaddleOCR for clean separation of concerns
 *
 * @param threads Number of threads for processing
 * @param boldThreshold Edge density threshold for bold detection (0.0-1.0), default 0.20
 */
class OcrTextAnalyzer(
    threads: Int = 4,
    private var boldThreshold: Float = 0.20f
) : AutoCloseable {
    private var _handle: Long

    init {
        _handle = construct(threads, boldThreshold)
        if (_handle == 0L) {
            throw RuntimeException("Failed to initialize OcrTextAnalyzer")
        }
    }

    private external fun construct(threads: Int, boldThreshold: Float): Long
    private external fun close(handle: Long): Boolean
    private external fun setBoldThreshold(handle: Long, threshold: Float)
    private external fun getBoldThreshold(handle: Long): Float
    private external fun analyzeTextRegions(
        handle: Long,
        imageBuffer: Buffer,
        boxesBuffer: Buffer,
        imageWidth: Int,
        imageHeight: Int,
        rotation: Int,
        recognizeSize: Int
    ): IntArray

    /**
     * Set the edge density threshold for bold detection
     * Higher values = stricter bold detection
     *
     * @param threshold Edge density threshold (0.0-1.0), default 0.22
     */
    fun setBoldThreshold(threshold: Float) {
        boldThreshold = threshold
        setBoldThreshold(_handle, threshold)
    }

    /**
     * Get the current bold detection threshold
     */
    fun getBoldThreshold(): Float {
        return getBoldThreshold(_handle)
    }


    /**
     * Analyze text regions from ImageProxy and return metrics
     *
     * @param imageProxy Camera image containing text regions
     * @param boxesBuffer Buffer containing detected bounding boxes
     * @param recognizeSize Size used for OCR coordinate space
     * @return List of text metrics for each box
     */
    fun analyzeTextRegions(
        imageProxy: ImageProxy,
        boxesBuffer: ByteBuffer,
        recognizeSize: Int
    ): List<OcrTextMetrics> {
        val imageBuffer = imageProxy.planes[0].buffer
        val results = analyzeTextRegions(
            handle = _handle,
            imageBuffer = imageBuffer,
            boxesBuffer = boxesBuffer,
            imageWidth = imageProxy.width,
            imageHeight = imageProxy.height,
            rotation = imageProxy.imageInfo.rotationDegrees,
            recognizeSize = recognizeSize
        )

        return parseResults(results)
    }

    /**
     * Parse JNI int array results into OcrTextMetrics objects
     * Format: [bgColor, txtColor, fontSize*100, lineHeight*100, fontWeight] per box
     */
    private fun parseResults(results: IntArray): List<OcrTextMetrics> {
        val metrics = mutableListOf<OcrTextMetrics>()

        // Validate that results array has correct length (must be divisible by 5)
        if (results.size % 5 != 0) {
            Timber.tag(TAG).e("Invalid results array length: ${results.size}. Expected multiple of 5.")
            return emptyList()
        }

        var i = 0
        while (i < results.size) {
            try {
                val bgColor = Color(results[i])
                val txtColor = Color(results[i + 1])
                val fontSize = results[i + 2] / 100f
                val lineHeight = results[i + 3] / 100f  // NEW: Parse line height
                val fontWeightInt = results[i + 4]
                val fontWeight = FontWeight.fromInt(fontWeightInt)

                metrics.add(
                    OcrTextMetrics(
                        colors = ObjectCharacterRecogniserColors(bgColor, txtColor),
                        fontSize = if (fontSize > 0f) fontSize else 0f, // Ensure positive font size
                        lineHeight = if (lineHeight > 0f) lineHeight else 0f,  // NEW: Include line height
                        fontWeight = fontWeight
                    )
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error parsing metrics at index $i")
                // Skip this invalid entry but continue processing others
            }

            i += 5
        }

        return metrics
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("OcrTextAnalyzer is already closed")
            return
        }

        close(_handle)
        _handle = 0L
    }

    companion object {
        private val TAG: String = OcrTextAnalyzer::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}

