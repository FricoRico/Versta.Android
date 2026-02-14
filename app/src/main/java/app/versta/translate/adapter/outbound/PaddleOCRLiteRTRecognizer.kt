package app.versta.translate.adapter.outbound

import android.content.Context
import androidx.camera.core.ImageProxy
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * LiteRT-based OCR recognizer using native C++ inference.
 * Provides full control over buffer management with zero-copy operations.
 * Supports GPU/NPU acceleration with automatic CPU fallback.
 */
class PaddleOCRLiteRTRecognizer(
    private val context: Context,
    private val paddleOCR: PaddleOCR,
    private val vocabSize: Int = 838,
    private val useGpu: Boolean = true,
) : AutoCloseable {

    private var handle: Long = 0L
    private var batchSize: Int = 1
    private var usingGpu: Boolean = false
    private var maxCropSize: Int = 960
    private var cropHeight: Int = 48

    /**
     * Load the TFLite model with automatic GPU/CPU selection.
     * GPU is preferred for performance, with automatic fallback to CPU if not available.
     */
    fun load(modelPath: String) {
        val modelFile = File(modelPath)
        require(modelFile.exists()) { "TFLite model not found: $modelPath" }

        handle = paddleOCR.initLiteRTRecognizer(modelPath, useGpu)
        if (handle == 0L) {
            throw RuntimeException("Failed to initialize LiteRT recognizer")
        }

        batchSize = paddleOCR.getLiteRTBatchSize(handle)
        usingGpu = paddleOCR.isLiteRTUsingGpu(handle)

        Timber.tag(TAG).d("LiteRT Recognizer initialized with batch size: $batchSize, GPU: $usingGpu")
    }

    /**
     * Process detection results in batches.
     * Runs batched inference in native C++ with automatic batch padding.
     */
    fun recognizeBatchFromDetection(
        input: ImageProxy,
        detectResultBuffer: ByteBuffer,
        cropCount: Int,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int
    ): List<ObjectCharacterRecogniserResult> {
        val results = mutableListOf<ObjectCharacterRecogniserResult>()
        val bytesPerCrop = cropHeight * maxCropSize * 3 * 4 // float32
        val outputBytesPerCrop = (maxCropSize / 8) * vocabSize * 4

        // Allocate buffers for batched inference
        val batchInputBuffer = ByteBuffer.allocateDirect(batchSize * bytesPerCrop)
            .order(ByteOrder.nativeOrder())
        val batchOutputBuffer = ByteBuffer.allocateDirect(batchSize * outputBytesPerCrop)
            .order(ByteOrder.nativeOrder())

        // Temporary buffers for individual crop processing
        val tempInputBuffer = ByteBuffer.allocateDirect(bytesPerCrop)
            .order(ByteOrder.nativeOrder())

        var processedCount = 0
        val startTime = System.currentTimeMillis()
        var inferenceTime = 0L

        while (processedCount < cropCount) {
            val remainingCrops = cropCount - processedCount
            val currentBatchSize = minOf(batchSize, remainingCrops)

            // Clear batch input buffer
            batchInputBuffer.clear()

            // Preprocess each crop in the batch
            for (i in 0 until currentBatchSize) {
                tempInputBuffer.clear()
                val preprocessSuccess = paddleOCR.preProcessSingleCrop(
                    origin = input.planes[0].buffer,
                    boxInput = detectResultBuffer,
                    output = tempInputBuffer,
                    originWidth = originWidth,
                    originHeight = originHeight,
                    originRotation = originRotation,
                    boxIndex = processedCount + i
                )

                if (!preprocessSuccess) {
                    throw IllegalStateException("Failed to preprocess crop ${processedCount + i}")
                }

                // Copy to batch buffer
                tempInputBuffer.rewind()
                batchInputBuffer.put(tempInputBuffer)
            }

            // Run native inference (handles padding internally)
            batchInputBuffer.rewind()
            batchOutputBuffer.clear()

            val inferenceStart = System.currentTimeMillis()
            val success = paddleOCR.runLiteRTInference(
                handle = handle,
                inputBuffer = batchInputBuffer,
                outputBuffer = batchOutputBuffer,
                cropCount = currentBatchSize
            )
            inferenceTime += (System.currentTimeMillis() - inferenceStart)

            if (!success) {
                throw RuntimeException("LiteRT inference failed for batch at index $processedCount")
            }

            // Process outputs individually
            batchOutputBuffer.rewind()
            val seqLen = maxCropSize / 8

            for (i in 0 until currentBatchSize) {
                // Extract this crop's output data
                val outputStart = i * outputBytesPerCrop
                val outputSlice = batchOutputBuffer.duplicate()
                outputSlice.position(outputStart)
                outputSlice.limit(outputStart + outputBytesPerCrop)

                // Create temp buffer for this crop's output
                val tempOutputBuffer = ByteBuffer.allocateDirect(outputBytesPerCrop)
                    .order(ByteOrder.nativeOrder())
                tempOutputBuffer.put(outputSlice)
                tempOutputBuffer.flip()

                // Process with native decoder
                val tokenBuffer = ByteBuffer.allocateDirect((1 + 1024) * 4)
                    .order(ByteOrder.nativeOrder())

                val cropResults = paddleOCR.postProcessRecognize(
                    outputBuffer = tempOutputBuffer,
                    outputShape = longArrayOf(1, seqLen.toLong(), vocabSize.toLong()),
                    tokenBuffer = tokenBuffer
                )

                results.add(cropResults.firstOrNull()
                    ?: ObjectCharacterRecogniserResult(score = 0f, tokens = longArrayOf()))
            }

            processedCount += currentBatchSize
        }

        Timber.tag(TAG).d("Batch recognition completed in ${System.currentTimeMillis() - startTime} ms (inference: $inferenceTime ms) for $cropCount crops")

        return results
    }

    /**
     * Run inference on a single preprocessed crop (batch size 1).
     * For backwards compatibility.
     */
    fun recognize(cropBuffer: ByteBuffer): ObjectCharacterRecogniserResult {
        val bytesPerCrop = cropHeight * maxCropSize * 3 * 4
        val outputBytesPerCrop = (maxCropSize / 8) * vocabSize * 4

        // Allocate buffers
        val inputBuffer = ByteBuffer.allocateDirect(batchSize * bytesPerCrop)
            .order(ByteOrder.nativeOrder())
        val outputBuffer = ByteBuffer.allocateDirect(batchSize * outputBytesPerCrop)
            .order(ByteOrder.nativeOrder())

        // Copy crop data
        inputBuffer.clear()
        cropBuffer.rewind()
        inputBuffer.put(cropBuffer)
        inputBuffer.rewind()

        // Run inference
        outputBuffer.clear()
        val success = paddleOCR.runLiteRTInference(
            handle = handle,
            inputBuffer = inputBuffer,
            outputBuffer = outputBuffer,
            cropCount = 1
        )

        if (!success) {
            throw RuntimeException("LiteRT inference failed")
        }

        // Process result
        outputBuffer.rewind()
        val seqLen = maxCropSize / 8
        val tokenBuffer = ByteBuffer.allocateDirect((1 + 1024) * 4)
            .order(ByteOrder.nativeOrder())

        val results = paddleOCR.postProcessRecognize(
            outputBuffer = outputBuffer,
            outputShape = longArrayOf(1, seqLen.toLong(), vocabSize.toLong()),
            tokenBuffer = tokenBuffer
        )

        return results.first()
    }

    override fun close() {
        if (handle != 0L) {
            paddleOCR.closeLiteRTRecognizer(handle)
            handle = 0L
            Timber.tag(TAG).d("LiteRT recognizer closed")
        }
    }

    companion object {
        private const val TAG = "PaddleOCRLiteRT"
    }
}
