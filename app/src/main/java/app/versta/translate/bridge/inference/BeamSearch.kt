package app.versta.translate.bridge.inference

import ai.onnxruntime.OnnxTensor
import app.versta.translate.utils.TensorUtils
import timber.log.Timber
import java.nio.ByteBuffer

class BeamSearch(
    beamSize: Int,
    minP: Float,
    repetitionPenalty: Float,
    padId: Long,
    eosId: Long
) : AutoCloseable {
    private var _handle: Long

    init {
        _handle = construct(beamSize, minP, repetitionPenalty / 10, padId, eosId)

        if (_handle == 0L) {
            throw RuntimeException("Failed to initialize BeamSearch")
        }
    }

    fun search(tensor: OnnxTensor) {
        val ortApiHandle = TensorUtils.getOrtApiHandle()
        val tensorHandle = TensorUtils.getNativeHandle(tensor)
        val size = tensor.info.shape[2].toInt()

        return search(
            handle = _handle,
            apiHandle = ortApiHandle,
            tensorHandle = tensorHandle,
            size = size
        )
    }

    fun transposeBuffer(
        tensor: OnnxTensor
    ): ByteBuffer {
        val ortApiHandle = TensorUtils.getOrtApiHandle()
        val tensorHandle = TensorUtils.getNativeHandle(tensor)

        return transposeBuffer(_handle, ortApiHandle, tensorHandle)
    }

    fun lastTokens(): Array<LongArray> {
        return lastTokens(_handle)
    }

    fun complete(completeOnRepeat: Boolean): Boolean {
        return complete(_handle, completeOnRepeat)
    }

    fun best(): LongArray {
        return best(_handle)
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        close(_handle)
        _handle = 0L
    }

    private external fun construct(beamSize: Int, minP: Float, repetitionPenalty: Float, padId: Long, eosId: Long): Long

    private external fun search(
        handle: Long,
        apiHandle: Long,
        tensorHandle: Long,
        size: Int,
    )
    private external fun transposeBuffer(
        handle: Long,
        apiHandle: Long,
        tensorHandle: Long,
    ): ByteBuffer
    private external fun lastTokens(handle: Long): Array<LongArray>
    private external fun complete(handle: Long, completeOnRepeat: Boolean): Boolean
    private external fun best(handle: Long): LongArray
    private external fun close(handle: Long): Boolean

    companion object {
        private val TAG: String = BeamSearch::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}