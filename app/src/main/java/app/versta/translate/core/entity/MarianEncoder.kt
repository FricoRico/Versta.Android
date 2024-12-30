package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

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

    fun close() {
        _inputIdsTensor.close()
        _attentionMaskTensor.close()
    }
}

class EncoderOutput {
    @Suppress("UNCHECKED_CAST")
    fun parse(output: OrtSession.Result): EncoderHiddenStates? {
        val outputLastHiddenStates = output.get("last_hidden_state") ?: return null

        // Shape: [batch_size, sequence_length, hidden_size]
        val lastHiddenStates = outputLastHiddenStates.get().value as Array<EncoderHiddenStates>
        output.close()

        return lastHiddenStates.first()
    }
}