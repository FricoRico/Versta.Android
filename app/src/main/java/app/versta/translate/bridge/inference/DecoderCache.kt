package app.versta.translate.bridge.inference

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import app.versta.translate.core.entity.ArchitectureConfig
import app.versta.translate.utils.TensorUtils
import timber.log.Timber
import java.nio.ByteBuffer

class DecoderCache(
    private val ortEnvironment: OrtEnvironment,
    private val architectureConfig: ArchitectureConfig
) : AutoCloseable {
    private var _handle: Long = 0L
    private var _beamSize: Int = 0
    private val _cache = mutableMapOf<String, OnnxTensorLike>()

    val cache: Map<String, OnnxTensorLike>
        get() = _cache.toMap()

    fun generateInitial(beamSize: Int) {
        if (_handle != 0L) {
            nativeClose(_handle)
            _handle = 0L
        }

        TensorUtils.closeTensorBuffer(_cache)
        TensorUtils.closeTensor(_cache)
        _cache.clear()

        _beamSize = beamSize

        val ortApiHandle = TensorUtils.getOrtApiHandle()
        _handle = nativeCreate(
            ortApiHandle = ortApiHandle,
            numLayers = architectureConfig.numLayers,
            numHeads = architectureConfig.numHeads,
            headDim = architectureConfig.headDim,
            beamSize = beamSize
        )

        if (_handle == 0L) {
            throw RuntimeException("Failed to initialize DecoderCache")
        }

        loadCacheFromNative()
    }

    fun update(outputs: OrtSession.Result, beamIndices: List<Int>) {
        if (_handle == 0L) {
            throw IllegalStateException("DecoderCache not initialized")
        }

        val ortApiHandle = TensorUtils.getOrtApiHandle()

        TensorUtils.closeTensorBuffer(_cache)
        TensorUtils.closeTensor(_cache)
        _cache.clear()

        for (output in outputs) {
            if (!output.key.contains(PRESENT_REGEX)) {
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

            val transposedBuffer = nativeTransposeBuffer(
                handle = _handle,
                ortApiHandle = ortApiHandle,
                tensorHandle = TensorUtils.getNativeHandle(tensor),
                beamIndices = beamIndices.toIntArray()
            )

            if (transposedBuffer == null) {
                continue
            }

            _cache[key] = OnnxTensor.createTensor(ortEnvironment, transposedBuffer.asFloatBuffer(), shape)
        }
    }

    private fun loadCacheFromNative() {
        val ortApiHandle = TensorUtils.getOrtApiHandle()
        val buffers = nativeGetCache(_handle, ortApiHandle)

        for ((name, buffer) in buffers) {
            val shape = longArrayOf(_beamSize.toLong(), architectureConfig.numHeads.toLong(), 0L, architectureConfig.headDim.toLong())
            _cache[name] = OnnxTensor.createTensor(ortEnvironment, buffer.asFloatBuffer(), shape)
        }
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        TensorUtils.closeTensorBuffer(_cache)
        TensorUtils.closeTensor(_cache)
        _cache.clear()

        nativeClose(_handle)
        _handle = 0L
    }

    private external fun nativeCreate(
        ortApiHandle: Long,
        numLayers: Int,
        numHeads: Int,
        headDim: Int,
        beamSize: Int
    ): Long

    private external fun nativeGetCache(
        handle: Long,
        ortApiHandle: Long
    ): Map<String, ByteBuffer>

    private external fun nativeTransposeBuffer(
        handle: Long,
        ortApiHandle: Long,
        tensorHandle: Long,
        beamIndices: IntArray
    ): ByteBuffer?

    private external fun nativeClose(handle: Long)

    companion object {
        private val TAG: String = DecoderCache::class.java.simpleName
        private val PRESENT_REGEX = "present.\\d".toRegex()

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
