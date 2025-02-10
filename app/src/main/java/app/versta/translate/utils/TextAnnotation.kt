package app.versta.translate.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Annotates the [sentence] with the [annotation] using the provided [style].
 *
 * @param sentence The sentence to annotate.
 * @param annotation The annotation to apply to the sentence.
 * @param style The style to apply to the annotation.
 */
fun annotateSentence(sentence: String, annotation: String, style: SpanStyle): AnnotatedString {
    val startIndex = sentence.indexOf(annotation)
    val endIndex = startIndex + annotation.length

    return if (startIndex == -1) {
        buildAnnotatedString {
            append(sentence)
        }
    } else {
        buildAnnotatedString {
            append(sentence.substring(0, startIndex))
            withStyle(style) {
                append(sentence.substring(startIndex, endIndex))
            }
            append(sentence.substring(endIndex))
        }
    }
}
