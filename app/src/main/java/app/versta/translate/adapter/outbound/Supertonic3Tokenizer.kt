package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelTokenizerFiles
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * Pure Kotlin port of the Supertonic reference `UnicodeProcessor` / text
 * chunker. Preprocessing matches every published port verbatim; the chunking
 * policy follows the reference Python pipeline (supertone-inc `py/helper.py`,
 * vra/supertonic-mnn `text.py`) — the Java port's comma/word fallbacks and
 * extra abbreviation entries are not reference behavior and would desync the
 * app-side chunks from the NEU-68 reference vectors.
 * Normalizes raw text (NFKD, emoji and symbol cleanup, `<lang>` tag wrap),
 * splits it into speakable chunks, and maps every chunk's code points through
 * the bundle's `unicode_indexer.json` to model input ids. Code points with no
 * model input id stay in the sequence as `-1` (the frozen NEU-69 contract has
 * the native engine wrap them at the ONNX gather).
 *
 * The pairing of text ids with their length mask and the model pipeline itself
 * live behind the native engine; this class only produces per-chunk id
 * sequences.
 */
class Supertonic3Tokenizer : TextToSpeechTokenizer {
    private var indexer: LongArray? = null

    override fun load(files: VoiceModelTokenizerFiles) {
        val path =
            requireNotNull(files.unicodeIndexer) { "Supertonic3 tokenizer requires unicode_indexer.json" }

        indexer =
            path
                .toFile()
                .readText()
                .let { Json.decodeFromString<List<Long>>(it) }
                .toLongArray()
    }

    override fun tokenize(
        text: String,
        language: Language,
        splitSentences: Boolean,
    ): List<LongArray> {
        val indexer = checkNotNull(indexer) { "Unicode indexer is not loaded" }

        return chunk(text, language, splitSentences)
            .map { chunk ->
                // Maps the tagged code points through the indexer. Code points
                // beyond the indexer table map to -1 and stay in the sequence:
                // the native engine resolves them with the ONNX-gather wrap
                // (frozen NEU-69 contract), so the ids must keep their positions.
                codePoints(preprocess(chunk, language.isoCode))
                    .map { if (it < indexer.size) indexer[it] else -1L }
                    .toLongArray()
            }.filter { it.isNotEmpty() }
    }

    /**
     * Normalizes a chunk and wraps it in language tags, mirroring the reference
     * `preprocessText`.
     */
    private fun preprocess(
        text: String,
        language: String,
    ): String {
        require(Supertonic3.LANGUAGES.contains(language)) { "Invalid language: $language" }

        var result = Normalizer.normalize(text, Normalizer.Form.NFKD)
        result = removeEmojis(result)

        replacements.forEach { (from, to) -> result = result.replace(from, to) }
        result = result.replace(symbolsPattern, "")

        expressions.forEach { (from, to) -> result = result.replace(from, to) }

        result = result.replace(spacingPattern, "$1")

        while (result.contains("\"\"")) result = result.replace("\"\"", "\"")
        while (result.contains("''")) result = result.replace("''", "'")
        while (result.contains("``")) result = result.replace("``", "`")

        result = result.replace("\\s+".toRegex(), " ").trim()

        if (!result.matches(terminalPunctuationPattern)) {
            result += "."
        }

        return "<$language>$result</$language>"
    }

    private fun codePoints(text: String): List<Int> {
        val points = mutableListOf<Int>()
        var i = 0
        while (i < text.length) {
            val point = text.codePointAt(i)
            points.add(point)
            i += Character.charCount(point)
        }
        return points
    }

    private fun removeEmojis(text: String): String {
        val result = StringBuilder()
        for (point in codePoints(text)) {
            if (emojiRanges.none { point in it }) {
                result.appendCodePoint(point)
            }
        }
        return result.toString()
    }

    /**
     * Splits text into chunks, mirroring the reference `chunk_text`: paragraphs
     * first, then abbreviation-aware sentence packing up to the language's
     * segment length. An overlong sentence stays whole, like the reference;
     * the engine's 1024-token cap is the tripwire for pathological input. With
     * [splitSentences] disabled each paragraph stays one candidate chunk.
     */
    private fun chunk(
        text: String,
        language: Language,
        splitSentences: Boolean,
    ): List<String> {
        val maxLength = if (language.isoCode in SHORT_SEGMENT_LANGUAGES) SHORT_SEGMENT_LENGTH else SEGMENT_LENGTH

        return text
            .trim()
            .split("\\n\\s*\\n+".toRegex())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { chunkParagraph(it, maxLength, splitSentences) }
    }

    private fun chunkParagraph(
        paragraph: String,
        maxLength: Int,
        splitSentences: Boolean,
    ): List<String> {
        if (paragraph.length <= maxLength) {
            return listOf(paragraph)
        }

        val sentences = if (splitSentences) splitSentences(paragraph) else listOf(paragraph)
        val chunks = mutableListOf<String>()
        var current = ""

        for (sentence in sentences.map { it.trim() }.filter { it.isNotEmpty() }) {
            // The reference (vra/supertonic-mnn `text.py` chunk_text) keeps an
            // overlong sentence whole and starts a new chunk with it; the
            // native side's 1024-token cap is the declared tripwire. No
            // comma/word fallback — chunk boundaries decide which NEU-68
            // reference vector a chunk is compared against.
            if (current.isNotEmpty() && current.length + sentence.length + 1 > maxLength) {
                chunks.add(current)
                current = ""
            }

            current = if (current.isEmpty()) sentence else "$current $sentence"
        }

        if (current.isNotEmpty()) {
            chunks.add(current)
        }

        return chunks
    }

    private fun splitSentences(text: String): List<String> {
        // Sentence boundaries are [.!?] followed by whitespace, except after a
        // known abbreviation or a single capital letter ("F. Scott" must not
        // split) — the reference pattern, verbatim.
        val abbreviations = ABBREVIATIONS.joinToString("|") { Regex.escape(it) }
        return text.split("(?<!(?:$abbreviations))(?<!\\b[A-Z]\\.)(?<=[.!?])\\s+".toRegex())
    }

    companion object {
        private const val SEGMENT_LENGTH = 300
        private const val SHORT_SEGMENT_LENGTH = 120

        private val SHORT_SEGMENT_LANGUAGES = setOf("ja", "ko")

        // The reference abbreviation set (supertone-inc `py/helper.py` and
        // vra/supertonic-mnn `text.py`); the Java port's "Rd."/"Dept." entries
        // never split sentence boundaries there, so they are not included.
        private val ABBREVIATIONS =
            listOf(
                "Mr.",
                "Mrs.",
                "Ms.",
                "Dr.",
                "Prof.",
                "Sr.",
                "Jr.",
                "Ph.D.",
                "etc.",
                "e.g.",
                "i.e.",
                "vs.",
                "Inc.",
                "Ltd.",
                "Co.",
                "Corp.",
                "St.",
                "Ave.",
                "Blvd.",
            )

        private val emojiRanges =
            listOf(
                0x1F600..0x1F64F,
                0x1F300..0x1F5FF,
                0x1F680..0x1F6FF,
                0x1F700..0x1F77F,
                0x1F780..0x1F7FF,
                0x1F800..0x1F8FF,
                0x1F900..0x1F9FF,
                0x1FA00..0x1FA6F,
                0x1FA70..0x1FAFF,
                0x2600..0x26FF,
                0x2700..0x27BF,
                0x1F1E6..0x1F1FF,
            )

        private val replacements =
            mapOf(
                "–" to "-",
                "‑" to "-",
                "—" to "-",
                "_" to " ",
                "“" to "\"",
                "”" to "\"",
                "‘" to "'",
                "’" to "'",
                "´" to "'",
                "`" to "'",
                "[" to " ",
                "]" to " ",
                "|" to " ",
                "/" to " ",
                "#" to " ",
                "→" to " ",
                "←" to " ",
            )

        private val symbolsPattern = "[♥☆♡©\\\\]".toRegex()

        private val expressions =
            mapOf(
                "@" to " at ",
                "e.g.," to "for example, ",
                "i.e.," to "that is, ",
            )

        private val spacingPattern = " ([,.!?;:'])".toRegex()
        private val terminalPunctuationPattern =
            ".*[.!?;:,'\"“”‘’)\\]}…。」』】〉》›»]$".toRegex()
    }
}
