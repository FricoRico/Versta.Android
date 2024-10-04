package name.ricardoismy.translate.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.content.Context

class DecoderMetadata(
    val batchSize: Int,
    val sequenceLength: Int,
) {
    private val completedBatches: BooleanArray = BooleanArray(batchSize) { false }
    private var completedBatchCount: Int = 0

    fun isBatchComplete(index: Int): Boolean {
        return completedBatches[index]
    }

    fun isDoneDecoding(): Boolean {
        return completedBatchCount == batchSize
    }

    fun markBatchComplete(index: Int) {
        completedBatches[index] = true
        completedBatchCount++
    }
}

// Shape: [batch_size, sequence_length, hidden_size]
typealias EncoderHiddenStates = Array<Array<FloatArray>>

// Shape: [batch_size, sequence_length, vocab_size]
typealias DecoderLogits = Array<Array<FloatArray>>

class OpusInference(
    context: Context,
    encoderFilePath: String,
    decoderFilePath: String,
    threadCount: Int = 4,
) {
    private val ortEnvironment = OrtEnvironment.getEnvironment(
        OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING,
        "OpusInference",
        OrtEnvironment.ThreadingOptions().apply {
            setGlobalSpinControl(false)
        })

    private val encoderSession: OrtSession
    private val decoderSession: OrtSession

    private val padTokenID: Long = 67027
    private val eosTokenId: Long = 0
    private val maxSequenceLength: Int = 128

    init {
        val assetManager = context.assets
        val encoderFile = assetManager.open(encoderFilePath).readBytes()
        val decoderFile = assetManager.open(decoderFilePath).readBytes()

        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setCPUArenaAllocator(true)
        sessionOptions.setMemoryPatternOptimization(true)
        sessionOptions.setInterOpNumThreads(1)
        sessionOptions.addXnnpack(mapOf("intra_op_num_threads" to threadCount.toString()))

        encoderSession = ortEnvironment.createSession(encoderFile, sessionOptions)
        decoderSession = ortEnvironment.createSession(decoderFile, sessionOptions)
    }

    @Suppress("UNCHECKED_CAST")
    fun encode(inputIds: Array<LongArray>, attentionMask: Array<LongArray>): EncoderHiddenStates {
        val inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMask)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        try {
            val encoderOutput = encoderSession.run(inputs)

            val encoderHiddenStates = encoderOutput.get("last_hidden_state").get().value as EncoderHiddenStates
            encoderOutput.close()

            return encoderHiddenStates
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            inputIdsTensor.close()
            attentionMaskTensor.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(
        encoderHiddenStates: Array<Array<FloatArray>>,
        attentionMask: Array<LongArray>,
    ): Array<LongArray> {
        try {
            val decoderMetadata = DecoderMetadata(encoderHiddenStates.size, maxSequenceLength)

            val encoderOutputsTensor = OnnxTensor.createTensor(ortEnvironment, encoderHiddenStates)
            val encoderAttentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMask)

            val decoderInputIds = Array(decoderMetadata.batchSize) { longArrayOf(padTokenID) }

            val inputs = mutableMapOf<String, OnnxTensorLike>(
                "encoder_hidden_states" to encoderOutputsTensor,
                "encoder_attention_mask" to encoderAttentionMaskTensor
            )

            for (step in 0 until decoderMetadata.sequenceLength) {
                if (decoderMetadata.isDoneDecoding()) {
                    break
                }

                val decoderInputIdsTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    decoderInputIds
                )

                inputs["input_ids"] = decoderInputIdsTensor

                val decoderOutputs = decoderSession.run(inputs)
                val logits = decoderOutputs.get("logits").get().value as DecoderLogits

                for (i in 0 until decoderMetadata.batchSize) {
                    if (decoderMetadata.isBatchComplete(i)) {
                        decoderInputIds[i] = decoderInputIds[i].plus(padTokenID)
                        continue
                    }

                    val batchLogits = logits[i][decoderInputIds[i].size - 1]
                    val token = argmaxTokenMatcher(batchLogits)

                    decoderInputIds[i] = decoderInputIds[i].plus(token)

                    if (token == eosTokenId) {
                        decoderMetadata.markBatchComplete(i)
                    }
                }

                decoderInputIdsTensor.close()
                decoderOutputs.close()
            }

            encoderOutputsTensor.close()
            encoderAttentionMaskTensor.close()

            return decoderInputIds
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun argmaxTokenMatcher(logits: FloatArray): Long {
        var maxLogit = Float.NEGATIVE_INFINITY
        var maxIndex = 0

        for (i in logits.indices) {
            if (logits[i] > maxLogit) {
                maxLogit = logits[i]
                maxIndex = i
            }
        }

        return maxIndex.toLong()
    }

    fun close() {
        encoderSession.close()
        decoderSession.close()
        ortEnvironment.close()
    }
}