package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.SpeechRecognitionInferenceFiles
import app.versta.translate.core.entity.SpeechRecognitionSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/**
 * Thrown by [SpeechRecognitionInference.start] when the microphone itself
 * cannot be captured (held by another app, AudioRecord init failure) while
 * the recognizer is loaded and healthy. Distinct from a plain
 * [IllegalStateException], which signals the inference was never loaded, so
 * callers do not misreport a capture failure as "model not loaded".
 */
class MicrophoneCaptureException(cause: Throwable) : RuntimeException(cause)

interface SpeechRecognitionInference {
    /**
     * Sets the language recognition should transcribe as. Takes effect on
     * the next [load].
     */
    fun setSourceLanguage(isoCode: String?)

    /**
     * Loads the whisper + VAD model bundle and prepares the recognizer.
     */
    fun load(files: SpeechRecognitionInferenceFiles, threads: Int = 4)

    /**
     * Starts the real-time recognition loop: captures microphone audio and emits
     * recognized [SpeechRecognitionSegment]s through [segments].
     */
    fun start(scope: CoroutineScope)

    /**
     * Stops the microphone and begins finalizing. Audio already buffered is
     * still transcribed to completion — [finalizing] stays true until the last
     * final segment has been emitted. Use [close] to tear down immediately
     * instead of draining.
     */
    fun stop()

    /**
     * Flow of recognized utterances accumulated since the last [start]. Every
     * utterance-batch pass emits exactly one segment per finished utterance —
     * there is no separate provisional/partial flow.
     */
    val segments: Flow<List<SpeechRecognitionSegment>>

    /**
     * Running real-time factor (audio seconds processed / compute seconds),
     * accounting for commit passes only. Values above 1.0 mean the pipeline
     * keeps up with live audio.
     */
    val rtf: Flow<Float?>

    /**
     * Flow indicating whether the microphone is currently capturing. Goes
     * false immediately on [stop], while [finalizing] may still be true.
     */
    val listening: Flow<Boolean>

    /**
     * True between [stop] and the last final segment of that session. The
     * microphone is already off; buffered audio is still being transcribed.
     * Surface this as a busy state — [start] is rejected while it holds.
     */
    val finalizing: Flow<Boolean>

    /**
     * Latest microphone input spectrum, folded into log-spaced vocal-range
     * bands (values normalized to [0, 1], dBFS-mapped), emitted once per
     * captured buffer while [listening]. Silent (all zeros) when idle or
     * stopped. Drives voice-activity UI such as the spectrum waveform.
     */
    val spectrum: Flow<FloatArray>

    fun close()
}
