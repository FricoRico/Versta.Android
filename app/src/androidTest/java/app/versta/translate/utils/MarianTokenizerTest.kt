package app.versta.translate.utils

import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageModelTokenizerFiles
import app.versta.translate.core.entity.LanguagePair
import org.junit.Assert.assertEquals
import org.junit.Test

class MarianTokenizerTest {

    @Test
    fun testMarianTokenizer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = context.filesDir.toPath()
        val tokenizer = MarianTokenizer()
        tokenizer.load(
            files = LanguageModelTokenizerFiles(
                config = path.resolve(CONFIG_FILE_NAME),
                sourceVocabulary = path.resolve(VOCAB_FILE_NAME),
                source = path.resolve(ENCODER_FILE_NAME),
                target = path.resolve(DECODER_FILE_NAME),
            ),
            languages = LanguagePair(
                source = Language.fromIsoCode("ja"),
                target = Language.fromIsoCode("nl")
            )
        )

        val input = "これはテストです。"
        val expected = listOf("▁これは", "テスト", "です", "。")
        val output = tokenizer.tokenize(input)

        assertEquals(expected, output)
    }

    @Test
    fun testMarianTokenizerEncoding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = context.filesDir.toPath()
        val tokenizer = MarianTokenizer()
        tokenizer.load(
            files = LanguageModelTokenizerFiles(
                config = path.resolve(CONFIG_FILE_NAME),
                sourceVocabulary = path.resolve(VOCAB_FILE_NAME),
                source = path.resolve(ENCODER_FILE_NAME),
                target = path.resolve(DECODER_FILE_NAME),
            ),
            languages = LanguagePair(
                source = Language.fromIsoCode("ja"),
                target = Language.fromIsoCode("nl")
            )
        )

        val input = "これはテストです。"
        val expected = longArrayOf(650, 9528, 207, 8)
        val (inputIds, _) = tokenizer.encode(input)

        assertEquals(expected, inputIds.filter { it != 0L })
    }

    @Test
    fun testMarianTokenizerDecoding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val path = context.filesDir.toPath()
        val tokenizer = MarianTokenizer()
        tokenizer.load(
            files = LanguageModelTokenizerFiles(
                config = path.resolve(CONFIG_FILE_NAME),
                sourceVocabulary = path.resolve(VOCAB_FILE_NAME),
                source = path.resolve(ENCODER_FILE_NAME),
                target = path.resolve(DECODER_FILE_NAME),
            ),
            languages = LanguagePair(
                source = Language.fromIsoCode("ja"),
                target = Language.fromIsoCode("nl")
            )
        )

        val input = longArrayOf(65000, 231, 24, 15, 6811, 2, 0)
        val expected = "Dit is een test."
        val output = tokenizer.decode(input)

        assertEquals(expected, output)
    }

    companion object {
        private const val CONFIG_FILE_NAME: String = "opus-mt-ja-nl.json"
        private const val VOCAB_FILE_NAME: String = "opus-mt-ja-nl-vocab.json"
        private const val ENCODER_FILE_NAME: String = "opus-mt-ja-nl-source.spm"
        private const val DECODER_FILE_NAME: String = "opus-mt-ja-nl-target.spm"
    }
}