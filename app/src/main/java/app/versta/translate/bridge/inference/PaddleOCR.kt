package app.versta.translate.bridge.inference

import android.graphics.PointF
import androidx.compose.ui.graphics.Color
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import timber.log.Timber
import java.nio.Buffer
import java.nio.ByteBuffer

const val RECOGNIZE_HEIGHT = 48

data class TextRegionMetrics(
    val colors: ObjectCharacterRecogniserColors,
    val fontSize: Float,
    val lineHeight: Float,
    val fontWeight: Int
)

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

    private external fun preprocessTextRegions(
        handle: Long,
        origin: Buffer,
        input: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
    ): IntArray

    private external fun postProcessRecognize(
        handle: Long,
        outputBuffer: Buffer,
        outputShape: LongArray,
        tokenBuffer: Buffer
    ): Boolean

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

    fun preprocessTextRegions(
        origin: Buffer,
        input: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0
    ): Pair<Boolean, List<TextRegionMetrics>> {
        val metrics = preprocessTextRegions(
            handle = _handle,
            origin = origin,
            input = input,
            output = output,
            originWidth = originWidth,
            originHeight = originHeight,
            originRotation = originRotation,
        )

        val success = metrics.isNotEmpty() && metrics[0] > 0
        val count = if (metrics.isNotEmpty()) metrics[0] else 0

        val metricsList = mutableListOf<TextRegionMetrics>()
        var i = 1
        repeat(count) {
            if (i + 4 < metrics.size) {
                val packedBg = metrics[i]
                val packedTxt = metrics[i + 1]

                val bgAlpha = ((packedBg shr 24) and 0xFF) / 255f
                val bgRed = ((packedBg shr 16) and 0xFF) / 255f
                val bgGreen = ((packedBg shr 8) and 0xFF) / 255f
                val bgBlue = (packedBg and 0xFF) / 255f

                val txtAlpha = ((packedTxt shr 24) and 0xFF) / 255f
                val txtRed = ((packedTxt shr 16) and 0xFF) / 255f
                val txtGreen = ((packedTxt shr 8) and 0xFF) / 255f
                val txtBlue = (packedTxt and 0xFF) / 255f

                val bgColor = Color(bgRed, bgGreen, bgBlue, bgAlpha)
                val txtColor = Color(txtRed, txtGreen, txtBlue, txtAlpha)

                val fontSize = metrics[i + 2] / 100f
                val lineHeight = metrics[i + 3] / 100f
                val fontWeight = metrics[i + 4]

                metricsList.add(
                    TextRegionMetrics(
                        colors = ObjectCharacterRecogniserColors(bgColor, txtColor),
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontWeight = fontWeight
                    )
                )
                i += 5
            }
        }

        return Pair(success, metricsList)
    }

    fun postProcessRecognize(
        outputBuffer: Buffer,
        outputShape: LongArray,
        tokenBuffer: ByteBuffer
    ): List<ObjectCharacterRecogniserResult> {
        val success = postProcessRecognize(
            handle = _handle,
            outputBuffer = outputBuffer,
            outputShape = outputShape,
            tokenBuffer = tokenBuffer
        )

        if (!success) {
            return emptyList()
        }

        tokenBuffer.rewind()

        val batchSize = tokenBuffer.int
        val results = mutableListOf<ObjectCharacterRecogniserResult>()

        repeat(batchSize) {
            val wordCount = tokenBuffer.int
            val score = tokenBuffer.int / 1000f

            val tokens = LongArray(wordCount) {
                tokenBuffer.int.toLong()
            }

            results.add(ObjectCharacterRecogniserResult(
                score = score,
                tokens = tokens
            ))
        }

        return results
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
