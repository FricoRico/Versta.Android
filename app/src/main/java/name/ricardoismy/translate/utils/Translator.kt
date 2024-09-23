package name.ricardoismy.translate.utils

import android.content.Context
import org.tensorflow.lite.support.model.Model.Device

class Translator(
    context: Context,
    device: Device = Device.CPU,
    threads: Int = 4
) {
    private val tokenizer = MarianTokenizer(
        context,
        encoderFilePath = ENCODER_FILE_NAME,
        decoderFilePath = DECODER_FILE_NAME,
        vocabularyFilePath = VOCAB_FILE_NAME,
        sourceLanguage = "ja",
        targetLanguage = "nl"
    )

    private val model = MarianModel(
        context,
        modelFilePath = MODEL_FILE_NAME,
        device = device,
        threads = threads
    )

    fun translate(input: String): String {
        val (inputIds, attentionMask) = tokenizer.encode(input)

        val logits = model.generate(inputIds, attentionMask)

        val tokens = model.getPredictedTokens(logits)

        val outputText = "Dit is een test."

        return outputText
    }

    companion object {
        private val TAG: String = Translator::class.java.simpleName
        private const val MODEL_FILE_NAME: String = "opus-mt-ja-nl.tflite"
        private const val VOCAB_FILE_NAME: String = "opus-mt-ja-nl-vocab.json"
        private const val ENCODER_FILE_NAME: String = "opus-mt-ja-nl-source.spm"
        private const val DECODER_FILE_NAME: String = "opus-mt-ja-nl-target.spm"
    }
}