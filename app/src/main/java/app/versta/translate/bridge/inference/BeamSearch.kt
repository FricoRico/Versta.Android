package app.versta.translate.bridge.inference

import ai.onnxruntime.OnnxTensor
import android.util.Log
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
    private var handle: Long

    init {
        handle = construct(beamSize, minP, repetitionPenalty / 10, padId, eosId)

        if (handle == 0L) {
            throw RuntimeException("Failed to initialize BeamSearch")
        }
    }

    fun search(tensor: OnnxTensor) {
        val ortApiHandle = TensorUtils.getOrtApiHandle()
        val tensorHandle = TensorUtils.getNativeHandle(tensor)
        val size = tensor.info.shape[2].toInt()

        return search(
            handle = handle,
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

        return transposeBuffer(handle, ortApiHandle, tensorHandle)
    }

    fun lastTokens(): Array<LongArray> {
        return lastTokens(handle)
    }

    fun complete(completeOnRepeat: Boolean): Boolean {
        return complete(handle, completeOnRepeat)
    }

    fun best(): LongArray {
        return best(handle)
    }

    override fun close() {
        if (handle == 0L) {
            Timber.tag(TAG).w("BeamSearch is already closed")
            return
        }

        close(handle)
        handle = 0L
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