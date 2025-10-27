package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.bridge.inference.BeamSearch
import app.versta.translate.utils.TensorUtils
import java.nio.LongBuffer

class MarianDecoderInput(
    private val ortEnvironment: OrtEnvironment,
    encoderHiddenStates: Array<EncoderHiddenStates>,
    encoderAttentionMask: Array<EncoderAttentionMasks>,
) {
    private val _encoderHiddenStatesTensor =
        OnnxTensor.createTensor(ortEnvironment, encoderHiddenStates)
    private val _encoderAttentionMaskTensor =
        OnnxTensor.createTensor(ortEnvironment, encoderAttentionMask)

    private val _cacheRegex = "past_key_values.\\d".toRegex()

    private var _inputIdsTensor: OnnxTensorLike? = null
    private var _useCacheTensor: OnnxTensorLike? = null

    private val _inputs = mutableMapOf<String, OnnxTensorLike?>(
        "input_ids" to null,
        "use_cache_branch" to null,
        "encoder_hidden_states" to _encoderHiddenStatesTensor,
        "encoder_attention_mask" to _encoderAttentionMaskTensor
    )

    fun getFromDirectBuffer(
        inputIdsBuffer: LongBuffer,
        beamCount: Int,
        cache: Map<String, OnnxTensorLike>? = null
    ): Map<String, OnnxTensorLike?> {
        val shape = longArrayOf(beamCount.toLong(), 1L)
        _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, shape)
        _useCacheTensor =
            OnnxTensor.createTensor(ortEnvironment, booleanArrayOf(cache?.isNotEmpty() ?: false))

        _inputs["input_ids"] = _inputIdsTensor
        _inputs["use_cache_branch"] = _useCacheTensor

        clearCache()
        if (cache != null) {
            _inputs.putAll(cache)
        }

        return _inputs
    }

    fun get(
        inputIds: Array<LongArray>,
        cache: Map<String, OnnxTensorLike>? = null
    ): Map<String, OnnxTensorLike?> {
        _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)
        _useCacheTensor =
            OnnxTensor.createTensor(ortEnvironment, booleanArrayOf(cache?.isNotEmpty() ?: false))

        _inputs["input_ids"] = _inputIdsTensor
        _inputs["use_cache_branch"] = _useCacheTensor

        clearCache()
        if (cache != null) {
            _inputs.putAll(cache)
        }

        return _inputs
    }

    private fun clearCache() {
        val keys = _inputs.keys.filter { it.contains(_cacheRegex) }
        for (key in keys) {
            _inputs.remove(key)
        }
    }

    fun close() {
        TensorUtils.closeTensor(_inputIdsTensor)
        TensorUtils.closeTensor(_useCacheTensor)
    }

    fun destroy() {
        TensorUtils.closeTensor(_inputIdsTensor)
        TensorUtils.closeTensor(_useCacheTensor)

        TensorUtils.closeTensor(_encoderHiddenStatesTensor)
        TensorUtils.closeTensor(_encoderAttentionMaskTensor)
    }
}

class MarianDecoderOutput(
    private val ortEnvironment: OrtEnvironment,
    private val beamSearch: BeamSearch
) {
    private val _cacheRegex = "present.\\d".toRegex()
    private val _cache = mutableMapOf<String, OnnxTensorLike>()
    val cache: Map<String, OnnxTensorLike>
        get() = _cache

    fun search(outputs: OrtSession.Result) {
        val tensor = outputs.get("logits").get()
        if (tensor !is OnnxTensor) {
            throw IllegalStateException("Logits is not a tensor")
        }

        beamSearch.search(tensor)
    }

    fun cache(outputs: OrtSession.Result) {
        for (output in outputs) {
            if (!output.key.contains(_cacheRegex)) {
                continue
            }

            val key = output.key.replace("present", "past_key_values")

            val tensor = output.value
            if (tensor !is OnnxTensor) {
                continue
            }

            val shape = tensor.info.shape
            if (shape.first() == 0L) {
                continue
            }

            val buffer = beamSearch.transposeBuffer(tensor)
            if (buffer == null) {
                continue
            }

            TensorUtils.closeTensorBuffer(_cache[key])
            TensorUtils.closeTensor(_cache[key])

            _cache[key] = OnnxTensor.createTensor(ortEnvironment, buffer.asFloatBuffer(), shape)
        }
    }

    fun destroy() {
        TensorUtils.closeTensorBuffer(_cache)
        TensorUtils.closeTensor(_cache)
    }
}