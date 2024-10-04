package app.versta.translate.utils

import android.content.Context

class Translator(
    context: Context,
) {
    private val tokenizer = MarianTokenizer(
        context,
        encoderFilePath = TOKENIZER_SOURCE_FILE_NAME,
        decoderFilePath = TOKENIZER_TARGET_FILE_NAME,
        vocabularyFilePath = VOCAB_FILE_NAME,
        sourceLanguage = "nl",
        targetLanguage = "en"
    )

    private val model = OpusInference(
        context,
        encoderFilePath = ENCODER_MODEL_FILE_NAME,
        decoderFilePath = DECODER_MODEL_FILE_NAME,
    )

    fun translate(input: String, sentenceBatching: Boolean = true): String {
        val encoderInput = if (sentenceBatching) tokenizer.splitSentences(input) else listOf(input)

        val (inputIds, attentionMask) = tokenizer.encode(encoderInput)
        val encoderHiddenStates = model.encode(inputIds, attentionMask)

        val tokenIds =
            model.decode(encoderHiddenStates, attentionMask)

        val outputText = tokenizer.decode(tokenIds)

        return outputText.joinToString(" ").trim()
    }

    companion object {
        private val TAG: String = Translator::class.java.simpleName
        private const val ENCODER_MODEL_FILE_NAME: String =
            "encoder_model_quantized_new.with_runtime_opt.ort"
        private const val DECODER_MODEL_FILE_NAME: String =
            "decoder_model_quantized_new.with_runtime_opt.ort"
        private const val VOCAB_FILE_NAME: String = "vocab.json"
        private const val TOKENIZER_SOURCE_FILE_NAME: String = "source.spm"
        private const val TOKENIZER_TARGET_FILE_NAME: String = "target.spm"
    }
}