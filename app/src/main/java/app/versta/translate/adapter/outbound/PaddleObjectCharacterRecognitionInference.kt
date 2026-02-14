package app.versta.translate.adapter.outbound

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
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

class PaddleObjectCharacterRecognitionInference(
    private val context: Context,
    private val ortEnvironment: OrtEnvironment,
    val detectSize: Int = 640,
    val recognizeSize: Int = 960,
    val cropHeight: Int = RECOGNIZE_HEIGHT,
    val maxCropSize: Int = 960,
    val unclipRatio: Float = 1.5f,
    val maxCandidates: Int = 100,
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

    // ONNX Detector (kept as-is)
    private var _detectSessionFile: File? = null
    private var _detectSession: OrtSession? = null

    // LiteRT Recognizer (REPLACES ONNX recognizer)
    private var _recognizerLiteRT: PaddleOCRLiteRTRecognizer? = null
    private var _recognizerModelPath: String? = null

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
        var inferenceTime = 0L
        try {
            // 1. DETECTION (ONNX - unchanged)
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
                throw InstantiationException("PaddleOCR detection session is not initialized.")
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

            Timber.tag(TAG).d("Detection results: ${detectResults.size} boxes found")
            
            if (detectResults.isEmpty()) {
                return detectResults
            }

            val count = detectResults.size

            // 2. RECOGNITION (LiteRT - REPLACES ONNX)
            if (_recognizerLiteRT == null) {
                throw IllegalStateException("PaddleOCR LiteRT recognizer is not initialized.")
            }

            // 2. RECOGNITION (LiteRT with chunked preprocessing and batching)
            if (_recognizerLiteRT == null) {
                throw IllegalStateException("PaddleOCR LiteRT recognizer is not initialized.")
            }

            // Process all crops in batches with chunked preprocessing
            val inferenceStartTime = System.currentTimeMillis()
            val recognizeResults = _recognizerLiteRT!!.recognizeBatchFromDetection(
                input = input,
                detectResultBuffer = detectResultBuffer,
                cropCount = count,
                originWidth = input.width,
                originHeight = input.height,
                originRotation = input.imageInfo.rotationDegrees
            )
            inferenceTime += (System.currentTimeMillis() - inferenceStartTime)
            
            // Decode tokens to text for all results
            val resultsWithText = recognizeResults.map { result ->
                val text = tokenizer.decode(result.tokens)
                ObjectCharacterRecogniserResult(
                    score = result.score,
                    tokens = result.tokens,
                    text = text
                )
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
                recognizeResults = resultsWithText,
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
            Timber.tag(TAG).d("PaddleOCR process time: ${endTime - starTime} ms, Inference time: ${inferenceTime} ms")
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
                text = recognizeResult.text,
                colors = colors
            )

            combinedResults.add(combinedResult)
        }
        return combinedResults
    }

    override fun close() {
        try {
            _detectSession?.close()
            _recognizerLiteRT?.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
        } finally {
            _detectSession = null
            _detectSessionFile = null
            _recognizerLiteRT = null
            _recognizerModelPath = null
        }
    }

    override fun load(detect: ObjectCharacterRecognitionDetectorWithFiles, recognize: ObjectCharacterRecognitionRecognizerWithFiles, threads: Int) {
        val detectFile = File(detect.inference.model.pathString)
        val recognizeTflitePath = recognize.inference.model.pathString.replace(".ort", ".tflite")
        val recognizeTfliteFile = File(recognizeTflitePath)

        if (_detectSessionFile?.equals(detectFile) == true && _recognizerModelPath?.equals(recognizeTfliteFile) == true) {
            return
        }

        // Load tokenizer
        tokenizer.load(recognize.tokenizer.vocabulary)

        Timber.tag(TAG).d("Loading PaddleOCR models: detect=${detectFile.path}, recognize=${recognizeTflitePath}")

        close()

        val detectOptions = OrtSession.SessionOptions().apply {
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

        _detectSession = ortEnvironment.createSession(readFileToByteBuffer(detectFile), detectOptions)
        _detectSessionFile = detectFile

        // Load LiteRT Recognizer (REPLACES ONNX recognizer)
        require(recognizeTfliteFile.exists()) {
            "TFLite model not found: ${recognizeTflitePath}. Please ensure the model file exists."
        }

        _recognizerLiteRT = PaddleOCRLiteRTRecognizer(
            context = context,
            paddleOCR = _paddleOCR,
            vocabSize = tokenizer.vocabSize.toInt() + 2
        )
        _recognizerLiteRT!!.load(recognizeTflitePath)
        _recognizerModelPath = recognizeTflitePath
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
        private val TAG: String = PaddleObjectCharacterRecognitionInference::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
