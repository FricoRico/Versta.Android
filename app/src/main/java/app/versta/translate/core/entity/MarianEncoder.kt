package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.utils.TensorUtils

// Shape: [sequence_length, hidden_size]
internal typealias EncoderHiddenStates = Array<FloatArray>

// Shape: [sequence_length]
internal typealias EncoderAttentionMasks = LongArray

class EncoderInput(ortEnvironment: OrtEnvironment, inputIds: LongArray, attentionMask: LongArray) {
    private val _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, arrayOf(inputIds))
    private val _attentionMaskTensor =
        OnnxTensor.createTensor(ortEnvironment, arrayOf(attentionMask))

    fun get(): Map<String, OnnxTensorLike> {
        return mapOf(
            "input_ids" to _inputIdsTensor,
            "attention_mask" to _attentionMaskTensor
        )
    }

    fun destroy() {
        TensorUtils.closeTensor(_inputIdsTensor)
        TensorUtils.closeTensor(_attentionMaskTensor)
    }
}

class EncoderOutput {
    private var _output: OrtSession.Result? = null

    @Suppress("UNCHECKED_CAST")
    fun parse(output: OrtSession.Result): EncoderHiddenStates? {
        _output = output

        val outputLastHiddenStates = output.get("last_hidden_state") ?: return null

        // Shape: [batch_size, sequence_length, hidden_size]
        val lastHiddenStates = outputLastHiddenStates.get().value as Array<EncoderHiddenStates>

        return lastHiddenStates.first()
    }

    fun destroy() {
        TensorUtils.closeTensor(_output)
    }
}