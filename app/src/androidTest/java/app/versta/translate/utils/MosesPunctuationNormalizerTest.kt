package app.versta.translate.utils

import app.versta.translate.adapter.outbound.MosesPunctuationNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class MosesPunctuationNormalizerTest {

    @Test
    fun testMosesNormalizeDocuments() {
        val moses = MosesPunctuationNormalizer()
        val inputs = listOf(
            "The United States in 1805 (color map)                 _Facing_     193",
            "=Formation of the Constitution.=--(1) The plans before the convention,",
            "directions--(1) The infective element must be eliminated. When the ulcer",
            "College of Surgeons, Edinburgh.)]"
        )
        val expected = listOf(
            "The United States in 1805 (color map) _Facing_ 193",
            "=Formation of the Constitution.=-- (1) The plans before the convention,",
            "directions-- (1) The infective element must be eliminated. When the ulcer",
            "College of Surgeons, Edinburgh.) ]"
        )
        for ((text, expect) in inputs.zip(expected)) {
            assertEquals(expect, moses.normalize(text))
        }
    }

    @Test
    fun testMosesNormalizeQuoteComma() {
        val mosesNormQuote = MosesPunctuationNormalizer(lang = "en", normQuoteCommas = true)
        val mosesNoNormQuote = MosesPunctuationNormalizer(lang = "en", normQuoteCommas = false)
        val text = "THIS EBOOK IS OTHERWISE PROVIDED TO YOU \"AS-IS\"."

        val expectedNormQuote = "THIS EBOOK IS OTHERWISE PROVIDED TO YOU \"AS-IS.\""
        assertEquals(expectedNormQuote, mosesNormQuote.normalize(text))

        val expectedNoNormQuote = "THIS EBOOK IS OTHERWISE PROVIDED TO YOU \"AS-IS\"."
        assertEquals(expectedNoNormQuote, mosesNoNormQuote.normalize(text))
    }

    @Test
    fun testMosesNormalizeNumbers() {
        val mosesNormNum = MosesPunctuationNormalizer(lang = "en", normNumbers = true)
        val mosesNoNormNum = MosesPunctuationNormalizer(lang = "en", normNumbers = false)

        var text = "12\u00A0123"
        var expected = "12.123"
        assertEquals(expected, mosesNormNum.normalize(text))

        text = "12 123"
        expected = "12 123"
        assertEquals(expected, mosesNoNormNum.normalize(text))
    }

    @Test
    fun testMosesNormalizeSingleApostrophe() {
        val mosesNormNum = MosesPunctuationNormalizer(lang = "en")
        val text = "yesterday ’s reception"
        val expected = "yesterday 's reception"
        assertEquals(expected, mosesNormNum.normalize(text))
    }

    @Test
    fun testNormalizationPipeline() {
        val mosesNormUnicode = MosesPunctuationNormalizer(
            preReplaceUnicodePunctuation = true,
            postRemoveControlChars = true
        )
        val text = "０《１２３》      ４５６％  '' 【７８９】"
        val expected = "0\"123\" 456% \" [789]"
        assertEquals(expected, mosesNormUnicode.normalize(text))
    }

    @Test
    fun testMosesNormalizeWithPerlParity() {
        val mosesPerlParity = MosesPunctuationNormalizer(perlParity = true)
        val text = "from the ‘bad bank’, Northern, wala\u00A0«\u00A0dox ci jawwu Les «\u00A0wagonways\u00A0»\u00A0étaient construits"
        val expected = "from the 'bad bank,\" Northern, wala \"dox ci jawwu Les \"wagonways\" étaient construits"
        assertEquals(expected, mosesPerlParity.normalize(text))
    }
}
