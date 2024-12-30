package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo

// Shape: [batch_size, sequence_length, vocab_size]
internal typealias DecoderLogits = Array<Array<FloatArray>>

// Shape: [last_key_values, hidden_states]
internal typealias DecoderCache = Map<String, OnnxTensorLike>

class DecoderInput(
    private val ortEnvironment: OrtEnvironment,
    encoderHiddenStates: Array<EncoderHiddenStates>,
    encoderAttentionMask: Array<EncoderAttentionMasks>,
) {
    private val _encoderHiddenStatesTensor =
        OnnxTensor.createTensor(ortEnvironment, encoderHiddenStates)
    private val _encoderAttentionMaskTensor =
        OnnxTensor.createTensor(ortEnvironment, encoderAttentionMask)

    private var _inputIdsTensor: OnnxTensorLike? = null
    private var _useCacheTensor: OnnxTensorLike? = null

    fun get(
        inputIds: Array<LongArray>,
        cache: DecoderCache?
    ): Map<String, OnnxTensorLike?> {
        _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)
        _useCacheTensor =
            OnnxTensor.createTensor(ortEnvironment, booleanArrayOf(cache?.isNotEmpty() ?: false))

        val inputs = mutableMapOf(
            "input_ids" to _inputIdsTensor,
            "use_cache_branch" to _useCacheTensor,
            "encoder_hidden_states" to _encoderHiddenStatesTensor,
            "encoder_attention_mask" to _encoderAttentionMaskTensor,
        )
        cache?.forEach { (key, value) ->
            inputs[key] = value
        }

        return inputs
    }

    fun close() {
        _inputIdsTensor?.close()
        _useCacheTensor?.close()

        _encoderHiddenStatesTensor.close()
        _encoderAttentionMaskTensor.close()
    }
}

class DecoderOutput {
    private val _cacheRegex = "present.\\d".toRegex()
    private val _cache = mutableMapOf<String, OnnxTensorLike>()
    val cache: DecoderCache
        get() = _cache

    @Suppress("UNCHECKED_CAST")
    fun parse(outputs: OrtSession.Result): DecoderLogits? {
        val outputLogits = outputs.get("logits") ?: return null

        val logits = outputLogits.get().value as DecoderLogits
        updateCache(outputs)

        return logits
    }

    private fun updateCache(outputs: OrtSession.Result) {
        for (output in outputs) {
            if (!output.key.contains(_cacheRegex)) {
                continue
            }

            if (output.value.info is TensorInfo) {
                val info = output.value.info as TensorInfo

                if (info.shape[2] >= 512) {
                    _cache.clear()
                    continue
                }

                if (info.shape[0] == 0L) {
                    continue
                }
            }

            val key = output.key.replace("present", "past_key_values")
            if (output.value is OnnxTensorLike) {
                _cache[key] = output.value as OnnxTensorLike
            }
        }
    }
}