package app.versta.translate.core.entity

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.utils.TensorUtils
import java.nio.FloatBuffer

data class DecoderCacheEntry(
    var buffer: FloatBuffer,
    var shape: LongArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecoderCacheEntry

        if (buffer != other.buffer) return false
        if (!shape.contentEquals(other.shape)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = buffer.hashCode()
        result = 31 * result + shape.contentHashCode()
        return result
    }
}

// Shape: [batch_size, sequence_length, vocab_size]
internal typealias DecoderLogits = Array<Array<FloatArray>>

// Shape: [last_key_values, hidden_states]
internal typealias DecoderCache = Map<String, DecoderCacheEntry>

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

    private var _cacheTensors: Map<String, OnnxTensorLike?>? = null

    fun get(
        inputIds: Array<LongArray>,
        cache: DecoderCache? = null
    ): Map<String, OnnxTensorLike?> {
        _inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)
        _useCacheTensor = OnnxTensor.createTensor(ortEnvironment, booleanArrayOf(cache?.isNotEmpty() ?: false))

        val inputs = mutableMapOf(
            "input_ids" to _inputIdsTensor,
            "use_cache_branch" to _useCacheTensor,
            "encoder_hidden_states" to _encoderHiddenStatesTensor,
            "encoder_attention_mask" to _encoderAttentionMaskTensor,
        )
        _cacheTensors = cache?.map { (key, value) ->
            inputs[key] = OnnxTensor.createTensor(ortEnvironment, value.buffer, value.shape)
            key to inputs[key]
        }?.toMap()

        return inputs
    }

    fun close() {
        TensorUtils.closeTensor(_inputIdsTensor)
        TensorUtils.closeTensor(_useCacheTensor)
        TensorUtils.closeTensor(_cacheTensors)
    }

    fun destroy() {
        TensorUtils.closeTensor(_inputIdsTensor)
        TensorUtils.closeTensor(_useCacheTensor)

        TensorUtils.closeTensor(_cacheTensors)

        TensorUtils.closeTensor(_encoderHiddenStatesTensor)
        TensorUtils.closeTensor(_encoderAttentionMaskTensor)
    }
}

class DecoderOutput {
    private var _outputs : OrtSession.Result? = null

    private val _cacheRegex = "present.\\d".toRegex()
    private val _cache = mutableMapOf<String, DecoderCacheEntry>()
    val cache: DecoderCache
        get() = _cache

    @Suppress("UNCHECKED_CAST")
    fun parse(outputs: OrtSession.Result): DecoderLogits? {
        _outputs = outputs

        val outputLogits = outputs.get("logits").get()
        if (outputLogits !is OnnxTensor) {
            return null
        }

        val logits = outputLogits.value
        if (logits !is Array<*> || logits.isEmpty() || logits.isArrayOf<DecoderLogits>()) {
            return null
        }

        return logits as DecoderLogits
    }

    fun cache(outputs: OrtSession.Result, ids: IntArray) {
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

            val buffer = tensor.floatBuffer
            val count = tensor.info.numElements

            val transposed = FloatArray(count.toInt())
            val size = (count / shape.first()).toInt()

            ids.forEachIndexed { newIndex, oldIndex ->
                buffer.position(oldIndex * size)
                buffer.get(transposed, newIndex * size, size)
            }

            val cache = FloatBuffer.wrap(transposed)

            _cache.merge(key, DecoderCacheEntry(cache, shape)) { _, entry ->
                entry.buffer = cache
                entry.shape = shape
                entry
            }
        }
    }

    fun close() {
        TensorUtils.closeTensor(_outputs)
    }

    fun destroy() {
        TensorUtils.closeTensor(_outputs)
    }
}