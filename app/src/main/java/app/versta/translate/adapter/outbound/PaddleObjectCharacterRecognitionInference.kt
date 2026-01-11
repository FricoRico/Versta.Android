package app.versta.translate.adapter.outbound

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import androidx.camera.core.ImageProxy
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import app.versta.translate.utils.DeviceUtils
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.EnumSet
import kotlin.io.path.pathString

const val RECOGNIZE_HEIGHT = 48

class PaddleObjectCharacterRecognition(
    private val ortEnvironment: OrtEnvironment,
    val detectSize: Int = 640,
    val recognizeSize: Int = 960,
    val cropHeight: Int = RECOGNIZE_HEIGHT,
    val maxCropSize: Int = 640,
    val unclipRatio: Float = 1.5f,
    val maxCandidates: Int = 100,
    val batchSize: Int = 16,
    val threads: Int = 4,
) : ObjectCharacterRecognitionInference, AutoCloseable {
    private val _paddleOCR = PaddleOCR(
        detectSize = detectSize,
        recognizeSize = recognizeSize,
        cropHeight = cropHeight,
        maxCropSize = maxCropSize,
        unclipRatio = unclipRatio,
        maxCandidates = maxCandidates,
        threads = threads
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

    val tokenizer = PaddleObjectCharacterRecognitionTokenizer()

    override fun process(input: ImageProxy): List<ObjectCharacterRecogniserResult> {
        val starTime = System.currentTimeMillis()
        try {
            val vocabSize = tokenizer.vocabSize.toInt() + 2
            val success = _paddleOCR.preProcessDetect(
                input = input.planes[0].buffer,
                output = detectInputBuffer,
                width = input.width,
                height = input.height,
                rotation = input.imageInfo.rotationDegrees,
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

            _detectSession!!.run(inputs, outputs)

            val detectResults = _paddleOCR.postProcessDetect(
                input = detectOutputBuffer,
                output = detectResultBuffer,
            )

            if (detectResults.isEmpty()) {
                return detectResults
            }

            val count = detectResults.size

            if (_recognizeSession == null) {
                throw IllegalStateException("PaddleOCR recognize session is not initialized.")
            }

            val recognizeInputBuffer =
                ByteBuffer.allocateDirect(count * 3 * 48 * maxCropSize * OnnxJavaType.FLOAT.size)
                    .order(ByteOrder.nativeOrder())

            val recognizeOutputBuffer =
                ByteBuffer.allocateDirect(count * maxCropSize / 8 * vocabSize * OnnxJavaType.FLOAT.size)
                    .order(ByteOrder.nativeOrder())

            _paddleOCR.preProcessRecognize(
                origin = input.planes[0].buffer,
                input = detectResultBuffer,
                output = recognizeInputBuffer,
                originWidth = input.width,
                originHeight = input.height,
                originRotation = input.imageInfo.rotationDegrees,
            )

            val floatByteSize = OnnxJavaType.FLOAT.size
            val intByteSize = OnnxJavaType.INT32.size

            val perSampleInputBytes = 3 * 48 * maxCropSize * floatByteSize
            val perSampleOutputBytes = (maxCropSize / 8) * vocabSize * floatByteSize

            var processed = 0
            val recognizeResults = mutableListOf<ObjectCharacterRecogniserResult>()
            while (processed < count) {
                val batchSize = minOf(batchSize, count - processed)

                val inputOffsetBytes = processed * perSampleInputBytes
                val inputSlice = recognizeInputBuffer.duplicate().order(ByteOrder.nativeOrder())
                    .apply {
                        position(inputOffsetBytes)
                        limit(inputOffsetBytes + batchSize * perSampleInputBytes)
                    }.slice().order(ByteOrder.nativeOrder())

                val batchInputShape = longArrayOf(batchSize.toLong(), 3, 48, maxCropSize.toLong())

                val outputOffsetBytes = processed * perSampleOutputBytes
                val outputSlice = recognizeOutputBuffer.duplicate().order(ByteOrder.nativeOrder())
                    .apply {
                        position(outputOffsetBytes)
                        limit(outputOffsetBytes + batchSize * perSampleOutputBytes)
                    }.slice().order(ByteOrder.nativeOrder())

                val batchOutputShape =
                    longArrayOf(batchSize.toLong(), maxCropSize.toLong() / 8, vocabSize.toLong())

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

                val results = _paddleOCR.postProcessRecognize(
                    outputBuffer = outputSlice,
                    outputShape = batchOutputShape,
                    tokenBuffer = tokenBufferBatch
                )
                recognizeResults.addAll(results)

                recognizeInputTensorBatch.close()
                recognizeOutputTensorBatch.close()

                processed += batchSize
            }

            val colorsList = _paddleOCR.getPixelColorFromRGBAByteBuffer(
                origin = input.planes[0].buffer,
                input = detectResultBuffer,
                originWidth = input.width,
                originHeight = input.height,
                originRotation = input.imageInfo.rotationDegrees,
            )

            val results = combineResults(
                detectResults = detectResults,
                recognizeResults = recognizeResults,
                colorResults = colorsList
            )

            Timber.tag(TAG).d("PaddleOCR detected ${results.size} text regions.")
            for (result in results) {
                Timber.tag(TAG).d("Text: ${result.text}, Score: ${result.score}, Colors: ${result.colors}")
            }

            return results
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            return emptyList()
        } finally {
            val endTime = System.currentTimeMillis()
            Timber.tag(TAG).d("PaddleOCR process time: ${endTime - starTime} ms")
        }
    }

    override fun cancel() {
        // TODO: Implement cancel if needed
        return
    }

    private fun combineResults(
        detectResults: List<ObjectCharacterRecogniserResult>,
        recognizeResults: List<ObjectCharacterRecogniserResult>,
        colorResults: List<ObjectCharacterRecogniserColors>
    ): List<ObjectCharacterRecogniserResult> {
        if (detectResults.size != recognizeResults.size || detectResults.size != colorResults.size) {
            throw IllegalStateException("Mismatched detect and recognize results size")
        }

        val combinedResults = mutableListOf<ObjectCharacterRecogniserResult>()
        for (i in detectResults.indices) {
            val detectResult = detectResults[i]
            val recognizeResult = recognizeResults[i]
            val colors = colorResults[i]

            val combinedResult = ObjectCharacterRecogniserResult(
                points = detectResult.points,
                score = recognizeResult.score,
                tokens = recognizeResult.tokens,
                text = tokenizer.decode(recognizeResult.tokens),
                colors = colors
            )

            combinedResults.add(combinedResult)
        }
        return combinedResults
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

    override fun load(detect: ObjectCharacterRecognitionDetectorWithFiles, recognize: ObjectCharacterRecognitionRecognizerWithFiles, threads: Int) {
        val detectFile = File(detect.inference.model.pathString)
        val recognizeFile = File(recognize.inference.model.pathString)

        if (_detectSessionFile?.equals(detectFile) == true && _recognizeSessionFile?.equals(recognizeFile) == true) {
            return
        }

        // TODO: Move to view model instead.
        tokenizer.load(recognize.tokenizer.vocabulary)

        Timber.tag(TAG).d("Loading PaddleOCR models: detect=${detectFile.path}, recognize=${recognizeFile.path}")

        close()
        val options = OrtSession.SessionOptions().apply {
//            setCPUArenaAllocator(true)
            setMemoryPatternOptimization(true)
//            setIntraOpNumThreads(threads)
//            setInterOpNumThreads(threads)
//            addXnnpack(mapOf("intra_op_num_threads" to threads.toString()))
            disableProfiling()
            addConfigEntry("optimization.enable_gelu_approximation", "1")
            addConfigEntry("session.disable_aot_function_inlining", "0")
            addConfigEntry("optimization.minimal_build_optimizations", "")
            addConfigEntry("optimization.enable_gemm_fastmath", "1")  // Faster MatMul (FP16-safe)
            addConfigEntry("ep.webgpu.enable_command_recording", "1")  // Reuse command buffers
            addConfigEntry("ep.webgpu.use_small_buffers", "0")  // Fewer memory transfers
            addConfigEntry("session.allow_released_ops_only", "1")  // Allow optimized op release

//            addConfigEntry("optimization.graph_optimizations_loop_level", "1")

            addConfigEntry("memory.enable_memory_arena_shrinkage", "")  // Keep empty for performance
            addConfigEntry("session.use_ort_model_bytes_directly", "1")
            addConfigEntry("session.use_ort_model_bytes_for_initializers", "0")
            addConfigEntry("session.set_denormal_as_zero", "1")
            addConfigEntry("session.use_env_allocators", "1")
            addConfigEntry("session.use_device_allocator_for_initializers", "1")

            addConfigEntry("ep.dynamic.workload_type", "Default")  // Performance over power
            addConfigEntry("session.qdq_matmulnbits_accuracy_level", "2") // 0:default, 1:FP32, 2:FP16, 3:BF16, 4:INT8
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

        _detectSession = ortEnvironment.createSession(readFileToByteBuffer(detectFile), options)
        _detectSessionFile = detectFile

        _recognizeSession = ortEnvironment.createSession(readFileToByteBuffer(recognizeFile), options)
        _recognizeSessionFile = recognizeFile
    }

    private fun readFileToByteBuffer(file: File): ByteBuffer {
        FileInputStream(file).use { inputStream ->
            val channel = inputStream.channel
            val size = channel.size()
            val buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, size)
            return buffer
        }
    }

    override fun getCachedDetectResultBuffer(): ByteBuffer {
        return detectResultBuffer
    }

    companion object {
        private val TAG: String = PaddleObjectCharacterRecognition::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
