package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.tokenize.Vocabulary
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceWithModelFiles
import java.util.Locale

private const val MAX_PHONEME_LENGTH = 512

class StyleTextToSpeech2Tokenizer : TextToSpeechTokenizer {
    private val _japaneseTransliterator = JapaneseTransliterator()
    private var _vocabulary: Map<String, Int> = _defaultVocabulary
    private var _loadedVocabularyPath: String? = null

    fun load(files: VoiceWithModelFiles) {
        // Only load vocabulary file if model has vocabulary file
        if (files.hasVocabularyFile()) {
            val vocabPath = files.vocabulary!!.pathString
            
            // Only reload if vocabulary path has changed
            if (_loadedVocabularyPath != vocabPath) {
                try {
                    val vocabList = Vocabulary.load(vocabPath)
                    _vocabulary = vocabList.mapIndexed { index, symbol -> symbol to index }.toMap()
                    _loadedVocabularyPath = vocabPath
                } catch (e: Exception) {
                    // Fall back to default vocabulary if loading fails
                    _vocabulary = _defaultVocabulary
                    _loadedVocabularyPath = null
                    throw IllegalArgumentException("Failed to load vocabulary file: $vocabPath", e)
                }
            }
        } else {
            // Use default vocabulary for older models or models without vocabulary file
            _vocabulary = _defaultVocabulary
            _loadedVocabularyPath = null
        }
    }

    /**
     * Helper function to split a string on a regex, but keep the delimiters
     */
    private fun split(text: String): List<Pair<Boolean, String>> {
        val result = mutableListOf<Pair<Boolean, String>>()
        var prev = 0
        for (match in _punctuationPattern.findAll(text)) {
            val fullMatch = match.value
            if (prev < match.range.first) {
                result.add(false to text.substring(prev, match.range.first))
            }
            if (fullMatch.isNotEmpty()) {
                result.add(true to fullMatch)
            }
            prev = match.range.last + 1
        }
        if (prev < text.length) {
            result.add(false to text.substring(prev))
        }
        return result
    }

    /**
     * Helper function to split numbers into phonetic equivalents
     */
    private fun splitNum(match: String): String {
        return if ("." in match) {
            match
        } else if (":" in match) {
            val (h, m) = match.split(":").map { it.toInt() }
            when {
                m == 0 -> "$h o'clock"
                m < 10 -> "$h oh $m"
                else -> "$h $m"
            }
        } else {
            val year = match.take(4).toIntOrNull() ?: 0
            if (year < 1100 || year % 1000 < 10) {
                match
            } else {
                val left = match.take(2)
                val right = match.substring(2, 4).toIntOrNull() ?: 0
                val suffix = if (match.endsWith("s")) "s" else ""
                when {
                    year % 1000 in 100..999 -> {
                        when (right) {
                            0 -> "$left hundred$suffix"
                            in 1..9 -> "$left oh $right$suffix"
                            else -> "$left $right$suffix"
                        }
                    }

                    else -> "$left $right$suffix"
                }
            }
        }
    }

    /**
     * Helper function to format monetary values
     */
    private fun flipMoney(match: String): String {
        val bill = if (match[0] == '$') "dollar" else "pound"
        return if (match.slice(1 until match.length).toDoubleOrNull() == null) {
            "${match.slice(1 until match.length)} ${bill}s"
        } else if (!match.contains(".")) {
            val suffix = if (match.slice(1 until match.length) == "1") "" else "s"
            "${match.slice(1 until match.length)} ${bill}$suffix"
        } else {
            val (b, c) = match.slice(1 until match.length).split(".")
            val d = c.padEnd(2, '0').toInt()
            val coins = if (match[0] == '$') {
                if (d == 1) "cent" else "cents"
            } else {
                if (d == 1) "penny" else "pence"
            }
            "$b ${bill}${if (b == "1") "" else "s"} and $d $coins"
        }
    }

    /**
     * Helper function to process decimal numbers
     */
    private fun pointNum(match: String): String {
        val (a, b) = match.split(".")
        return "$a point ${b.toCharArray().joinToString(" ")}"
    }

    /**
     * Normalize text for phonemization
     */
    private fun normalizeText(text: String): String {
        return text
            // 1. Handle quotes and brackets
            .replace("[‘’]".toRegex(), "'")
            .replace("«".toRegex(), "“")
            .replace("»".toRegex(), "”")
            .replace("[“”]".toRegex(), "\"")
            .replace("\\(".toRegex(), "«")
            .replace("\\)".toRegex(), "»")

            // 2. Replace uncommon punctuation marks
            .replace("、".toRegex(), ", ")
            .replace("。".toRegex(), ". ")
            .replace("！".toRegex(), "! ")
            .replace("，".toRegex(), ", ")
            .replace("：".toRegex(), ": ")
            .replace("；".toRegex(), "; ")
            .replace("？".toRegex(), "? ")

            // 3. Whitespace normalization
            .replace("[^\\S \\n]".toRegex(), " ")
            .replace("  +".toRegex(), " ")
            .replace("(?<=\\n) +(?=\\n)".toRegex(), "")

            // 4. Abbreviations
            .replace("\\bD[Rr]\\.(?= [A-Z])".toRegex(), "Doctor")
            .replace("\\b(?:Mr\\.|MR\\.(?= [A-Z]))".toRegex(), "Mister")
            .replace("\\b(?:Ms\\.|MS\\.(?= [A-Z]))".toRegex(), "Miss")
            .replace("\\b(?:Mrs\\.|MRS\\.(?= [A-Z]))".toRegex(), "Mrs")
            .replace("\\betc\\.(?! [A-Z])".toRegex(RegexOption.IGNORE_CASE), "etc")

            // 5. Normalize casual words
            .replace("\\b(y)eah?\\b".toRegex(RegexOption.IGNORE_CASE), "$1e'a")

            // 5. Handle numbers and currencies
            .replace(
                "\\d*\\.\\d+|\\b\\d{4}s?\\b|(?<!:)\\b(?:[1-9]|1[0-2]):[0-5]\\d\\b(?!:)".toRegex(),
                { match -> splitNum(match.value) })
            .replace("(?<=\\d),(?=\\d)".toRegex(), "")
            .replace(
                "[$£]\\d+(?:\\.\\d+)?(?: hundred| thousand| (?:[bm]|tr)illion)*\\b|[$£]\\d+\\.\\d\\d?\\b".toRegex(
                    RegexOption.IGNORE_CASE
                ), { match -> flipMoney(match.value) })
            .replace("\\d*\\.\\d+".toRegex(), { match -> pointNum(match.value) })
            .replace("(?<=\\d)-(?=\\d)".toRegex(), " to ")
            .replace("(?<=\\d)S".toRegex(), " S")

            // 6. Handle possessives
            .replace("(?<=[BCDFGHJ-NP-TV-Z])'?s\\b".toRegex(), "'S")
            .replace("(?<=X')S\\b".toRegex(), "s")

            // 7. Handle hyphenated words/letters
            .replace("(?:[A-Za-z]\\.){2,} [a-z]".toRegex(), { m -> m.value.replace(".", "-") })
            .replace("(?<=[A-Z])\\.(?=[A-Z])".toRegex(RegexOption.IGNORE_CASE), "-")

            // 8. Strip leading and trailing whitespace
            .trim()
    }

    /**
     * Transliterate text using the appropriate transliterator. Currently only supports Japanese
     * as it requires Kanji to Hiragana conversion as ESpeakNG does not support Kanji.
     */
    private fun transliterate(text: String, language: Language): String {
        return when (language.locale) {
            Locale.JAPANESE ->
                _japaneseTransliterator.convertToFurigana(text)
                    .replace("・", "")
                    .replace("ー", "")

            else -> text
        }
    }

    /**
     * Phonemize text using the eSpeak-NG phonemizer
     */
    private fun phonemize(text: String, language: Language): List<String> {
        val transliterated = transliterate(text, language)
        val normalized = normalizeText(transliterated)
        val voice = voiceLanguage(language)

        return split(normalized).filter { !it.first }.map {
            var phonemes = ESpeakNG.getSession().phonemize(it.second, voice)
                .replace("\\([a-z]+\\)".toRegex(), "")
                .replace("kəkˈoːɹoʊ".toRegex(), "kˈoʊkəɹoʊ")
                .replace("kəkˈɔːɹəʊ".toRegex(), "kˈəʊkəɹəʊ")
                .replace("ʲ".toRegex(), "j")
                .replace("r".toRegex(), "ɹ")
                .replace("x".toRegex(), "k")
                .replace("ɬ".toRegex(), "l")
                .replace("-".toRegex(), "—")
                .replace("(?<=[a-zɹː])(?=hˈʌndɹɪd)".toRegex(), " ")
                .replace(" z(?=[;:,.!?¡¿—…\"«»“” ]|\$)\\)".toRegex(), "z")

            phonemes = when (language.locale) {
                Locale.FRENCH ->
                    phonemes
                        .replace("(.)̃".toRegex()) {
                            it.groupValues.last()
                        }

                Locale.ITALIAN ->
                    phonemes
                        .replace("(.)̪".toRegex()) {
                            it.groupValues.last()
                        }

                Locale.CHINESE ->
                    phonemes.replace("[0-9]".toRegex(), "")
                        .replace("j".toRegex(), "ʝ")
                        .replace("(.)̪".toRegex()) {
                            it.groupValues.last()
                        }

                Locale.forLanguageTag("pt") ->
                    phonemes.replace("(.)̃".toRegex()) {
                        it.groupValues.last()
                    }

                Locale.JAPANESE ->
                    phonemes.replace("ä".toRegex(), "a")
                        .replace("ᵝ".toRegex(), "ʷ")
                        .replace("ɽ".toRegex(), "ɹ")
                        .replace("ũ".toRegex(), "u")
                        .replace("(.)̞".toRegex()) {
                            it.groupValues.last()
                        }

                Locale.ENGLISH ->
                    phonemes
                        .replace("(?<=nˈaɪn)ti(?!ː)".toRegex(), "di")

                else -> phonemes
            }

            phonemes.trim()
        }
    }

    private fun voiceLanguage(
        language: Language
    ): String {
        return when (language) {
            Language.fromIsoCode("en") -> return "en-US"
            Language.fromIsoCode("pt") -> return "pt-BR"
            Language.fromIsoCode("zh") -> return "cmn"
            else -> return language.isoCode
        }
    }

    override fun tokenize(
        text: String,
        language: Language,
        splitSentences: Boolean
    ): List<LongArray> {
        try {
            val phonemes = phonemize(text, language)

            return phonemes.map {
                if (it.length > MAX_PHONEME_LENGTH) {
                    throw IllegalArgumentException(
                        "Text is too long, must be less than $MAX_PHONEME_LENGTH phonemes"
                    )
                }

                _pauzeToken + it.map { char ->
                    val token = _vocabulary[char.toString()]
                    if (token == null) {
                        throw IllegalArgumentException("Unknown symbol: $char")
                    }
                    token.toLong()
                }.toLongArray() + _pauzeToken
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Tokenizing text", e)
        }
    }

    companion object {
        private val _pauzeToken = longArrayOf(0)
        private val _punctuationPattern = Regex(
            pattern = "(\\s*[${Regex.escape(";:,.!?¡¿—…\"«»“”(){}[]")}]+\\s*)+",
            option = RegexOption.IGNORE_CASE
        )
        private val _defaultVocabulary = (listOf("$") +
                ";:,.!?¡¿—…\"«»“” ".toList() +
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toList() +
                "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ".toList())
            .mapIndexed { index, symbol -> symbol.toString() to index }.toMap()
    }
}