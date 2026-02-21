package app.versta.translate.adapter.outbound

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions.OptLevel
import ai.onnxruntime.providers.NNAPIFlags
import androidx.camera.core.ImageProxy
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.bridge.inference.TextRegionMetrics
import app.versta.translate.core.entity.CombinedOcrRegion
import app.versta.translate.core.entity.CombinedOcrResult
import app.versta.translate.core.entity.DetectResult
import app.versta.translate.core.entity.DetectedRegion
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import app.versta.translate.core.entity.PaddleObjectCharacterRecognitionDetectInput
import app.versta.translate.core.entity.PaddleObjectCharacterRecognitionDetectOutput
import app.versta.translate.core.entity.PaddleObjectCharacterRecognitionRecognizeInput
import app.versta.translate.core.entity.PaddleObjectCharacterRecognitionRecognizeOutput
import app.versta.translate.core.entity.RecognizeResult
import app.versta.translate.core.entity.RecognizedRegion
import app.versta.translate.utils.DeviceUtils
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
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
    val batchSize: Int = 24,
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

    override fun detect(input: ImageProxy): DetectResult {
        if (_detectSession == null) {
            throw InstantiationException("PaddleOCR detect session is not initialized.")
        }

        val detectInput = PaddleObjectCharacterRecognitionDetectInput(
            ortEnvironment = ortEnvironment,
            paddleOCR = _paddleOCR,
            detectSize = detectSize
        )

        val detectOutput = PaddleObjectCharacterRecognitionDetectOutput(
            ortEnvironment = ortEnvironment,
            paddleOCR = _paddleOCR,
            detectSize = detectSize,
            maxCandidates = maxCandidates
        )

        var inferenceTime = 0L

        try {
            val success = detectInput.preprocess(
                input = input.planes[0].buffer,
                width = input.width,
                height = input.height,
                rotation = input.imageInfo.rotationDegrees
            )
            if (!success) {
                throw IllegalStateException("Failed to preprocess camera frame.")
            }

            val inputs = detectInput.get()
            val startTime = System.currentTimeMillis()
            detectOutput.run(_detectSession!!, inputs)
            inferenceTime += System.currentTimeMillis() - startTime

            val regions = detectOutput.parse().map { result ->
                DetectedRegion(points = result.points)
            }

            return DetectResult(
                regions = regions,
                resultBuffer = detectOutput.resultBuffer
            )
        } finally {
            detectInput.destroy()
            detectOutput.destroy()

            Timber.tag(TAG).d("Paddle detector inference time: $inferenceTime ms")
        }
    }

    override fun recognize(input: ImageProxy, detectResult: DetectResult): RecognizeResult {
        if (_recognizeSession == null) {
            throw InstantiationException("PaddleOCR recognize session is not initialized.")
        }

        val recognizeInput = PaddleObjectCharacterRecognitionRecognizeInput(
            ortEnvironment = ortEnvironment,
            paddleOCR = _paddleOCR,
            maxBatchSize = maxCandidates,
            cropHeight = cropHeight,
            maxCropSize = maxCropSize
        )

        val recognizeOutput = PaddleObjectCharacterRecognitionRecognizeOutput(
            ortEnvironment = ortEnvironment,
            paddleOCR = _paddleOCR,
            session = _recognizeSession!!,
            maxCandidates = maxCandidates,
            maxCropSize = maxCropSize
        )

        var inferenceTime = 0L

        try {
            val (success, metrics) = recognizeInput.preprocess(
                origin = input.planes[0].buffer,
                detectResultBuffer = detectResult.resultBuffer,
                originWidth = input.width,
                originHeight = input.height,
                originRotation = input.imageInfo.rotationDegrees
            )

            if (!success) {
                throw IllegalStateException("Failed to preprocess recognition frames.")
            }

            val regions = detectResult.regions.size
            val results = mutableListOf<RecognizedRegion>()
            var processed = 0

            while (processed < regions) {
                val currentBatchSize = minOf(batchSize, regions - processed)

                val recognizeInputs = recognizeInput.get(processed, currentBatchSize)
                val startTime = System.currentTimeMillis()
                recognizeOutput.run(_recognizeSession!!, recognizeInputs, processed, currentBatchSize)
                inferenceTime += System.currentTimeMillis() - startTime

                val batchResults = recognizeOutput.parse(processed, currentBatchSize)
                results.addAll(batchResults.map { result ->
                    RecognizedRegion(
                        tokens = result.tokens,
                        score = result.score,
                    )
                })

                processed += currentBatchSize
            }

            return combineWithMetrics(results, metrics)
        } finally {
            recognizeInput.destroy()
            recognizeOutput.destroy()

            Timber.tag(TAG).d("Paddle recognizer inference time: $inferenceTime ms")
        }
    }

    override fun run(input: ImageProxy): CombinedOcrResult {
        val startTime = System.currentTimeMillis()

        try {
            val detectResult = detect(input)

            if (detectResult.regions.isEmpty()) {
                return CombinedOcrResult(regions = emptyList())
            }

            val recognizeResult = recognize(input, detectResult)

            return combineResults(detectResult, recognizeResult)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e)
            return CombinedOcrResult(regions = emptyList())
        } finally {
            Timber.tag(TAG).d("PaddleOCR process time: ${System.currentTimeMillis() - startTime} ms")
        }
    }

    override fun cancel() {
        return
    }

    private fun combineWithMetrics(
        regions: List<RecognizedRegion>,
        metrics: List<TextRegionMetrics>
    ): RecognizeResult {
        if (regions.size != metrics.size) {
            throw IllegalStateException("Mismatched regions and metrics size: " +
                "regions=${regions.size}, metrics=${metrics.size}")
        }

        val combinedRegions = regions.mapIndexed { i, region ->
            val metric = metrics[i]
            RecognizedRegion(
                tokens = region.tokens,
                score = region.score,
                colors = metric.colors,
                fontSize = metric.fontSize,
                lineHeight = metric.lineHeight,
                fontWeight = metric.fontWeight
            )
        }

        return RecognizeResult(regions = combinedRegions)
    }

    private fun combineResults(
        detectResult: DetectResult,
        recognizeResult: RecognizeResult
    ): CombinedOcrResult {
        if (detectResult.regions.size != recognizeResult.regions.size) {
            throw IllegalStateException("Mismatched detect and recognize results size: " +
                "detect=${detectResult.regions.size}, recognize=${recognizeResult.regions.size}")
        }

        val combinedRegions = detectResult.regions.indices.map { i ->
            val detected = detectResult.regions[i]
            val recognized = recognizeResult.regions[i]

            CombinedOcrRegion(
                points = detected.points,
                tokens = recognized.tokens,
                score = recognized.score,
                colors = recognized.colors,
                fontSize = recognized.fontSize,
                lineHeight = recognized.lineHeight,
                fontWeight = recognized.fontWeight
            )
        }

        return CombinedOcrResult(regions = combinedRegions)
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

    override fun load(
        detect: ObjectCharacterRecognitionDetectorWithFiles,
        recognize: ObjectCharacterRecognitionRecognizerWithFiles,
        threads: Int
    ) {
        val detectFile = File(detect.inference.model.pathString)
        val recognizeFile = File(recognize.inference.model.pathString)

        if (_detectSessionFile?.equals(detectFile) == true && _recognizeSessionFile?.equals(recognizeFile) == true) {
            return
        }

        close()
        val options = OrtSession.SessionOptions().apply {
            setCPUArenaAllocator(true)
            setMemoryPatternOptimization(true)
            setIntraOpNumThreads(threads)
            setInterOpNumThreads(threads)
            disableProfiling()
            setOptimizationLevel(OptLevel.ALL_OPT)
            addConfigEntry("optimization.enable_gelu_approximation", "1")
            addConfigEntry("session.disable_aot_function_inlining", "0")
            addConfigEntry("optimization.minimal_build_optimizations", "")
            addConfigEntry("mlas.enable_gemm_fastmath_arm64_bfloat16", "1")
            addConfigEntry("session.allow_released_opsets_only", "1")
            addConfigEntry("session.graph_optimizations_loop_level", "1")
            addConfigEntry("session.use_ort_model_bytes_directly", "1")
            addConfigEntry("session.use_ort_model_bytes_for_initializers", "0")
            addConfigEntry("session.set_denormal_as_zero", "1")
            addConfigEntry("session.use_env_allocators", "1")
            addConfigEntry("session.use_device_allocator_for_initializers", "1")
            addConfigEntry("ep.dynamic.workload_type", "Default")
            addConfigEntry("session.qdq_matmulnbits_accuracy_level", "2")
            addNnapi(EnumSet.of(NNAPIFlags.USE_FP16, NNAPIFlags.USE_NCHW))
            DeviceUtils.isEmulator.let {
                if (it) {
                    Timber.tag(TAG).d("Emulator detected, skipping WebGPU")
                    return@let
                }

                addWebGPU(
                    mapOf(
                        "preferredLayout" to "NCHW",
                        "enableGraphCapture" to "1"
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

    companion object {
        private val TAG: String = PaddleObjectCharacterRecognition::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
