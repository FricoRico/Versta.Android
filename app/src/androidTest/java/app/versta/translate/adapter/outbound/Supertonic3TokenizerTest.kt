package app.versta.translate.adapter.outbound

import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceModelTokenizerFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Unit tests for the Supertonic 3 UnicodeProcessor port. The fixture indexer
 * is the identity mapping, so token ids equal the code points of the
 * preprocessed text and can be decoded straight back to strings.
 */
class Supertonic3TokenizerTest {
    private lateinit var indexerFile: File

    private val english = Language.fromIsoCode("en")

    @Before
    fun setUp() {
        val directory =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.cacheDir
                .resolve("supertonic3-tokenizer-test")
        directory.deleteRecursively()
        directory.mkdirs()

        // Identity mapping for every code point up to and including the CJK
        // range, so ids decode back to the exact characters.
        indexerFile = directory.resolve("unicode_indexer.json")
        indexerFile.writeText((0..0x4FFF).joinToString(", ", "[", "]") { it.toString() })
    }

    private fun tokenizer(): Supertonic3Tokenizer =
        Supertonic3Tokenizer().also {
            it.load(VoiceModelTokenizerFiles(unicodeIndexer = indexerFile.toPath()))
        }

    private fun decode(tokens: LongArray): String =
        tokens
            .map { String(Character.toChars(it.toInt())) }
            .joinToString("")

    @Test
    fun tokenizeBeforeLoadThrows() {
        assertThrows(IllegalStateException::class.java) {
            Supertonic3Tokenizer().tokenize("Hello", english)
        }
    }

    @Test
    fun loadWithoutUnicodeIndexerThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            Supertonic3Tokenizer().load(VoiceModelTokenizerFiles())
        }
    }

    @Test
    fun tokenizeWrapsSentencesInLanguageTags() {
        val tokens = tokenizer().tokenize("Hello world", english)

        assertEquals(listOf("<en>Hello world.</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeKeepsTerminalPunctuation() {
        val tokens = tokenizer().tokenize("Already punctuated!", english)

        assertEquals(listOf("<en>Already punctuated!</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeNormalizesComposedCharactersToNfkd() {
        // é is U+00E9; NFKD decomposes it into "e" + combining acute (U+0301).
        val tokens = tokenizer().tokenize("Café", english)

        assertEquals(listOf("<en>Café.</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeStripsEmojis() {
        val tokens = tokenizer().tokenize("Great trip 😀☀", english)

        assertEquals(listOf("<en>Great trip.</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeReplacesDashesQuotesAndSymbols() {
        val tokens = tokenizer().tokenize("“She said — [hi]”", english)

        assertEquals(listOf("<en>\"She said - hi \".</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeExpandsAtSign() {
        val tokens = tokenizer().tokenize("root@localhost", english)

        assertEquals(listOf("<en>root at localhost.</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeKeepsUnknownCodePointsAsMinusOneSentinels() {
        // The fixture table ends at U+4FFF; U+5000 maps to -1 and stays in the
        // sequence: the native engine wraps those ids at the ONNX gather, so
        // positions must be preserved (frozen NEU-69 contract).
        val tokens = tokenizer().tokenize("a\u5000b", english)

        assertEquals(listOf(idSequence("<en>a\u5000b.</en>")), tokens.map { it.toList() })
    }

    @Test
    fun tokenizePacksCommaPartsLikeTheReference() {
        // Reference chunkText packs comma-separated parts on a `+ 1` check
        // while the join adds ", ": 150 + 149 + 1 fits 300 exactly, so both
        // parts share one 301-character chunk instead of two.
        val first = "a".repeat(150)
        val second = "b".repeat(149)
        val tokens = tokenizer().tokenize("$first, $second", english)

        assertEquals(1, tokens.size)
        assertEquals(listOf("<en>$first, $second.</en>"), tokens.map(::decode))
    }

    /**
     * Expected id sequence for [text] under the identity fixture: code points
     * up to the table end map to themselves, everything beyond to -1.
     */
    private fun idSequence(text: String): List<Long> {
        val ids = mutableListOf<Long>()
        var i = 0
        while (i < text.length) {
            val point = text.codePointAt(i)
            ids.add(if (point <= 0x4FFF) point.toLong() else -1L)
            i += Character.charCount(point)
        }
        return ids
    }

    @Test
    fun tokenizeSplitsOverlongParagraphsOnSentenceBoundaries() {
        val paragraph = "The journey continues today. ".repeat(30).trim()
        val tokens = tokenizer().tokenize(paragraph, english)

        assertTrue(tokens.size > 1)
        tokens.map(::decode).forEach { chunk ->
            assertTrue(chunk.startsWith("<en>"))
            assertTrue(chunk.endsWith(".</en>"))
        }
    }

    @Test
    fun tokenizeWithoutSentenceSplittingKeepsOneChunk() {
        val tokens = tokenizer().tokenize("First. Second.", english, splitSentences = false)

        assertEquals(listOf("<en>First. Second.</en>"), tokens.map(::decode))
    }

    @Test
    fun tokenizeChunksParagraphsOverTheSegmentLength() {
        val sentence = "word ".repeat(80).trim()
        val tokens = tokenizer().tokenize(sentence, english)

        assertTrue(tokens.size > 1)
        assertTrue(tokens.all { it.size <= 300 + "<en></en>.".length })
    }

    @Test
    fun tokenizeSplitsParagraphs() {
        val tokens = tokenizer().tokenize("First paragraph.\n\nSecond paragraph.", english)

        assertEquals(
            listOf("<en>First paragraph.</en>", "<en>Second paragraph.</en>"),
            tokens.map(::decode),
        )
    }

    @Test
    fun tokenizeBlankTextReturnsNoChunks() {
        assertTrue(tokenizer().tokenize("   ", english).isEmpty())
    }

    @Test
    fun tokenizeRejectsUnsupportedLanguages() {
        assertThrows(IllegalArgumentException::class.java) {
            tokenizer().tokenize("Nǐ hǎo", Language.fromIsoCode("zh"))
        }
    }
}
