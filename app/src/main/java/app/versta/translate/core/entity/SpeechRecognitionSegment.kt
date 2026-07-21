package app.versta.translate.core.entity

/**
 * A single recognized speech utterance. Every segment is final in the
 * utterance-batch design — there is no partial/provisional variant.
 */
data class SpeechRecognitionSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)
