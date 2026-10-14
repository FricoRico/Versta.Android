package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelTokenizerFiles
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * Pure Kotlin port of the Supertonic reference `UnicodeProcessor` / text
 * chunker (https://github.com/supertone-inc/supertonic, `java/Helper.java`).
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
     * Splits text into chunks of at most the language's segment length,
     * mirroring the reference `chunkText`: paragraphs first, then sentences
     * (abbreviation-aware), with comma and word fallbacks for overlong
     * sentences. With [splitSentences] disabled each paragraph stays one
     * candidate chunk.
     */
    private fun chunk(
        text: String,
        language: Language,
        splitSentences: Boolean,
    ): List<String> {
        val maxLength = if (language.isoCode in SHORT_SEGMENT_LANGUAGES) SHORT_SEGMENT_LENGTH else SEGMENT_LENGTH

        return text
            .trim()
            .split("\\n\\s*\\n".toRegex())
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
            if (sentence.length > maxLength) {
                if (current.isNotEmpty()) {
                    chunks.add(current)
                    current = ""
                }
                chunks.addAll(chunkLongSentence(sentence, maxLength))
                continue
            }

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

    private fun chunkLongSentence(
        sentence: String,
        maxLength: Int,
    ): List<String> {
        val chunks = mutableListOf<String>()
        var current = ""

        for (part in sentence.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            if (part.length > maxLength) {
                if (current.isNotEmpty()) {
                    chunks.add(current)
                    current = ""
                }
                chunks.addAll(chunkWords(part, maxLength))
                continue
            }

            // The reference packs comma parts on a `+ 1` check while the join
            // itself adds ", ", so a chunk here may reach maxLength + 1 chars.
            // Mirrored verbatim: chunk boundaries must match the reference per
            // chunk for the NEU-68 vector comparisons.
            if (current.isNotEmpty() && current.length + part.length + 1 > maxLength) {
                chunks.add(current)
                current = ""
            }
            current = if (current.isEmpty()) part else "$current, $part"
        }

        if (current.isNotEmpty()) {
            chunks.add(current)
        }

        return chunks
    }

    private fun chunkWords(
        text: String,
        maxLength: Int,
    ): List<String> {
        val chunks = mutableListOf<String>()
        var current = ""

        for (word in text.split("\\s+".toRegex())) {
            if (current.isNotEmpty() && current.length + word.length + 1 > maxLength) {
                chunks.add(current)
                current = ""
            }
            current = if (current.isEmpty()) word else "$current $word"
        }

        if (current.isNotEmpty()) {
            chunks.add(current)
        }

        return chunks
    }

    private fun splitSentences(text: String): List<String> {
        val abbreviations = ABBREVIATIONS.joinToString("|") { Regex.escape(it) }
        return text.split("(?<!(?:$abbreviations))(?<=[.!?])\\s+".toRegex())
    }

    companion object {
        private const val SEGMENT_LENGTH = 300
        private const val SHORT_SEGMENT_LENGTH = 120

        private val SHORT_SEGMENT_LANGUAGES = setOf("ja", "ko")

        private val ABBREVIATIONS =
            listOf(
                "Dr.",
                "Mr.",
                "Mrs.",
                "Ms.",
                "Prof.",
                "Sr.",
                "Jr.",
                "St.",
                "Ave.",
                "Rd.",
                "Blvd.",
                "Dept.",
                "Inc.",
                "Ltd.",
                "Co.",
                "Corp.",
                "etc.",
                "vs.",
                "i.e.",
                "e.g.",
                "Ph.D.",
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
