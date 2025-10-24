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

const val RECOGNIZE_HEIGHT = 48

class PaddleOCR(
    private val ortEnvironment: OrtEnvironment,
    val detectSize: Int = 640,
    val recognizeSize: Int = 960,
    val cropHeight: Int = RECOGNIZE_HEIGHT,
    val maxCropSize: Int = 640,
    val unclipRatio: Float = 1.5f,
    val maxCandidates: Int = 100,
    val batchSize: Int = 24,
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

    class OcrColors(
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
        ByteBuffer.allocateDirect(3 * detectSize * detectSize * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())

    // Shape: [batch_size, channels, width, height]
    val detectInputShape = longArrayOf(1, 3, detectSize.toLong(), detectSize.toLong())
    val detectInputTensor =
        OnnxTensor.createTensor(
            ortEnvironment,
            detectInputBuffer,
            detectInputShape,
            OnnxJavaType.FLOAT
        )

    val detectOutputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(detectSize * detectSize * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())

    val detectOutputShape = longArrayOf(1, 1, detectSize.toLong(), detectSize.toLong())
    val detectOutputTensor =
        OnnxTensor.createTensor(
            ortEnvironment,
            detectOutputBuffer,
            detectOutputShape,
            OnnxJavaType.FLOAT
        )

    val detectResultBuffer =
        ByteBuffer.allocateDirect(1 + maxCandidates * 4 * 2 * OnnxJavaType.INT32.size)
            .order(ByteOrder.nativeOrder())

    var recognizeInputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(3 * 48 * maxCropSize * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())
    var recognizeOutputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(60 * 838 * OnnxJavaType.FLOAT.size)
            .order(ByteOrder.nativeOrder())

    val tokenizer = PaddleTokenizer()

    external fun construct(
        detectSize: Int,
        recognizeSize: Int,
        cropHeight: Int,
        maxCropSize: Int,
        unclipRatio: Float = 1.5f,
        maxCandidates: Int,
        threads: Int = 4,
    ): Long

    external fun close(handle: Long): Boolean

    external fun preProcessDetect(
        handle: Long,
        input: Buffer,
        output: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        outputRotation: Int = 0,
    ): Boolean

    external fun postProcessDetect(
        handle: Long,
        input: Buffer,
        output: Buffer,
        threshold: Float = 0.3f,
        maxValue: Float = 1f,
    ): Int

    external fun preProcessRecognize(
        handle: Long,
        origin: Buffer,
        input: Buffer,
        output: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
    ): Boolean

    external fun postProcessRecognize(
        handle: Long,
        outputBuffer: Buffer,
        outputShape: LongArray,
        tokenBuffer: Buffer
    ): Boolean

    external fun getPixelColorFromImage(
        handle: Long,
        origin: Buffer,
        input: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int = 0,
    ): IntArray

    private fun preProcessDetect(input: Buffer, width: Int, height: Int, rotation: Int): Boolean {
        return preProcessDetect(
            handle = _handle,
            input = input,
            output = detectInputBuffer,
            inputWidth = width,
            inputHeight = height,
            outputRotation = rotation,
        )
    }

    fun getPixelColorFromRGBAByteBuffer(
        origin: Buffer,
        input: Buffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int,
    ): List<OcrColors> {
        val colors = getPixelColorFromImage(
            handle = _handle,
            origin = origin,
            input = input,
            originWidth = originWidth,
            originHeight = originHeight,
            originRotation = originRotation,
        )

        return colors.toList().chunked(2) { chunk ->
            OcrColors(Color(chunk[0]), Color(chunk[1]))
        }.reversed()
    }

    fun processCameraFrame(imageProxy: ImageProxy): Pair<List<OcrResults>, Bitmap?> {
        var totalInferenceTime = 0L

        try {
            val success = preProcessDetect(
                input = imageProxy.planes[0].buffer,
                width = imageProxy.width,
                height = imageProxy.height,
                rotation = imageProxy.imageInfo.rotationDegrees,
            )
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

            var startTime = System.currentTimeMillis()
            _detectSession!!.run(inputs, outputs)
            totalInferenceTime += System.currentTimeMillis() - startTime

            val count = postProcessDetect(
                handle = _handle,
                input = detectOutputBuffer,
                output = detectResultBuffer,
            )

            if (count <= 0) {
                return Pair(emptyList(), null)
            }

            if (_recognizeSession == null) {
                throw IllegalStateException("PaddleOCR recognize session is not initialized.")
            }

            recognizeInputBuffer =
                ByteBuffer.allocateDirect(count * 3 * 48 * maxCropSize * OnnxJavaType.FLOAT.size)
                    .order(ByteOrder.nativeOrder())

            recognizeOutputBuffer =
                ByteBuffer.allocateDirect(count * maxCropSize / 8 * 838 * OnnxJavaType.FLOAT.size)
                    .order(ByteOrder.nativeOrder())

            preProcessRecognize(
                handle = _handle,
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

            val perSampleInputBytes = 3 * 48 * maxCropSize * floatByteSize
            val perSampleOutputBytes = (maxCropSize / 8) * 838 * floatByteSize

            val tokensList = mutableListOf<LongArray>()
            val scoresList = mutableListOf<Float>()

            var processed = 0
            while (processed < count) {
                val batchSize = minOf(batchSize, count - processed)

                val inputOffsetBytes = processed * perSampleInputBytes
                val inputSlice = recognizeInputBuffer.duplicate().order(ByteOrder.nativeOrder())
                    .apply {
                        position(inputOffsetBytes)
                        limit(inputOffsetBytes + batchSize * perSampleInputBytes)
                    }.slice().order(ByteOrder.nativeOrder())

                val batchInputShape = longArrayOf(batchSize.toLong(), 3, 48, maxCropSize.toLong())

                // Output slice for this batch
                val outputOffsetBytes = processed * perSampleOutputBytes
                val outputSlice = recognizeOutputBuffer.duplicate().order(ByteOrder.nativeOrder())
                    .apply {
                        position(outputOffsetBytes)
                        limit(outputOffsetBytes + batchSize * perSampleOutputBytes)
                    }.slice().order(ByteOrder.nativeOrder())

                val batchOutputShape =
                    longArrayOf(batchSize.toLong(), maxCropSize.toLong() / 8, 838)

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

                startTime = System.currentTimeMillis()
                _recognizeSession!!.run(recognizeInputs, recognizeOutputs)
                totalInferenceTime += System.currentTimeMillis() - startTime

                val scoreCountCapacity = batchSize * intByteSize
                val tokenCountCapacity = batchSize * intByteSize
                val tokenCapacity = batchSize * 1024 * intByteSize
                val tokenBufferBatch =
                    ByteBuffer.allocateDirect(scoreCountCapacity + tokenCountCapacity + tokenCapacity)
                        .order(ByteOrder.nativeOrder())

                postProcessRecognize(
                    handle = _handle,
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
        } finally {
            Log.d(TAG, "Total inference time: $totalInferenceTime ms")
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
        if (_handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        close(_handle)
        _handle = 0L

        unload()
    }

    fun unload() {
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

        unload()
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
