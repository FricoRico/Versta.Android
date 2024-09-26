package name.ricardoismy.translate.utils

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class MarianTokenizerTest {

    @Test
    fun testMarianTokenizer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = MarianTokenizer(
            context,
            encoderFilePath = ENCODER_FILE_NAME,
            decoderFilePath = DECODER_FILE_NAME,
            vocabularyFilePath = VOCAB_FILE_NAME,
            sourceLanguage = "ja",
            targetLanguage = "nl"
        )

        val input = "これはテストです。"
        val expected = listOf("▁これは", "テスト", "です", "。")
        val output = tokenizer.tokenize(input)

        assertEquals(expected, output)
    }

    @Test
    fun testMarianTokenizerEncoding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = MarianTokenizer(
            context,
            encoderFilePath = ENCODER_FILE_NAME,
            decoderFilePath = DECODER_FILE_NAME,
            vocabularyFilePath = VOCAB_FILE_NAME,
            sourceLanguage = "ja",
            targetLanguage = "nl"
        )

        val input = "これはテストです。"
        val expected = listOf(650, 9528, 207, 8)
        val (inputIds, _) = tokenizer.encode(input)

        assertEquals(expected, inputIds.filter { it != 0 })
    }

    @Test
    fun testMarianTokenizerDecoding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = MarianTokenizer(
            context,
            encoderFilePath = ENCODER_FILE_NAME,
            decoderFilePath = DECODER_FILE_NAME,
            vocabularyFilePath = VOCAB_FILE_NAME,
            sourceLanguage = "ja",
            targetLanguage = "nl"
        )

        val input = intArrayOf(65000, 231, 24, 15, 6811, 2, 0)
        val expected = "Dit is een test."
        val output = tokenizer.decode(input)

        assertEquals(expected, output)
    }

    companion object {
        private const val VOCAB_FILE_NAME: String = "opus-mt-ja-nl-vocab.json"
        private const val ENCODER_FILE_NAME: String = "opus-mt-ja-nl-source.spm"
        private const val DECODER_FILE_NAME: String = "opus-mt-ja-nl-target.spm"
    }
}