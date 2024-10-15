package app.versta.translate.core.service.translation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.content.Context
import app.versta.translate.core.entity.LanguageModelInferenceFiles
import app.versta.translate.core.service.ModelInterface
import java.io.File
import kotlin.io.path.pathString

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
    threadCount: Int = 4,
): ModelInterface {
    private val ortEnvironment = OrtEnvironment.getEnvironment(
        OrtLoggingLevel.ORT_LOGGING_LEVEL_WARNING,
        "OpusInference",
        OrtEnvironment.ThreadingOptions().apply {
            setGlobalSpinControl(false)
        })

    private val sessionOptions = OrtSession.SessionOptions().apply {
        setCPUArenaAllocator(true)
        setMemoryPatternOptimization(true)
        setInterOpNumThreads(1)
        addXnnpack(mapOf("intra_op_num_threads" to threadCount.toString()))
    }

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null

    private val padTokenID: Long = 67027
    private val eosTokenId: Long = 0
    private val maxSequenceLength: Int = 128

    private val assetManager = context.assets

    @Suppress("UNCHECKED_CAST")
    override fun encode(inputIds: Array<LongArray>, attentionMask: Array<LongArray>): EncoderHiddenStates {
        val inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIds)
        val attentionMaskTensor = OnnxTensor.createTensor(ortEnvironment, attentionMask)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attentionMaskTensor
        )

        try {
            val encoderOutput = encoderSession?.run(inputs)

            if (encoderOutput == null) {
                inputIdsTensor.close()
                attentionMaskTensor.close()
                return emptyArray()
            }

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
    override fun decode(encoderHiddenStates: Array<*>, attentionMask: Array<*>): Array<LongArray> {
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

                val decoderOutputs = decoderSession?.run(inputs)

                if (decoderOutputs == null) {
                    decoderInputIdsTensor.close()
                    break
                }

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

                decoderOutputs.close()
                decoderInputIdsTensor.close()
            }

            encoderOutputsTensor.close()
            encoderAttentionMaskTensor.close()

            return decoderInputIds
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    override fun load(files: LanguageModelInferenceFiles) {
        close()

        val encoderFile = File(files.encoder.pathString).readBytes()
        val decoderFile = File(files.decoder.pathString).readBytes()

        encoderSession = ortEnvironment.createSession(encoderFile, sessionOptions)
        decoderSession = ortEnvironment.createSession(decoderFile, sessionOptions)
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
        encoderSession?.close()
        decoderSession?.close()

        ortEnvironment.close()
    }
}