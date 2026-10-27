package app.versta.translate.adapter.outbound

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden input→output tests for the pure text-normalization logic of
 * [StyleTextToSpeech2Tokenizer]. Expectations pin the exact current behavior,
 * including quirks such as `$5 hundred dollars` → `5 hundred dollars dollars`
 * (the scaled-money match keeps its original trailing word) and the no-op
 * `X's` possessive dance.
 */
class StyleTextToSpeech2TokenizerTest {
    private fun normalize(text: String): String = StyleTextToSpeech2Tokenizer.normalizeText(text)

    @Test
    fun quotesAreNormalized() {
        assertEquals("'hello'", normalize("‘hello’"))
        assertEquals("\"bonjour\"", normalize("«bonjour»"))
        assertEquals("\"quoted\"", normalize("“quoted”"))
        assertEquals("He said 'hi' «twice»", normalize("He said ‘hi’ (twice)"))
    }

    @Test
    fun parenthesesBecomeGuillemets() {
        assertEquals("«paren»", normalize("(paren)"))
    }

    @Test
    fun cjkPunctuationGetsLatinEquivalent() {
        assertEquals("こんにちは. 元気? はい!", normalize("こんにちは。元気？はい！"))
        assertEquals("a, b, c: d; e", normalize("a、b，c：d；e"))
    }

    @Test
    fun doctorAbbreviationExpansion() {
        assertEquals("Doctor Smith", normalize("Dr. Smith"))
        // The pattern only matches capital D, so lowercase dr. is untouched...
        assertEquals("dr. Smith", normalize("dr. Smith"))
        // ...and the lookahead fails without a capitalized following word.
        assertEquals("Dr. smith", normalize("Dr. smith"))
    }

    @Test
    fun misterMissMrsAbbreviationExpansions() {
        assertEquals("Mister Jones", normalize("Mr. Jones"))
        assertEquals("Mister Jones", normalize("MR. Jones"))
        assertEquals("MR. jones", normalize("MR. jones"))
        assertEquals("Miss Parker", normalize("Ms. Parker"))
        assertEquals("Mrs Jones", normalize("MRS. Jones"))
    }

    @Test
    fun etceteraIsNotExpanded() {
        // Pinned: the etc rule replaces with "etc" (a no-op regardless of case).
        assertEquals("etc. stuff", normalize("etc. stuff"))
        assertEquals("etc. Stuff", normalize("etc. Stuff"))
        assertEquals("ETC. blah", normalize("ETC. blah"))
    }

    @Test
    fun yeahIsNormalized() {
        assertEquals("ye'a", normalize("yeah"))
        assertEquals("Ye'a", normalize("Yeah"))
        assertEquals("ye'a", normalize("yea"))
        assertEquals("Ye'a!", normalize("YEAH!"))
        // Word boundary prevents matching inside longer words.
        assertEquals("yeahright", normalize("yeahright"))
    }

    @Test
    fun clockTimesToWords() {
        assertEquals("10 o'clock", normalize("10:00"))
        assertEquals("3 oh 5", normalize("3:05"))
        assertEquals("12 30", normalize("12:30"))
        // 13:00 is outside the [1-9]|1[0-2] hour range and is left as-is.
        assertEquals("13:00", normalize("13:00"))
        assertEquals("at 10 o'clock sharp", normalize("at 10:00 sharp"))
    }

    @Test
    fun yearsToWords() {
        assertEquals("19 99", normalize("1999"))
        assertEquals("19 hundred", normalize("1900"))
        assertEquals("18 oh 5", normalize("1805"))
        assertEquals("20 10", normalize("2010"))
        assertEquals("20 19s", normalize("2019s"))
        assertEquals("20 24", normalize("2024"))
        // Years before 1100 or with %1000 < 10 are left as digits.
        assertEquals("1066", normalize("1066"))
        assertEquals("2001", normalize("2001"))
        assertEquals("2000", normalize("2000"))
        assertEquals("11 hundred", normalize("1100"))
    }

    @Test
    fun plainMonetaryAmounts() {
        assertEquals("5 dollars", normalize("$5"))
        assertEquals("1 dollar", normalize("$1"))
        assertEquals("2 pounds", normalize("£2"))
        assertEquals("1 pound and 1 penny", normalize("£1.01"))
        assertEquals("1 dollar and 50 cents", normalize("$1.50"))
        assertEquals("1 dollar and 50 cents", normalize("$1.5"))
        assertEquals("0 dollars and 1 cent", normalize("$0.01"))
        assertEquals("12 dollars and 34 cents", normalize("$12.34"))
    }

    @Test
    fun scaledMonetaryAmountsPinDoublingQuirk() {
        // The scale word survives inside the match and the trailing word of the
        // original sentence is kept too, doubling the unit; pinned as-is.
        assertEquals("5 hundred dollars dollars", normalize("$5 hundred dollars"))
        assertEquals("2 thousand dollars", normalize("$2 thousand"))
        assertEquals("3 billion dollars", normalize("$3 billion"))
        assertEquals("3 million dollars pounds", normalize("$3 million pounds"))
    }

    @Test
    fun decimalsAreSpelledOut() {
        assertEquals("3 point 1 4", normalize("3.14"))
        assertEquals("0 point 5", normalize("0.5"))
        assertEquals("point 5", normalize(".5"))
        assertEquals("100 point 0 0", normalize("100.00"))
    }

    @Test
    fun groupedThousandsSeparatorsRemoved() {
        assertEquals("1000", normalize("1,000"))
        assertEquals("12345678", normalize("12,345,678"))
    }

    @Test
    fun rangesAndDigitPlurals() {
        assertEquals("10 to 20", normalize("10-20"))
        assertEquals("3 to 4 years", normalize("3-4 years"))
        assertEquals("100 S", normalize("100S"))
        assertEquals("3 S", normalize("3S"))
    }

    @Test
    fun possessiveCasingRules() {
        // Vowel-ending words keep lowercase 's'.
        assertEquals("James's", normalize("James's"))
        // Consonant-ending words get uppercase 'S' for sibilant pronunciation.
        assertEquals("B'S", normalize("B's"))
        assertEquals("Alex's car", normalize("Alex's car"))
        // X's is first uppercased then re-lowercased by the X exception; no-op.
        assertEquals("X's", normalize("X's"))
        assertEquals("cats", normalize("cats"))
    }

    @Test
    fun dottedAbbreviationsAreHyphenated() {
        assertEquals("U-S- citizen", normalize("U.S. citizen"))
        assertEquals("A-B-C- run", normalize("A.B.C. run"))
        assertEquals("e-g- something", normalize("e.g. something"))
        assertEquals("U-K- pm", normalize("U.K. pm"))
    }

    @Test
    fun punctuationSplitKeepsDelimiters() {
        assertEquals(
            listOf(false to "Hello", true to ", ", false to "world", true to "!"),
            StyleTextToSpeech2Tokenizer.split("Hello, world!"),
        )
        assertEquals(
            listOf(false to "a", true to "...", false to "b"),
            StyleTextToSpeech2Tokenizer.split("a...b"),
        )
        assertEquals(
            listOf(true to "(", false to "test", true to ") ", false to "this"),
            StyleTextToSpeech2Tokenizer.split("(test) this"),
        )
        assertEquals(
            listOf(false to "plain text"),
            StyleTextToSpeech2Tokenizer.split("plain text"),
        )
        assertEquals(
            listOf(false to "version 2", true to ".", false to "0", true to "?"),
            StyleTextToSpeech2Tokenizer.split("version 2.0?"),
        )
    }

    @Test
    fun whitespaceHandlingAndCombinedSentence() {
        // NBSP passes through the [^\S \n] rule untouched; a tab becomes a space.
        assertEquals("a\u00A0b", normalize("a\u00A0b"))
        assertEquals("a b", normalize("a\tb"))
        assertEquals(
            "Doctor Smith paid 23 dollars and 5 cents in 19 99, etc",
            normalize("Dr. Smith paid $23.05 in 1999, etc."),
        )
    }
}
