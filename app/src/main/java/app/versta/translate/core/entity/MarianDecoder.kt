package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.bridge.inference.BeamSearch
import app.versta.translate.bridge.inference.DecoderCache
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

    private val _inputs = mutableMapOf<String, OnnxTensorLike?>(
        "input_ids" to null,
        "encoder_hidden_states" to _encoderHiddenStatesTensor,
        "encoder_attention_mask" to _encoderAttentionMaskTensor
    )

    fun getFromDirectBuffer(
        inputIdsBuffer: LongBuffer,
        beamCount: Int,
        decoderCache: DecoderCache
    ): Map<String, OnnxTensorLike?> {
        val shape = longArrayOf(beamCount.toLong(), 1L)
        _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, shape)

        _inputs["input_ids"] = _inputIdsTensor

        clearCache()
        _inputs.putAll(decoderCache.cache)

        return _inputs
    }

    fun get(
        inputIds: Array<LongArray>,
        decoderCache: DecoderCache
    ): Map<String, OnnxTensorLike?> {
        _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)

        _inputs["input_ids"] = _inputIdsTensor

        clearCache()
        _inputs.putAll(decoderCache.cache)

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
    }

    fun destroy() {
        TensorUtils.closeTensor(_inputIdsTensor)

        TensorUtils.closeTensor(_encoderHiddenStatesTensor)
        TensorUtils.closeTensor(_encoderAttentionMaskTensor)
    }
}

class MarianDecoderOutput(
    private val beamSearch: BeamSearch
) {
    fun search(outputs: OrtSession.Result) {
        val tensor = outputs.get("logits").get()
        if (tensor !is OnnxTensor) {
            throw IllegalStateException("Logits is not a tensor")
        }

        beamSearch.search(tensor)
    }

    fun destroy() {
    }
}
