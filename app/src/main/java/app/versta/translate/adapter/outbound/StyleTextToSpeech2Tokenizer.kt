package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.entity.Language
import java.util.Locale

private const val MAX_PHONEME_LENGTH = 512

class StyleTextToSpeech2Tokenizer(
    private val eSpeakNG: ESpeakNG,
    private val openJTalk: OpenJTalk,
) : TextToSpeechTokenizer {
    /**
     * Phonemize text using the eSpeak-NG phonemizer
     */
    private fun phonemize(
        text: String,
        language: Language,
    ): List<String> {
        val normalized = normalizeText(text)
        val voice = voiceLanguage(language)

        return split(normalized).filter { !it.first }.map {
            if (language.locale == Locale.JAPANESE) {
                if (!openJTalk.isReady()) {
                    throw IllegalStateException("OpenJTalk is not ready")
                }

                openJTalk
                    .phonemize(it.second)
                    .replace("G", "gw")
                    .replace("j", "y")
                    .replace("K", "kw")
                    .replace("ç", "hy")
                    .replace("ƫ", "ty")
                    .replace("ɕ", "sh")
                    .replace("ɲ", "ny")
                    .replace("ʥ", "j")
                    .replace("ʦ", "ts")
                    .replace("ʨ", "ch")
                    .replace("ᶀ", "by")
                    .replace("ᶁ", "dy")
                    .replace("ᶃ", "gy")
                    .replace("ᶄ", "ky")
                    .replace("ᶆ", "my")
                    .replace("ᶈ", "py")
                    .replace("ᶉ", "ry")
                    .trim()
            } else {
                if (!eSpeakNG.isReady()) {
                    throw IllegalStateException("eSpeakNG is not ready")
                }

                var phonemes =
                    eSpeakNG
                        .phoneme(it.second, voice)
                        .replace(ipaAnnotationPattern, "")
                        .replace("kəkˈoːɹoʊ", "kˈoʊkəɹoʊ")
                        .replace("kəkˈɔːɹəʊ", "kˈəʊkəɹəʊ")
                        .replace("ʲ", "j")
                        .replace("r", "ɹ")
                        .replace("x", "k")
                        .replace("ɬ", "l")
                        .replace("-", "—")
                        .replace(hundredSuffixBoundaryPattern, " ")
                        .replace(strayAnnotationClosurePattern, "z")

                phonemes =
                    when (language.locale) {
                        Locale.FRENCH -> {
                            phonemes
                                .replace(combiningTildePattern) {
                                    it.groupValues.last()
                                }
                        }

                        Locale.ITALIAN -> {
                            phonemes
                                .replace(combiningDentalPattern) {
                                    it.groupValues.last()
                                }
                        }

                        Locale.CHINESE -> {
                            phonemes
                                .replace(digitPattern, "")
                                .replace("j", "ʝ")
                                .replace(combiningDentalPattern) {
                                    it.groupValues.last()
                                }
                        }

                        Locale.forLanguageTag("pt") -> {
                            phonemes.replace(combiningTildePattern) {
                                it.groupValues.last()
                            }
                        }

                        Locale.ENGLISH -> {
                            phonemes
                                .replace(ninetyPattern, "di")
                        }

                        else -> {
                            phonemes
                        }
                    }

                phonemes.trim()
            }
        }
    }

    private fun voiceLanguage(
        language: Language,
    ): String =
        when (language) {
            Language.fromIsoCode("en") -> "en-US"
            Language.fromIsoCode("pt") -> "pt-BR"
            Language.fromIsoCode("zh") -> "cmn"
            else -> language.isoCode
        }

    override fun tokenize(
        text: String,
        language: Language,
        splitSentences: Boolean,
    ): List<LongArray> {
        try {
            val phonemes = phonemize(text, language)

            return phonemes.map {
                if (it.length > MAX_PHONEME_LENGTH) {
                    throw IllegalArgumentException(
                        "Text is too long, must be less than $MAX_PHONEME_LENGTH phonemes",
                    )
                }

                _pauzeToken +
                    it
                        .map { char ->
                            val token =
                                _vocabulary[char.toString()]
                                    ?: throw IllegalArgumentException("Unknown symbol: $char")
                            token.toLong()
                        }.toLongArray() + _pauzeToken
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Tokenizing text", e)
        }
    }

    companion object {
        private val _pauzeToken = longArrayOf(0)
        private val _punctuationPattern =
            Regex(
                pattern = "(\\s*[${Regex.escape(";:,.!?¡¿—…\"«»“”(){}[]")}]+\\s*)+",
                option = RegexOption.IGNORE_CASE,
            )
        private val _vocabulary =
            (
                listOf("$") +
                    ";:,.!?¡¿—…\"«»“” ".toList() +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toList() +
                    "ɑɐɒæɓʙβɔɕçɗɖðʤəɘɚɛɜɝɞɟʄɡɠɢʛɦɧħɥʜɨɪʝɭɬɫɮʟɱɯɰŋɳɲɴøɵɸθœɶʘɹɺɾɻʀʁɽʂʃʈʧʉʊʋⱱʌɣɤʍχʎʏʑʐʒʔʡʕʢǀǁǂǃˈˌːˑʼʴʰʱʲʷˠˤ˞↓↑→↗↘'̩'ᵻ".toList()
            ).mapIndexed { index, symbol -> symbol.toString() to index }
                .toMap()

        private val fancyApostrophePattern = Regex("[‘’]")
        private val fancyDoubleQuotePattern = Regex("[“”]")
        private val nonStandardWhitespacePattern = Regex("[^\\S \\n]")
        private val repeatedWhitespacePattern = Regex("  +")
        private val whitespaceBetweenNewlinesPattern = Regex("(?<=\\n) +(?=\\n)")
        private val doctorPattern = Regex("\\bD[Rr]\\.(?= [A-Z])")
        private val misterPattern = Regex("\\b(?:Mr\\.|MR\\.(?= [A-Z]))")
        private val missPattern = Regex("\\b(?:Ms\\.|MS\\.(?= [A-Z]))")
        private val mrsPattern = Regex("\\b(?:Mrs\\.|MRS\\.(?= [A-Z]))")
        private val etceteraPattern = Regex("\\betc\\.(?! [A-Z])", RegexOption.IGNORE_CASE)
        private val yeahPattern = Regex("\\b(y)eah?\\b", RegexOption.IGNORE_CASE)
        private val numberPattern =
            Regex("\\d*\\.\\d+|\\b\\d{4}s?\\b|(?<!:)\\b(?:[1-9]|1[0-2]):[0-5]\\d\\b(?!:)")
        private val groupedThousandsPattern = Regex("(?<=\\d),(?=\\d)")
        private val moneyPattern =
            Regex(
                "[$£]\\d+(?:\\.\\d+)?(?: hundred| thousand| (?:[bm]|tr)illion)*\\b|[$£]\\d+\\.\\d\\d?\\b",
                RegexOption.IGNORE_CASE,
            )
        private val decimalPattern = Regex("\\d*\\.\\d+")
        private val numericRangePattern = Regex("(?<=\\d)-(?=\\d)")
        private val digitPluralPattern = Regex("(?<=\\d)S")
        private val possessivePattern = Regex("(?<=[BCDFGHJ-NP-TV-Z])'?s\\b")
        private val possessiveAfterXPattern = Regex("(?<=X')S\\b")
        private val dottedAbbreviationPattern = Regex("(?:[A-Za-z]\\.){2,} [a-z]")
        private val dottedLetterPattern = Regex("(?<=[A-Z])\\.(?=[A-Z])", RegexOption.IGNORE_CASE)
        private val ipaAnnotationPattern = Regex("\\([a-z]+\\)")
        private val hundredSuffixBoundaryPattern = Regex("(?<=[a-zɹː])(?=hˈʌndɹɪd)")
        private val strayAnnotationClosurePattern = Regex(" z(?=[;:,.!?¡¿—…\"«»“” ]|\$)\\)")
        private val combiningTildePattern = Regex("(.)̃")
        private val combiningDentalPattern = Regex("(.)̪")
        private val digitPattern = Regex("[0-9]")
        private val ninetyPattern = Regex("(?<=nˈaɪn)ti(?!ː)")

        /**
         * Helper function to split a string on a regex, but keep the delimiters
         */
        internal fun split(text: String): List<Pair<Boolean, String>> {
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
        internal fun splitNum(match: String): String =
            if ("." in match) {
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

                        else -> {
                            "$left $right$suffix"
                        }
                    }
                }
            }

        /**
         * Helper function to format monetary values
         */
        internal fun flipMoney(match: String): String {
            val bill = if (match[0] == '$') "dollar" else "pound"
            return if (match.slice(1 until match.length).toDoubleOrNull() == null) {
                "${match.slice(1 until match.length)} ${bill}s"
            } else if (!match.contains(".")) {
                val suffix = if (match.slice(1 until match.length) == "1") "" else "s"
                "${match.slice(1 until match.length)} ${bill}$suffix"
            } else {
                val (b, c) = match.slice(1 until match.length).split(".")
                val d = c.padEnd(2, '0').toInt()
                val coins =
                    if (match[0] == '$') {
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
        internal fun pointNum(match: String): String {
            val (a, b) = match.split(".")
            return "$a point ${b.toCharArray().joinToString(" ")}"
        }

        /**
         * Normalize text for phonemization
         */
        internal fun normalizeText(text: String): String =
            text
                // 1. Handle quotes and brackets
                .replace(fancyApostrophePattern, "'")
                .replace("«", "“")
                .replace("»", "”")
                .replace(fancyDoubleQuotePattern, "\"")
                .replace("(", "«")
                .replace(")", "»")
                // 2. Replace uncommon punctuation marks
                .replace("、", ", ")
                .replace("。", ". ")
                .replace("！", "! ")
                .replace("，", ", ")
                .replace("：", ": ")
                .replace("；", "; ")
                .replace("？", "? ")
                // 3. Whitespace normalization
                .replace(nonStandardWhitespacePattern, " ")
                .replace(repeatedWhitespacePattern, " ")
                .replace(whitespaceBetweenNewlinesPattern, "")
                // 4. Abbreviations
                .replace(doctorPattern, "Doctor")
                .replace(misterPattern, "Mister")
                .replace(missPattern, "Miss")
                .replace(mrsPattern, "Mrs")
                .replace(etceteraPattern, "etc")
                // 5. Normalize casual words
                .replace(yeahPattern, "$1e'a")
                // 5. Handle numbers and currencies
                .replace(numberPattern) { match -> splitNum(match.value) }
                .replace(groupedThousandsPattern, "")
                .replace(moneyPattern) { match -> flipMoney(match.value) }
                .replace(decimalPattern) { match -> pointNum(match.value) }
                .replace(numericRangePattern, " to ")
                .replace(digitPluralPattern, " S")
                // 6. Handle possessives
                .replace(possessivePattern, "'S")
                .replace(possessiveAfterXPattern, "s")
                // 7. Handle hyphenated words/letters
                .replace(dottedAbbreviationPattern) { m -> m.value.replace(".", "-") }
                .replace(dottedLetterPattern, "-")
                // 8. Strip leading and trailing whitespace
                .trim()
    }
}
