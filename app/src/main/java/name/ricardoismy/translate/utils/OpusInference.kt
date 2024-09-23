package name.ricardoismy.translate.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OnnxTensorLike
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlin.math.exp
import kotlin.random.Random

class OpusInference(
    context: Context,
    encoderFilePath: String,
    decoderFilePath: String,
) {
    private val ortEnvironment = OrtEnvironment.getEnvironment()

    private val encoderSession: OrtSession
    private val decoderSession: OrtSession

    init {
        val assetManager = context.assets
        val encoderFile = assetManager.open(encoderFilePath).readBytes()
        val decoderFile = assetManager.open(decoderFilePath).readBytes()

        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.addNnapi()

        encoderSession = ortEnvironment.createSession(encoderFile, sessionOptions)
        decoderSession = ortEnvironment.createSession(decoderFile, sessionOptions)
    }

    fun encode(inputIds: IntArray, attentionMask: IntArray): Array<Array<FloatArray>> {
        val inputIdsTensor = OnnxTensor.createTensor(
            ortEnvironment,
            arrayOf(inputIds.map { it.toLong() }.toLongArray())
        )
        val attentionMaskTensor = OnnxTensor.createTensor(
            ortEnvironment,
            arrayOf(attentionMask.map { it.toLong() }.toLongArray())
        )

        val inputs = mutableMapOf<String, OnnxTensorLike>()
        inputs["input_ids"] = inputIdsTensor
        inputs["attention_mask"] = attentionMaskTensor

        // TODO: support multiple sequence support
        val encoderOutput = encoderSession.run(inputs)

        val encoderHiddenStates =
            encoderOutput[0].value as Array<Array<FloatArray>>  // Shape: [batch_size, sequence_length, hidden_size]

        inputIdsTensor.close()
        attentionMaskTensor.close()
        encoderOutput.close()

        return encoderHiddenStates
    }

    fun decode(
        encoderOutputs: Array<Array<FloatArray>>,
        encoderAttentionMask: IntArray,
        padTokenID: Int,
        eosTokenId: Int
    ): IntArray {
        val encoderOutputsTensor = OnnxTensor.createTensor(ortEnvironment, encoderOutputs)
        val encoderAttentionMaskTensor = OnnxTensor.createTensor(
            ortEnvironment,
            arrayOf(encoderAttentionMask.map { it.toLong() }.toLongArray())
        )

        val temperature = 0.7f
        val decoderInputIds = mutableListOf<Long>(padTokenID.toLong())
        val generatedTokens = mutableListOf<Long>()

        // TODO: support multiple sequence support
        for (step in 0 until 50) {
            // Convert decoderInputIds to LongArray and make it 2D
            val decoderInputIdsArray = arrayOf(decoderInputIds.toLongArray())

            // Create decoder input IDs tensor
            val decoderInputIdsTensor = OnnxTensor.createTensor(
                ortEnvironment,
                decoderInputIdsArray
            )

            // Prepare inputs for the decoder
            val inputs = mutableMapOf<String, OnnxTensorLike>()
            inputs["input_ids"] = decoderInputIdsTensor
            inputs["encoder_hidden_states"] = encoderOutputsTensor
            inputs["encoder_attention_mask"] = encoderAttentionMaskTensor

            // Run the decoder session
            val decoderOutputs = decoderSession.run(inputs)

            // Get the logits
            val logits =
                decoderOutputs[0].value as Array<Array<FloatArray>>  // Shape: [batch_size, sequence_length, vocab_size]

            // Get the logits for the last token in the sequence
            val lastTokenLogits = logits[0][decoderInputIds.size - 1]

            // Greedy decoding: pick the token with the highest score
            val nextTokenId = lastTokenLogits.indices.maxByOrNull { lastTokenLogits[it] }?.toLong()
                ?: eosTokenId.toLong()

            generatedTokens.add(nextTokenId)

            // Check for EOS token
            if (nextTokenId == eosTokenId.toLong()) {
                break
            }

            // Append the next token ID to decoder input IDs for the next iteration
            decoderInputIds.add(nextTokenId)

            // Clean up tensors
            decoderInputIdsTensor.close()
            decoderOutputs.close()
        }

        encoderOutputsTensor.close()
        encoderAttentionMaskTensor.close()

        return generatedTokens.map { it.toInt() }.toIntArray()
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val expLogits = logits.map { exp((it - maxLogit).toDouble()) }
        val sumExpLogits = expLogits.sum()
        return expLogits.map { (it / sumExpLogits).toFloat() }.toFloatArray()
    }

    private fun sampleFromDistribution(probabilities: FloatArray): Int {
        val cumulativeProbs = probabilities.scan(0f, Float::plus).drop(1)
        val randomValue = Random.nextFloat() * cumulativeProbs.last()
        return cumulativeProbs.indexOfFirst { it > randomValue }
    }



    fun close() {
        encoderSession.close()
        decoderSession.close()
        ortEnvironment.close()
    }
}