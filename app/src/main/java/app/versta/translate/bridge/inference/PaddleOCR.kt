package app.versta.translate.bridge.inference

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.Color
import app.versta.translate.MainApplication
import app.versta.translate.R
import app.versta.translate.adapter.outbound.PaddleTokenizer
import app.versta.translate.utils.DeviceUtils
import timber.log.Timber
import java.io.File
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.EnumSet

const val MAX_OBJECTS = 100

class PaddleOCR(
    private val ortEnvironment: OrtEnvironment,
    val detectWidth: Int = 640,
    val detectHeight: Int = 640,
    val recognizeWidth: Int = 960,
    val recognizeHeight: Int = 960,
    val cropWidth: Int = 640,
    val recognizeMaxBatchSize: Int = 24,
) : AutoCloseable {
    class OcrColors (
        val background: Color,
        val foreground: Color
    )
    class OcrResults(
        val points: Array<PointF>,
        var score: Float = 0f,
        var tokens: LongArray = longArrayOf(),
        var text: String = "",
        var translated: String = "",
        var colors: OcrColors = OcrColors(Color.Black, Color.White)
    )

    private var _detectSessionFile: File? = null
    private var _detectSession: OrtSession? = null

    private var _recognizeSessionFile: File? = null
    private var _recognizeSession: OrtSession? = null

    private val detectInputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(3 * detectWidth * detectHeight * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())

    // Shape: [batch_size, channels, width, height]
    val detectInputShape = longArrayOf(1, 3, detectWidth.toLong(), detectHeight.toLong())
    val detectInputTensor =
        OnnxTensor.createTensor(
            ortEnvironment,
            detectInputBuffer,
            detectInputShape,
            OnnxJavaType.FLOAT
        )

    val detectOutputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(detectWidth * detectHeight * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())

    val detectOutputShape = longArrayOf(1, 1, detectWidth.toLong(), detectHeight.toLong())
    val detectOutputTensor =
        OnnxTensor.createTensor(
            ortEnvironment,
            detectOutputBuffer,
            detectOutputShape,
            OnnxJavaType.FLOAT
        )

    val detectResultBuffer =
        ByteBuffer.allocateDirect(1 + MAX_OBJECTS * 4 * 2 * OnnxJavaType.INT32.size)
            .order(ByteOrder.nativeOrder())

    var recognizeInputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(3 * 48 * cropWidth * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())
    var recognizeOutputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(60 * 838 * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())

    val tokenizer = PaddleTokenizer()

    external fun preProcessDetect(
        input: Buffer,
        output: Buffer,
        debug: Buffer? = null,
        inputWidth: Int,
        inputHeight: Int,
        outputWidth: Int = detectWidth,
        outputHeight: Int = detectHeight,
        outputRotation: Int = 0,
        threads: Int = 4,
    ): Boolean

    external fun postProcessDetect(
        input: Buffer,
        output: Buffer,
        detectedWidth: Int = detectWidth,
        detectedHeight: Int = detectHeight,
        outputWidth: Int = recognizeWidth,
        outputHeight: Int = recognizeHeight,
        threshold: Float = 0.3f,
        maxValue: Int = 1,
        threads: Int = 4,
    ): Int

    external fun preProcessRecognize(
        origin: Buffer,
        input: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
        detectedWidth: Int = recognizeWidth,
        detectedHeight: Int = recognizeHeight,
        threads: Int = 4,
    ): Boolean

    external fun postProcessRecognize(
        outputBuffer: Buffer,
        outputShape: LongArray,
        tokenBuffer: Buffer
    ): Boolean

    external fun getPixelColorFromImage(
        origin: Buffer,
        input: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
        detectedWidth: Int = recognizeWidth,
        detectHeight: Int = recognizeHeight,
        threads: Int = 4,
    ): IntArray

    private fun preProcessDetect(imageProxy: ImageProxy): Boolean {
        return preProcessDetect(
            input = imageProxy.planes[0].buffer,
            output = detectInputBuffer,
            inputWidth = imageProxy.width,
            inputHeight = imageProxy.height,
            outputRotation = imageProxy.imageInfo.rotationDegrees,
        )
    }

    fun processCameraFrame(imageProxy: ImageProxy): Pair<List<OcrResults>, Bitmap?> {
        try {
            val success = preProcessDetect(imageProxy)
            if (!success) {
                throw IllegalStateException("Failed to preprocess camera frame.")
            }

            if (_detectSession == null) {
                throw InstantiationException("PaddleOCR session is not initialized.")
            }

            if (_recognizeSession == null) {
                throw InstantiationException("PaddleOCR recognize session is not initialized.")
            }

            val inputName = _detectSession!!.inputInfo.keys.first()
            val inputs = mapOf(inputName to detectInputTensor)

            val outputName = _detectSession!!.outputNames.first()
            val outputs = mapOf(outputName to detectOutputTensor)

            _detectSession!!.run(inputs, outputs)

            val count = postProcessDetect(
                input = detectOutputBuffer,
                output = detectResultBuffer,
            )

            if (count <= 0) {
                return Pair(emptyList(), null)
            }

            if (_recognizeSession == null) {
                throw IllegalStateException("PaddleOCR recognize session is not initialized.")
            }

            // Allocate full buffers for all detected items. We'll slice these into batches below.
            recognizeInputBuffer =
                ByteBuffer.allocateDirect(count * 3 * 48 * cropWidth * OnnxJavaType.FLOAT.size)
                    .order(ByteOrder.nativeOrder())

            recognizeOutputBuffer =
                ByteBuffer.allocateDirect(count * cropWidth / 8 * 838 * OnnxJavaType.FLOAT.size)
                    .order(ByteOrder.nativeOrder())

            preProcessRecognize(
                origin = imageProxy.planes[0].buffer,
                input = detectResultBuffer,
                output = recognizeInputBuffer,
                originWidth = imageProxy.width,
                originHeight = imageProxy.height,
                originRotation = imageProxy.imageInfo.rotationDegrees,
            )

            // We'll run the recognize session in batches not exceeding recognizeMaxBatchSize.
            val floatByteSize = OnnxJavaType.FLOAT.size
            val intByteSize = OnnxJavaType.INT32.size

            val perSampleInputBytes = 3 * 48 * cropWidth * floatByteSize
            val perSampleOutputBytes = (cropWidth / 8) * 838 * floatByteSize

            val tokensList = mutableListOf<LongArray>()
            val scoresList = mutableListOf<Float>()

            var processed = 0
            while (processed < count) {
                val batchSize = minOf(recognizeMaxBatchSize, count - processed)

                // Input slice for this batch
                val inputOffsetBytes = processed * perSampleInputBytes
                val inputSlice = recognizeInputBuffer.duplicate().order(ByteOrder.nativeOrder())
                    .apply {
                        position(inputOffsetBytes)
                        limit(inputOffsetBytes + batchSize * perSampleInputBytes)
                    }.slice().order(ByteOrder.nativeOrder())

                val batchInputShape = longArrayOf(batchSize.toLong(), 3, 48, cropWidth.toLong())

                // Output slice for this batch
                val outputOffsetBytes = processed * perSampleOutputBytes
                val outputSlice = recognizeOutputBuffer.duplicate().order(ByteOrder.nativeOrder())
                    .apply {
                        position(outputOffsetBytes)
                        limit(outputOffsetBytes + batchSize * perSampleOutputBytes)
                    }.slice().order(ByteOrder.nativeOrder())

                val batchOutputShape = longArrayOf(batchSize.toLong(), cropWidth.toLong() / 8, 838)

                val recognizeInputTensorBatch = OnnxTensor.createTensor(
                    ortEnvironment,
                    inputSlice,
                    batchInputShape,
                    OnnxJavaType.FLOAT
                )

                val recognizeOutputTensorBatch = OnnxTensor.createTensor(
                    ortEnvironment,
                    outputSlice,
                    batchOutputShape,
                    OnnxJavaType.FLOAT
                )

                val recognizeInputName = _recognizeSession!!.inputInfo.keys.first()
                val recognizeInputs = mapOf(recognizeInputName to recognizeInputTensorBatch)

                val recognizeOutputName = _recognizeSession!!.outputNames.first()
                val recognizeOutputs = mapOf(recognizeOutputName to recognizeOutputTensorBatch)

                _recognizeSession!!.run(recognizeInputs, recognizeOutputs)

                val scoreCountCapacity = batchSize * intByteSize
                val tokenCountCapacity = batchSize * intByteSize
                val tokenCapacity = batchSize * 1024 * intByteSize
                val tokenBufferBatch =
                    ByteBuffer.allocateDirect(scoreCountCapacity + tokenCountCapacity + tokenCapacity)
                        .order(ByteOrder.nativeOrder())

                postProcessRecognize(
                    outputBuffer = outputSlice,
                    outputShape = batchOutputShape,
                    tokenBuffer = tokenBufferBatch
                )

                tokenBufferBatch.rewind()
                val parsedCount = tokenBufferBatch.int
                repeat(parsedCount) {
                    val tokenCount = tokenBufferBatch.int
                    val score = (tokenBufferBatch.int / 1000f)
                    var tokens = longArrayOf()
                    repeat(tokenCount) {
                        val tokenId = tokenBufferBatch.int.toLong()
                        tokens = tokens.plus(tokenId)
                    }
                    tokensList.add(tokens)
                    scoresList.add(score)
                }

                recognizeInputTensorBatch.close()
                recognizeOutputTensorBatch.close()

                processed += batchSize
            }

            val results = unwrapDetectResultBuffer()
            val colorsList = getPixelColorFromRGBAByteBuffer(
                origin = imageProxy.planes[0].buffer,
                input = detectResultBuffer,
                originWidth = imageProxy.width,
                originHeight = imageProxy.height,
                originRotation = imageProxy.imageInfo.rotationDegrees,
            )

            if (results.size != tokensList.size || scoresList.size != tokensList.size || colorsList.size != tokensList.size) {
                throw IllegalStateException("Mismatched recognize results size")
            }

            for (i in results.indices) {
                val tokens = tokensList[i]
                val score = scoresList[i]
                val text = tokenizer.decode(tokens)
                val colors = colorsList[i]

                results[i].tokens = tokens
                results[i].text = text
                results[i].score = score
                results[i].colors = colors
            }

            return Pair(results, null)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            return Pair(emptyList(), null)
        }
    }

    fun unwrapDetectResultBuffer(): List<OcrResults> {
        detectResultBuffer.rewind()

        val count = detectResultBuffer.int

        val results = mutableListOf<OcrResults>()
        repeat(count) {
            val points = Array(4) {
                val x = detectResultBuffer.int
                val y = detectResultBuffer.int
                PointF(x.toFloat(), y.toFloat())
            }
            results.add(OcrResults(points))
        }
        return results.reversed()
    }

    override fun close() {
        try {
            _detectSession?.close()
            _recognizeSession?.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        } finally {
            _detectSession = null
            _detectSessionFile = null
            _recognizeSession = null
            _recognizeSessionFile = null
        }
    }

    fun getPixelColorFromRGBAByteBuffer(
        origin: Buffer,
        input: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int,
        detectedWidth: Int = recognizeWidth,
        detectedHeight: Int = recognizeHeight,
    ): List<OcrColors> {
        val colors = getPixelColorFromImage(
            origin,
            input,
            originWidth,
            originHeight,
            originRotation,
            detectedWidth,
            detectedHeight
        )

        return colors.toList().chunked(2) { chunk ->
            OcrColors(Color(chunk[0]), Color(chunk[1]))
        }.reversed()
    }

    fun load() {
        val detectModelStream =
            MainApplication.context.resources.openRawResource(R.raw.model_det_fp16)
        val detectModelBytes = detectModelStream.readBytes()
        val detectModelBuffer = ByteBuffer.allocateDirect(detectModelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(detectModelBytes)
            rewind()
        }

        val recognizeModelStream =
            MainApplication.context.resources.openRawResource(R.raw.model_rec_latin_fp16)
        val recognizeModelBytes = recognizeModelStream.readBytes()
        val recognizeModelBuffer = ByteBuffer.allocateDirect(recognizeModelBytes.size).apply {
            order(ByteOrder.nativeOrder())
            put(recognizeModelBytes)
            rewind()
        }

        val tokenizerFile = MainApplication.context.resources.openRawResource(R.raw.vocab_rec_latin)
        // temporarily save to a file
        val tempFile = File.createTempFile("vocab_rec_latin", ".bin")
        tokenizerFile.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        tokenizer.load(tempFile.toPath())

        close()
        val options = OrtSession.SessionOptions().apply {
            setCPUArenaAllocator(true)
            setMemoryPatternOptimization(true)
            addXnnpack(mapOf("intra_op_num_threads" to "4"))
            addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.USE_NCHW))
            DeviceUtils.isEmulator.let {
                if (it) {
                    Timber.tag(TAG).d("Emulator detected, skipping WebGPU")
                    return@let
                }

                addWebGPU(
                    mapOf(
                        "ep.webgpuexecutionprovider.preferredLayout" to "NCHW",
                        "ep.webgpuexecutionprovider.enableGraphCapture" to "1",
                    )
                )
            }
        }

        _detectSession = ortEnvironment.createSession(detectModelBuffer, options)
        _recognizeSession = ortEnvironment.createSession(recognizeModelBuffer, options)
    }

    companion object {
        private val TAG: String = PaddleOCR::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}