package app.versta.translate.utils

class MosesPunctuationNormalizer(
    private val lang: String = "en",
    private val penn: Boolean = true,
    private val normQuoteCommas: Boolean = true,
    private val normNumbers: Boolean = true,
    private val preReplaceUnicodePunct: Boolean = false,
    private val postRemoveControlChars: Boolean = false,
    private val perlParity: Boolean = false
) {
    private val extraWhitespace = listOf(
        "\\r" to "",
        "\\(" to " (",
        "\\)" to ") ",
        " +" to " ",
        "\\) ([.!:?;,])" to ")$1",
        "\\( " to "(",
        " \\)" to ")",
        "(\\d) %" to "$1%",
        " :" to ":",
        " ;" to ";"
    )

    private val normalizeUnicodeIfNotPenn = listOf(
        "`" to "'",
        "''" to " \" "
    )

    private val normalizeUnicode = mutableListOf(
        "„" to "\"",
        "“" to "\"",
        "”" to "\"",
        "–" to "-",
        "—" to " - ",
        " +" to " ",
        "´" to "'",
        "([a-zA-Z])‘([a-zA-Z])" to "$1'$2",
        "([a-zA-Z])’([a-zA-Z])" to "$1'$2",
        "‘" to "'",
        "‚" to "'",
        "’" to "'",
        "''" to "\"",
        "´´" to "\"",
        "…" to "..."
    )

    private val frenchQuotes = mutableListOf(
        "\u00A0«\u00A0" to "\"",
        "«\u00A0" to "\"",
        "«" to "\"",
        "\u00A0»\u00A0" to "\"",
        "\u00A0»" to "\"",
        "»" to "\""
    )

    private val handlePseudoSpaces = listOf(
        "\u00A0%" to "%",
        "nº\u00A0" to "nº ",
        "\u00A0:" to ":",
        "\u00A0ºC" to " ºC",
        "\u00A0cm" to " cm",
        "\u00A0\\?" to "?",
        "\u00A0\\!" to "!",
        "\u00A0;" to ";",
        ",\u00A0" to ", ",
        " +" to " "
    )

    private val enQuotationFollowedByComma = listOf(
        "\"([,.]+)" to "$1\""
    )

    private val deEsFrQuotationFollowedByComma = listOf(
        ",\"" to "\",",
        "(\\.+)\"(\\s*[^<])" to "\"$1$2" // don't fix period at end of sentence
    )

    private val deEsCzCsFr = listOf(
        "(\\d)\u00A0(\\d)" to "$1,$2"
    )

    private val other = listOf(
        "(\\d)\u00A0(\\d)" to "$1.$2"
    )

    private val replaceUnicodePunctuation = listOf(
        "，" to ",",
        "。\\s*" to ". ",
        "、" to ",",
        "”" to "\"",
        "“" to "\"",
        "∶" to ":",
        "：" to ":",
        "？" to "?",
        "《" to "\"",
        "》" to "\"",
        "）" to ")",
        "！" to "!",
        "（" to "(",
        "；" to ";",
        "」" to "\"",
        "「" to "\"",
        "０" to "0",
        "１" to "1",
        "２" to "2",
        "３" to "3",
        "４" to "4",
        "５" to "5",
        "６" to "6",
        "７" to "7",
        "８" to "8",
        "９" to "9",
        "．\\s*" to ". ",
        "～" to "~",
        "’" to "'",
        "…" to "...",
        "━" to "-",
        "〈" to "<",
        "〉" to ">",
        "【" to "[",
        "】" to "]",
        "％" to "%"
    )

    private val substitutions: List<Pair<String, String>>

    init {
        if (perlParity) {
            normalizeUnicode[11] = "’" to "\""
            frenchQuotes[0] = "\u00A0«\u00A0" to " \""
            frenchQuotes[3] = "\u00A0»\u00A0" to "\" "
        }

        val subs = mutableListOf<Pair<String, String>>()
        subs.addAll(extraWhitespace)
        subs.addAll(normalizeUnicode)
        subs.addAll(frenchQuotes)
        subs.addAll(handlePseudoSpaces)

        if (penn) {
            subs.addAll(1, normalizeUnicodeIfNotPenn)
        }

        if (normQuoteCommas) {
            when (lang) {
                "en" -> subs.addAll(enQuotationFollowedByComma)
                "de", "es", "fr" -> subs.addAll(deEsFrQuotationFollowedByComma)
            }
        }

        if (normNumbers) {
            when (lang) {
                "de", "es", "cz", "cs", "fr" -> subs.addAll(deEsCzCsFr)
                else -> subs.addAll(other)
            }
        }

        substitutions = subs
    }

    fun normalize(text: String): String {
        var normalizedText = text

        if (preReplaceUnicodePunct) {
            normalizedText = replaceUnicodePunct(normalizedText)
        }

        for ((pattern, replacement) in substitutions) {
            normalizedText = normalizedText.replace(Regex(pattern), replacement)
        }

        if (postRemoveControlChars) {
            normalizedText = removeControlChars(normalizedText)
        }

        return normalizedText.trim()
    }

    private fun replaceUnicodePunct(text: String): String {
        var replacedText = text
        for ((pattern, replacement) in replaceUnicodePunctuation) {
            replacedText = replacedText.replace(Regex(pattern), replacement)
        }
        return replacedText
    }

    private fun removeControlChars(text: String): String {
        return Regex("\\p{C}").replace(text, "")
    }
}