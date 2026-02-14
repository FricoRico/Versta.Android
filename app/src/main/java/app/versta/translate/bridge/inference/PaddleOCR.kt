package app.versta.translate.bridge.inference

import android.graphics.PointF
import androidx.compose.ui.graphics.Color
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import timber.log.Timber
import java.nio.Buffer
import java.nio.ByteBuffer
import kotlin.collections.get

const val RECOGNIZE_HEIGHT = 48

class PaddleOCR(
    val detectSize: Int = 640,
    val recognizeSize: Int = 960,
    val cropHeight: Int = RECOGNIZE_HEIGHT,
    val maxCropSize: Int = 640,
    val unclipRatio: Float = 1.5f,
    val maxCandidates: Int = 100,
    val threads: Int = 4,
) : AutoCloseable {
    private var _handle: Long

    init {
        _handle = construct(
            detectSize = detectSize,
            recognizeSize = recognizeSize,
            cropHeight = cropHeight,
            maxCropSize = maxCropSize,
            unclipRatio = unclipRatio,
            maxCandidates = maxCandidates,
            threads = threads
        )

        if (_handle == 0L) {
            throw RuntimeException("Failed to initialize BeamSearch")
        }
    }

    private external fun construct(
        detectSize: Int,
        recognizeSize: Int,
        cropHeight: Int,
        maxCropSize: Int,
        unclipRatio: Float = 1.5f,
        maxCandidates: Int,
        threads: Int = 4,
    ): Long

    external fun close(handle: Long): Boolean

    private external fun preProcessDetect(
        handle: Long,
        input: Buffer,
        output: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        outputRotation: Int = 0,
    ): Boolean

    private external fun postProcessDetect(
        handle: Long,
        input: Buffer,
        output: Buffer,
        threshold: Float = 0.3f,
        maxValue: Float = 1f,
    ): Boolean

    private external fun preProcessRecognize(
        handle: Long,
        origin: Buffer,
        input: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
    ): Boolean

    private external fun postProcessRecognize(
        handle: Long,
        outputBuffer: Buffer,
        outputShape: LongArray,
        tokenBuffer: Buffer
    ): Boolean

    private external fun preProcessSingleCrop(
        handle: Long,
        origin: Buffer,
        boxInput: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int,
        boxIndex: Int
    ): Boolean

    private external fun getPixelColorFromImage(
        handle: Long,
        origin: Buffer,
        input: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
    ): IntArray

    // LiteRT Recognizer JNI methods
    external fun initLiteRTRecognizer(
        modelPath: String,
        useGpu: Boolean
    ): Long

    external fun runLiteRTInference(
        handle: Long,
        inputBuffer: Buffer,
        outputBuffer: Buffer,
        cropCount: Int
    ): Boolean

    external fun getLiteRTBatchSize(
        handle: Long
    ): Int

    external fun isLiteRTUsingGpu(
        handle: Long
    ): Boolean

    external fun closeLiteRTRecognizer(
        handle: Long
    )

    fun preProcessDetect(
        input: Buffer,
        output: Buffer,
        width: Int,
        height: Int,
        rotation: Int
    ): Boolean {
        return preProcessDetect(
            handle = _handle,
            input = input,
            output = output,
            inputWidth = width,
            inputHeight = height,
            outputRotation = rotation,
        )
    }

    fun postProcessDetect(
        input: Buffer,
        output: ByteBuffer,
    ): List<ObjectCharacterRecogniserResult> {
        val success = postProcessDetect(
            handle = _handle,
            input = input,
            output = output,
            threshold = 0.3f,
            maxValue = 1f,
        )

        if (!success) {
            return emptyList()
        }

        output.rewind()

        val count = output.int

        val results = mutableListOf<ObjectCharacterRecogniserResult>()
        repeat(count) {
            val points = Array(4) {
                val x = output.int
                val y = output.int
                PointF(x.toFloat(), y.toFloat())
            }
            results.add(ObjectCharacterRecogniserResult(points))
        }
        return results.reversed()
    }


    fun preProcessRecognize(
        origin: Buffer,
        input: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0
    ): Boolean {
        return preProcessRecognize(
            handle = _handle,
            origin = origin,
            input = input,
            output = output,
            originWidth = originWidth,
            originHeight = originHeight,
            originRotation = originRotation,
        )
    }

    fun postProcessRecognize(
        outputBuffer: Buffer,
        outputShape: LongArray,
        tokenBuffer: ByteBuffer
    ): List<ObjectCharacterRecogniserResult> {
        postProcessRecognize(
            handle = _handle,
            outputBuffer = outputBuffer,
            outputShape = outputShape,
            tokenBuffer = tokenBuffer
        )

        tokenBuffer.rewind()
        val count = tokenBuffer.int

        val results = mutableListOf<ObjectCharacterRecogniserResult>()
        repeat(count) {
            val tokenCount = tokenBuffer.int
            val score = (tokenBuffer.int / 1000f)
            var tokens = longArrayOf()

            repeat(tokenCount) {
                val tokenId = tokenBuffer.int.toLong()
                tokens = tokens.plus(tokenId)
            }

            results.add(
                ObjectCharacterRecogniserResult(
                    score = score,
                    tokens = tokens
                )
            )
        }

        return results
    }

    fun preProcessSingleCrop(
        origin: Buffer,
        boxInput: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int,
        boxIndex: Int
    ): Boolean {
        return preProcessSingleCrop(
            handle = _handle,
            origin = origin,
            boxInput = boxInput,
            output = output,
            originWidth = originWidth,
            originHeight = originHeight,
            originRotation = originRotation,
            boxIndex = boxIndex
        )
    }

    fun getPixelColorFromRGBAByteBuffer(
        origin: Buffer,
        input: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int,
    ): List<ObjectCharacterRecogniserColors> {
        val colors = getPixelColorFromImage(
            handle = _handle,
            origin = origin,
            input = input,
            originWidth = originWidth,
            originHeight = originHeight,
            originRotation = originRotation,
        )

        return colors.toList().chunked(2) { chunk ->
            ObjectCharacterRecogniserColors(Color(chunk[0]), Color(chunk[1]))
        }.reversed()
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("SentencePiece is already closed")
            return
        }

        close(_handle)
        _handle = 0L
    }

    companion object {
        private val TAG: String = PaddleOCR::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
