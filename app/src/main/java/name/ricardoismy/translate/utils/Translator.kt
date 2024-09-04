package name.ricardoismy.translate.utils

import android.content.Context
import com.github.google.sentencepiece.SentencePieceProcessor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.model.Model.Device
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

class Translator(
    context: Context,
    device: Device = Device.CPU,
    threads: Int = 4
) {
    private val delegate: Delegate? = when (device) {
        Device.CPU -> null
        Device.NNAPI -> NnApiDelegate()
        Device.GPU -> GpuDelegate()
    }

    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, MODEL_FILE_NAME),
        Interpreter.Options().apply {
            numThreads = threads
            delegate?.let { addDelegate(it) }
        }
    )

    private val encoder = SentencePieceProcessor()
    private val decoder = SentencePieceProcessor()

    private val maxInputLength = 512 // Adapt this to the actual max input size
    private val vocabSize = 65001 // Adapt this to the actual vocab size
    private val eosTokenId = 0
    private val padTokenId = 65000

    private val inputBuffer =
        ByteBuffer.allocateDirect(maxInputLength * 4).order(ByteOrder.nativeOrder())
    private val decoderInputBuffer =
        ByteBuffer.allocateDirect(maxInputLength * 4).order(ByteOrder.nativeOrder())
    private val attentionMaskBuffer =
        ByteBuffer.allocateDirect(maxInputLength * 4).order(ByteOrder.nativeOrder())
    private val logitsBuffer =
        TensorBuffer.createFixedSize(intArrayOf(6, vocabSize), DataType.FLOAT32)

    init {
        val encoderBuffer = FileUtil.loadMappedFile(context, ENCODER_FILE_NAME)
        val encoderBytes = ByteArray(encoderBuffer.remaining())
        encoderBuffer.get(encoderBytes)

        val decoderBuffer = FileUtil.loadMappedFile(context, DECODER_FILE_NAME)
        val decoderBytes = ByteArray(decoderBuffer.remaining())
        decoderBuffer.get(decoderBytes)

        val vocabBuffer = FileUtil.loadMappedFile(context, VOCAB_FILE_NAME)
        val vocabString = Charset.forName("UTF-8").decode(vocabBuffer).toString()
        val vocab = Json.decodeFromString<JsonObject>(vocabString).keys.toList()

        encoder.loadFromSerializedProto(encoderBytes)

        decoder.loadFromSerializedProto(decoderBytes)
//        decoder.setVocabulary(vocab)

        interpreter.signatureKeys.forEach { key ->
            val inputShape = interpreter.getSignatureInputs(key)
            val outputShape = interpreter.getSignatureOutputs(key)
            println("Input shape for $key: ${inputShape.contentToString()}")
            println("Output shape for $key: ${outputShape.contentToString()}")
        }
    }

    fun translate(input: String): String {
        attentionMaskBuffer.clear()
        decoderInputBuffer.clear()
        inputBuffer.clear()

        val inputIds = encoder.encodeAsIds(input)

        for (i in inputIds.indices) {
            inputBuffer.putInt(inputIds[i])
            attentionMaskBuffer.putInt(1) // Assuming all tokens are attended
        }
        for (i in inputIds.size until maxInputLength) {
            inputBuffer.putInt(padTokenId)
            attentionMaskBuffer.putInt(0)
        }

        decoderInputBuffer.putInt(0)
        for (i in 1 until maxInputLength) {
            decoderInputBuffer.putInt(padTokenId) // Fill with padding
        }

        val inputs = arrayOf(
            attentionMaskBuffer,
            decoderInputBuffer,
            inputBuffer
        )

        val outputs = mapOf(1 to logitsBuffer)

        // Run inference
        interpreter.runForMultipleInputsOutputs(inputs, outputs)

        val logitsArray = logitsBuffer.floatArray

        val outputIds = mutableListOf<Int>()
        for (timestep in 0 until maxInputLength) {
            val start_index = timestep * vocabSize
            val end_index = start_index + vocabSize

            val logits = logitsArray.sliceArray(start_index until end_index)
            val maxIdx = logits.indices.maxByOrNull { logits[it] } ?: 0

            if (maxIdx == eosTokenId) break
            if (maxIdx != padTokenId) {
                outputIds.add(maxIdx)
            }
        }

        // Decode the output IDs into text using the sentencepiece decoder
        val outputText = decoder.decodeIds(*outputIds.toIntArray())

        return outputText.trimEnd() // Return the final decoded text
    }

    fun close() {
        encoder.close()
        decoder.close()
        interpreter.close()
        if (delegate is Closeable) {
            delegate.close()
        }
    }

    companion object {
        private val TAG: String = Translator::class.java.simpleName
        private const val MODEL_FILE_NAME: String = "opus-mt-ja-nl.tflite"
        private const val VOCAB_FILE_NAME: String = "opus-mt-ja-nl-vocab.json"
        private const val ENCODER_FILE_NAME: String = "opus-mt-ja-nl-source.spm"
        private const val DECODER_FILE_NAME: String = "opus-mt-ja-nl-target.spm"
    }
}