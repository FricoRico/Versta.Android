package name.ricardoismy.translate.utils

import android.content.Context
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.DequantizeOp
import org.tensorflow.lite.support.metadata.MetadataExtractor
import org.tensorflow.lite.support.model.Model.Device
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable


class MarianModel(
    context: Context,
    modelFilePath: String,
    device: Device = Device.CPU,
    threads: Int = 4
) {
    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> NnApiDelegate()
        Device.GPU -> GpuDelegate()
    }

    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, modelFilePath),
        Interpreter.Options().apply {
            numThreads = threads
            delegate?.let { addDelegate(it) }
        }
    )

    init {
        interpreter.signatureKeys.forEach { key ->
            val inputShape = interpreter.getSignatureInputs(key)
            val outputShape = interpreter.getSignatureOutputs(key)
            println("Input shape for $key: ${inputShape.contentToString()}")
            println("Output shape for $key: ${outputShape.contentToString()}")
        }
    }

    fun generate(inputIds: IntArray, attentionMask: IntArray): Array<FloatArray> {
        val decoderInputIds = IntArray(inputIds.size) { 0 }
        val decoderAttentionMask = IntArray(inputIds.size) { 0 }

        val inputSignature = interpreter.getInputTensorFromSignature("input_ids", "serving_default")
        val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, inputIds.size), inputSignature.dataType())
        inputBuffer.loadArray(inputIds)

        interpreter.resizeInput(inputSignature.index(), inputBuffer.shape)

        val attentionMaskSignature = interpreter.getInputTensorFromSignature("attention_mask", "serving_default")
        val attentionMaskBuffer = TensorBuffer.createFixedSize(intArrayOf(1, attentionMask.size), attentionMaskSignature.dataType())
        attentionMaskBuffer.loadArray(attentionMask)

        interpreter.resizeInput(attentionMaskSignature.index(), attentionMaskBuffer.shape)

        val decoderInputSignature = interpreter.getInputTensorFromSignature("decoder_input_ids", "serving_default")
        val decoderInputIdsBuffer = TensorBuffer.createFixedSize(intArrayOf(1, decoderInputIds.size), decoderInputSignature.dataType())
        decoderInputIdsBuffer.loadArray(decoderInputIds)

        interpreter.resizeInput(decoderInputSignature.index(), decoderInputIdsBuffer.shape)

        val decoderAttentionMaskSignature = interpreter.getInputTensorFromSignature("decoder_attention_mask", "serving_default")
        val decoderAttentionMaskBuffer = TensorBuffer.createFixedSize(intArrayOf(1, decoderAttentionMask.size), decoderAttentionMaskSignature.dataType())
        decoderAttentionMaskBuffer.loadArray(decoderAttentionMask)

        interpreter.resizeInput(decoderAttentionMaskSignature.index(), decoderAttentionMaskBuffer.shape)

        val inputs = mutableMapOf<String, Any>()
        inputs["input_ids"] = inputBuffer.buffer
        inputs["attention_mask"] = attentionMaskBuffer.buffer
        inputs["decoder_input_ids"] = decoderInputIdsBuffer.buffer
        inputs["decoder_attention_mask"] = decoderAttentionMaskBuffer.buffer

        val logitsSignature = interpreter.getOutputTensorFromSignature("logits", "serving_default")
        val outputBuffer = TensorBuffer.createDynamic(logitsSignature.dataType())
        val outputs = mutableMapOf<String, Any>()
        outputs["logits"] = outputBuffer.buffer

        interpreter.allocateTensors()
        interpreter.runSignature(inputs, outputs, "serving_default")

        return extractLogits(outputBuffer, logitsSignature.shape())
    }

    private fun extractLogits(tensorBuffer: TensorBuffer, shape: IntArray): Array<FloatArray> {
        // Get logits as a flat FloatArray
        val logitsFlat = tensorBuffer.floatArray

        // Shape: [batch_size, sequence_length, vocab_size]
        val batchSize = shape[0]
        val seqLen = shape[1]
        val vocabSize = shape[2]

        // Reshape to [batch_size][sequence_length][vocab_size] (for 1D data)
        val logits = Array(seqLen) { FloatArray(vocabSize) }
        for (i in 0 until seqLen) {
            for (j in 0 until vocabSize) {
                val index = i * vocabSize + j
                logits[i][j] = logitsFlat[index]
            }
        }

        return logits
    }

    fun argmax(logits: FloatArray): Int {
        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    fun getPredictedTokens(logits: Array<FloatArray>): IntArray {
        val predictedTokens = IntArray(logits.size)  // Size is the sequence length
        for (i in logits.indices) {
            predictedTokens[i] = argmax(logits[i])
        }
        return predictedTokens
    }

    fun close() {
        interpreter.close()
        if (delegate is Closeable) {
            delegate.close()
        }
    }
}