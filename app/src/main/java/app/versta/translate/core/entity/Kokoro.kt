package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.utils.TensorUtils

// Shape: [1, num_samples]
internal typealias Waveform = FloatArray

class KokoroInput(ortEnvironment: OrtEnvironment, tokens: LongArray, style: FloatArray, speed: Float) {
    private val _tokenTensor = OnnxTensor.createTensor(ortEnvironment, arrayOf(tokens))
    private val _styleTensor = OnnxTensor.createTensor(ortEnvironment, arrayOf(style))
    private val _speedTensor = OnnxTensor.createTensor(ortEnvironment, floatArrayOf(speed))

    fun get(): Map<String, OnnxTensorLike> {
        return mapOf(
            "input_ids" to _tokenTensor,
            "style" to _styleTensor,
            "speed" to _speedTensor
        )
    }

    fun destroy() {
        TensorUtils.closeTensor(_tokenTensor)
        TensorUtils.closeTensor(_styleTensor)
        TensorUtils.closeTensor(_speedTensor)
    }
}

class KokoroOutput {
    private var _output: OrtSession.Result? = null

    @Suppress("UNCHECKED_CAST")
    fun parse(output: OrtSession.Result): Waveform? {
        _output = output

        val outputWaveform = output.get("waveform") ?: return null

        // Shape: [1, num_samples]
        val waveform = outputWaveform.get().value as Array<Waveform>

        return waveform.first()
    }

    fun destroy() {
        _output?.close()
    }
}