package app.versta.translate.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.lang.reflect.Array
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.LongBuffer

class TensorUtils {
    companion object {
        fun createIntTensor(
            env: OrtEnvironment,
            data: IntArray
        ): OnnxTensor {
            val shape = determineShape(data)
            return createIntTensor(env, data, shape)
        }

        fun createIntTensor(
            env: OrtEnvironment,
            data: IntArray,
            shape: LongArray
        ): OnnxTensor {
            return OnnxTensor.createTensor(env, IntBuffer.wrap(data), shape)
        }

        fun createFloatTensor(
            env: OrtEnvironment,
            data: FloatArray
        ): OnnxTensor {
            val shape = determineShape(data)
            return createFloatTensor(env, data, shape)
        }

        fun createFloatTensor(
            ortEnvironment: OrtEnvironment,
            data: FloatArray,
            shape: LongArray
        ): OnnxTensor {
            return OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(data), shape)
        }

        fun createLongTensor(
            ortEnvironment: OrtEnvironment,
            data: LongArray
        ): OnnxTensor {
            val shape = determineShape(data)
            return createLongTensor(ortEnvironment, data, shape)
        }

        fun createLongTensor(
            ortEnvironment: OrtEnvironment,
            data: LongArray,
            shape: LongArray
        ): OnnxTensor {
            return OnnxTensor.createTensor(ortEnvironment, LongBuffer.wrap(data), shape)
        }

        private fun determineShape(data: Any): LongArray {
            if (!data.javaClass.isArray) {
                throw IllegalArgumentException("Argument is not an array.")
            }

            val length = Array.getLength(data)
            if (length == 0) {
                return longArrayOf(0)
            }

            val next = Array.get(data, 0)
            return if (next == null || !next.javaClass.isArray) {
                longArrayOf(length.toLong())
            } else {
                longArrayOf(length.toLong()) + determineShape(next)
            }
        }

        fun closeTensor(tensor: OrtSession.Result?) {
            tensor?.close()
        }

        fun closeTensor(tensor: Map<String, OnnxTensorLike?>?) {
            tensor?.forEach { closeTensor(it.value) }
        }

        fun closeTensor(tensor: Collection<OnnxTensorLike>?) {
            tensor?.forEach { closeTensor(it) }
        }

        fun closeTensor(tensor: OnnxTensorLike?) {
            if (tensor != null && !tensor.isClosed) {
                tensor.close()
            }
        }
    }
}