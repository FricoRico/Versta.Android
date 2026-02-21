package app.versta.translate.core.entity

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.bridge.inference.TextRegionMetrics
import app.versta.translate.utils.TensorUtils
import timber.log.Timber
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PaddleObjectCharacterRecognitionRecognizeInput(
    private val ortEnvironment: OrtEnvironment,
    private val paddleOCR: PaddleOCR,
    private val maxBatchSize: Int,
    val cropHeight: Int = 48,
    val maxCropSize: Int = 640
) {
    val buffer: ByteBuffer = ByteBuffer
        .allocateDirect(maxBatchSize * 3 * cropHeight * maxCropSize * OnnxJavaType.FLOAT.size)
        .order(ByteOrder.nativeOrder())

    private val floatByteSize = OnnxJavaType.FLOAT.size
    private val perSampleInputBytes = 3 * cropHeight * maxCropSize * floatByteSize

    private var batchTensors = mutableMapOf<Int, OnnxTensor>()

    fun preprocess(
        origin: Buffer,
        detectResultBuffer: ByteBuffer,
        originWidth: Int,
        originHeight: Int,
        originRotation: Int
    ): Pair<Boolean, List<TextRegionMetrics>> {
        buffer.clear()
        return paddleOCR.preprocessTextRegions(
            origin = origin,
            input = detectResultBuffer,
            output = buffer,
            originWidth = originWidth,
            originHeight = originHeight,
            originRotation = originRotation
        )
    }

    fun get(batchIndex: Int, batchSize: Int): Map<String, OnnxTensorLike> {
        val inputOffsetBytes = batchIndex * perSampleInputBytes

        val inputSlice = buffer.duplicate().order(ByteOrder.nativeOrder()).apply {
            position(inputOffsetBytes)
            limit(inputOffsetBytes + batchSize * perSampleInputBytes)
        }.slice().order(ByteOrder.nativeOrder())

        val batchInputShape = longArrayOf(batchSize.toLong(), 3, cropHeight.toLong(), maxCropSize.toLong())

        val tensorKey = batchIndex * 1000 + batchSize
        if (!batchTensors.containsKey(tensorKey)) {
            batchTensors[tensorKey] = OnnxTensor.createTensor(
                ortEnvironment,
                inputSlice,
                batchInputShape,
                OnnxJavaType.FLOAT
            )
        }

        return mapOf("x" to batchTensors[tensorKey]!!)
    }

    fun clear() = buffer.clear()
    fun rewind() = buffer.rewind()

    fun destroy() {
        for (tensor in batchTensors.values) {
            TensorUtils.closeTensor(tensor)
        }
        batchTensors.clear()
    }
}

class PaddleObjectCharacterRecognitionRecognizeOutput(
    private val ortEnvironment: OrtEnvironment,
    private val paddleOCR: PaddleOCR,
    session: OrtSession,
    private val maxCandidates: Int,
    private val maxCropSize: Int = 640
) {
    private val floatByteSize = OnnxJavaType.FLOAT.size
    private val intByteSize = OnnxJavaType.INT32.size

    private val vocabSize: Int

    init {
        val outputInfo = session.outputInfo
        val outputName = session.outputNames.first()
        val tensorInfo = outputInfo[outputName]?.info as? TensorInfo

        vocabSize = tensorInfo?.shape?.last()?.toInt() ?: 100
    }

    private val _outputBuffer: ByteBuffer = allocateOutputBuffer(maxCandidates, maxCropSize, vocabSize)
    val outputBuffer: ByteBuffer get() = _outputBuffer

    private val tokenBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(maxCandidates * (2 * intByteSize + 1024 * intByteSize))
        .order(ByteOrder.nativeOrder())

    private var batchOutputTensors = mutableMapOf<Int, OnnxTensor>()

    private var _results: List<ObjectCharacterRecogniserResult> = emptyList()
    val results: List<ObjectCharacterRecogniserResult> get() = _results
    val count: Int get() = _results.size
    val isEmpty: Boolean get() = _results.isEmpty()

    operator fun get(index: Int): ObjectCharacterRecogniserResult = _results[index]
    fun forEach(action: (ObjectCharacterRecogniserResult) -> Unit) = _results.forEach(action)

    private fun allocateOutputBuffer(candidates: Int, cropSize: Int, vocab: Int): ByteBuffer {
        return ByteBuffer
            .allocateDirect(candidates * (cropSize / 8) * vocab * floatByteSize)
            .order(ByteOrder.nativeOrder())
    }

    private fun getBatchOutputTensor(batchIndex: Int, batchSize: Int): OnnxTensor {
        val perSampleOutputBytes = (maxCropSize / 8) * vocabSize * floatByteSize
        val outputOffsetBytes = batchIndex * perSampleOutputBytes

        val outputSlice = _outputBuffer.duplicate().order(ByteOrder.nativeOrder()).apply {
            position(outputOffsetBytes)
            limit(outputOffsetBytes + batchSize * perSampleOutputBytes)
        }.slice().order(ByteOrder.nativeOrder())

        val batchOutputShape = longArrayOf(
            batchSize.toLong(),
            maxCropSize.toLong() / 8,
            vocabSize.toLong()
        )

        val tensorKey = batchIndex * 1000 + batchSize
        if (!batchOutputTensors.containsKey(tensorKey)) {
            batchOutputTensors[tensorKey] = OnnxTensor.createTensor(
                ortEnvironment,
                outputSlice,
                batchOutputShape,
                OnnxJavaType.FLOAT
            )
        }

        return batchOutputTensors[tensorKey]!!
    }

    fun run(session: OrtSession, inputs: Map<String, OnnxTensorLike>, batchIndex: Int, batchSize: Int) {
        _outputBuffer.clear()

        val outputTensor = getBatchOutputTensor(batchIndex, batchSize)
        val outputName = session.outputNames.first()
        val outputs = mapOf(outputName to outputTensor)

        session.run(inputs, outputs)
    }

    fun parse(batchIndex: Int, batchSize: Int): List<ObjectCharacterRecogniserResult> {
        val perSampleOutputBytes = (maxCropSize / 8) * vocabSize * floatByteSize
        val outputOffsetBytes = batchIndex * perSampleOutputBytes

        val outputSlice = _outputBuffer.duplicate().order(ByteOrder.nativeOrder()).apply {
            position(outputOffsetBytes)
            limit(outputOffsetBytes + batchSize * perSampleOutputBytes)
        }.slice().order(ByteOrder.nativeOrder())

        val batchOutputShape = longArrayOf(
            batchSize.toLong(),
            maxCropSize.toLong() / 8,
            vocabSize.toLong()
        )

        val scoreCountCapacity = batchSize * intByteSize
        val tokenCountCapacity = batchSize * intByteSize
        val tokenCapacity = batchSize * 1024 * intByteSize
        val requiredTokenBufferSize = scoreCountCapacity + tokenCountCapacity + tokenCapacity

        if (tokenBuffer.capacity() < requiredTokenBufferSize) {
            Timber.tag(TAG)
                .w("Token buffer too small, resizing from ${tokenBuffer.capacity()} to $requiredTokenBufferSize")
        }
        tokenBuffer.clear()
        tokenBuffer.limit(requiredTokenBufferSize)

        _results = paddleOCR.postProcessRecognize(
            outputBuffer = outputSlice,
            outputShape = batchOutputShape,
            tokenBuffer = tokenBuffer
        )

        return _results
    }

    fun clear() {
        _outputBuffer.clear()
        tokenBuffer.clear()
    }

    fun rewind() {
        _outputBuffer.rewind()
        tokenBuffer.rewind()
    }

    fun destroy() {
        for (tensor in batchOutputTensors.values) {
            TensorUtils.closeTensor(tensor)
        }
        batchOutputTensors.clear()
        _results = emptyList()
    }

    companion object {
        private val TAG: String = PaddleObjectCharacterRecognitionRecognizeOutput::class.java.simpleName
    }
}
