package name.ricardoismy.translate.utils

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

    fun translate(input: String): String {
        val (inputIds, attentionMask) = tokenizer.encode(input)

        val encoderHiddenStates = model.encode(inputIds, attentionMask)
        val tokenIds = model.decode(encoderHiddenStates, attentionMask, tokenizer.padId, tokenizer.eosId)

        val outputText = tokenizer.decode(tokenIds)

        return outputText
    }

    companion object {
        private val TAG: String = Translator::class.java.simpleName
        private const val ENCODER_MODEL_FILE_NAME: String = "opus-mt-nl-en-encoder.ort"
        private const val DECODER_MODEL_FILE_NAME: String = "opus-mt-nl-en-decoder.ort"
        private const val VOCAB_FILE_NAME: String = "opus-mt-nl-en-vocab.json"
        private const val TOKENIZER_SOURCE_FILE_NAME: String = "opus-mt-nl-en-source.spm"
        private const val TOKENIZER_TARGET_FILE_NAME: String = "opus-mt-nl-en-target.spm"
    }
}