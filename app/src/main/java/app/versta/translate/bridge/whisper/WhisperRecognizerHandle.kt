package app.versta.translate.bridge.whisper

import app.versta.translate.core.entity.SpeechRecognitionMetrics

/**
 * The recognizer operations [app.versta.translate.adapter.outbound.WhisperSpeechRecognition]
 * drives: buffering audio, committing finished utterances, flushing the tail,
 * resetting between sessions, seeding carried context and reading metrics.
 * Implemented by [WhisperRecognizer]; defined as an interface so the
 * utterance-batch driver can be tested against a fake that never invokes the
 * native library.
 */
interface WhisperRecognizerHandle : AutoCloseable {
    fun feed(pcm: FloatArray)

    /**
     * Feeds only the first [length] samples of [pcm]. Lets implementations
     * that accept a length directly (e.g. [WhisperRecognizer]'s native call)
     * avoid trimming [pcm] into a fresh array on every call; the default here
     * falls back to a copy for implementations that don't.
     */
    fun feed(pcm: FloatArray, length: Int) {
        feed(pcm.copyOf(length))
    }

    fun process(): Long

    fun flush(): Long

    fun reset()

    fun setCarriedContext(tokens: IntArray)

    fun metrics(): SpeechRecognitionMetrics?

    override fun close()
}
