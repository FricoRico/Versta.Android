package app.versta.translate.core.entity

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.utils.TensorUtils
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PaddleObjectCharacterRecognitionDetectInput(
    private val ortEnvironment: OrtEnvironment,
    private val paddleOCR: PaddleOCR,
    val detectSize: Int = 640
) {
    val buffer: ByteBuffer = ByteBuffer
        .allocateDirect(detectSize * detectSize * 3 * OnnxJavaType.FLOAT.size)
        .order(ByteOrder.nativeOrder())

    private val inputShape = longArrayOf(1, 3, detectSize.toLong(), detectSize.toLong())
    private var inputTensor: OnnxTensor? = null

    fun preprocess(input: Buffer, width: Int, height: Int, rotation: Int): Boolean {
        buffer.clear()
        return paddleOCR.preProcessDetect(
            input = input,
            output = buffer,
            width = width,
            height = height,
            rotation = rotation
        )
    }

    fun get(): Map<String, OnnxTensorLike> {
        buffer.rewind()
        if (inputTensor == null) {
            inputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                buffer,
                inputShape,
                OnnxJavaType.FLOAT
            )
        }
        return mapOf("x" to inputTensor!!)
    }

    fun clear() = buffer.clear()
    fun rewind() = buffer.rewind()

    fun destroy() {
        TensorUtils.closeTensor(inputTensor)
        inputTensor = null
    }
}

class PaddleObjectCharacterRecognitionDetectOutput(
    private val ortEnvironment: OrtEnvironment,
    private val paddleOCR: PaddleOCR,
    val detectSize: Int = 640,
    val maxCandidates: Int = 100
) {
    val outputBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(detectSize * detectSize * OnnxJavaType.FLOAT.size)
        .order(ByteOrder.nativeOrder())

    val resultBuffer: ByteBuffer = ByteBuffer
        .allocateDirect(1 + maxCandidates * 4 * 2 * OnnxJavaType.INT32.size)
        .order(ByteOrder.nativeOrder())

    private val outputShape = longArrayOf(1, 1, detectSize.toLong(), detectSize.toLong())
    private var outputTensor: OnnxTensor? = null

    private var _results: List<ObjectCharacterRecogniserResult> = emptyList()
    val results: List<ObjectCharacterRecogniserResult> get() = _results
    val count: Int get() = _results.size
    val isEmpty: Boolean get() = _results.isEmpty()

    operator fun get(index: Int): ObjectCharacterRecogniserResult = _results[index]
    fun forEach(action: (ObjectCharacterRecogniserResult) -> Unit) = _results.forEach(action)

    fun getTensor(): OnnxTensor {
        if (outputTensor == null) {
            outputBuffer.rewind()
            outputTensor = OnnxTensor.createTensor(
                ortEnvironment,
                outputBuffer,
                outputShape,
                OnnxJavaType.FLOAT
            )
        }
        return outputTensor!!
    }

    fun run(session: OrtSession, inputs: Map<String, OnnxTensorLike>) {
        outputBuffer.clear()
        resultBuffer.clear()

        val tensor = getTensor()
        val outputName = session.outputNames.first()
        val outputs = mapOf(outputName to tensor)

        session.run(inputs, outputs)
    }

    fun parse(): List<ObjectCharacterRecogniserResult> {
        _results = paddleOCR.postProcessDetect(
            input = outputBuffer,
            output = resultBuffer
        )
        return _results
    }

    fun destroy() {
        TensorUtils.closeTensor(outputTensor)
        outputTensor = null
        _results = emptyList()
    }

}
