package app.versta.translate.bridge.whisper

import app.versta.translate.core.entity.SpeechRecognitionInitialPrompts
import app.versta.translate.core.entity.SpeechRecognitionMetrics
import timber.log.Timber

/**
 * Utterance-batch speech recognizer backed by whisper.cpp with Silero VAD
 * segmentation. Audio is pushed continuously via [feed]; [process] checks
 * whether a complete utterance has accumulated (confirmed by trailing
 * silence, or a forced cut once [maxUtteranceMs] is reached) and, if so,
 * transcribes it in a single pass and emits exactly one segment via
 * [WhisperSegmentCallback.onSegment].
 *
 * This replaces an earlier fixed-window streaming design (~2.6 s windows
 * committed via a DTW-timestamp seam) that traded accuracy for low-latency
 * partial text. There is no partial/provisional output here: every emitted
 * segment is a finished utterance.
 *
 * [flush] drains any remaining buffered audio as one or more final passes,
 * intended for stop() so the user's last words are not dropped even if no
 * trailing silence was ever confirmed.
 *
 * Context continuity across utterances comes from Whisper's own decoder
 * prompt-conditioning: each utterance's decoded tokens automatically prime
 * the next one within a session (see [WhisperSegmentCallback.onSegment]'s
 * `contextTokenIds`, which the caller should persist and feed back via
 * [setCarriedContext] to resume that chain across sessions — e.g. after a
 * language swap and back within a short window). With no carry set, decoding
 * falls back to [initialPrompt], if any.
 *
 * @param model              the [WhisperModel] whose context this recognizer
 *                           decodes with.
 * @param callback           receives each transcribed utterance.
 * @param vadEnabled         enable the Silero VAD utterance segmenter.
 *                           Without it, [process] never finds a boundary and
 *                           only [flush] (on stop()) ever emits anything.
 * @param vadThreshold       Silero VAD probability threshold (0..1).
 * @param language           ISO 639-1 language code hint. When null or empty,
 *                           whisper auto-detects the language from audio.
 * @param initialPrompt      decoder priming text (whisper's `initial_prompt`)
 *                           used only when there is no carried context (see
 *                           [setCarriedContext]). Defaults to the
 *                           [language]'s entry in
 *                           [SpeechRecognitionInitialPrompts], if any.
 * @param noSpeechThreshold  post-decode gate: an utterance whose no_speech
 *                           probability exceeds this has its text discarded
 *                           entirely, since whisper suppresses a segment
 *                           internally only when no_speech is high AND
 *                           avg_logprob is low — a *confident* hallucination
 *                           over silence or music is emitted regardless.
 *                           Matches whisper's own 0.6 default rather than
 *                           sitting above it, since this drops text outright
 *                           with no fallback — the VAD gates upstream (the
 *                           native recognizer's speech-presence check before
 *                           flushing, and its onset-run requirement) are the
 *                           primary defense against hallucination.
 * @param endpointSilenceMs  confirmed trailing silence (ms) after the last
 *                           detected speech before an utterance is
 *                           considered finished. Larger than the VAD's own
 *                           internal segment-boundary threshold so a
 *                           mid-sentence breath does not end dictation early.
 * @param maxUtteranceMs     forced-split ceiling (ms) for one utterance,
 *                           safely under whisper's ~30 s hard context limit.
 *                           Every transcribed window is unconditionally
 *                           clamped to this length; the native side prefers
 *                           to cut at a detected pause near the limit rather
 *                           than mid-word.
 */
class WhisperRecognizer(
    model: WhisperModel,
    callback: WhisperSegmentCallback,
    vadEnabled: Boolean = true,
    vadThreshold: Float = 0.8f,
    language: String? = null,
    initialPrompt: String? = SpeechRecognitionInitialPrompts.forLanguage(language),
    noSpeechThreshold: Float = 0.6f,
    endpointSilenceMs: Int = 600,
    maxUtteranceMs: Int = 15_000,
) : AutoCloseable, WhisperRecognizerHandle {
    @Volatile
    private var handle: Long

    init {
        handle = create(
            model.getHandle(),
            vadEnabled,
            vadThreshold,
            language.orEmpty(),
            initialPrompt.orEmpty(),
            noSpeechThreshold,
            endpointSilenceMs,
            maxUtteranceMs,
        )

        if (handle == 0L) {
            throw RuntimeException("Failed to create WhisperRecognizer")
        }

        setCallback(handle, callback)
    }

    override fun feed(pcm: FloatArray) {
        if (handle == 0L) {
            return
        }
        feed(handle, pcm, pcm.size)
    }

    override fun feed(pcm: FloatArray, length: Int) {
        if (handle == 0L) {
            return
        }
        feed(handle, pcm, length)
    }

    /**
     * Checks buffered audio for a complete utterance (confirmed by trailing
     * silence, or a forced cut at maxUtteranceMs) and transcribes it in one
     * pass when found.
     *
     * @return number of PCM samples consumed (0 if no utterance is ready yet)
     */
    override fun process(): Long {
        if (handle == 0L) {
            throw RuntimeException("Recognizer is not initialized")
        }
        return process(handle)
    }

    /**
     * Transcribes any remaining buffered audio, regardless of trailing
     * silence confirmation. Intended for stop() — ensures no trailing
     * utterance is lost.
     *
     * @return number of PCM samples consumed (0 if nothing was pending)
     */
    override fun flush(): Long {
        if (handle == 0L) {
            throw RuntimeException("Recognizer is not initialized")
        }
        return flush(handle)
    }

    override fun reset() {
        if (handle != 0L) {
            reset(handle)
        }
    }

    /**
     * Seeds the decoder context to use for the NEXT utterance transcribed,
     * taking precedence over [initialPrompt]. Pass the `contextTokenIds`
     * from a previous [WhisperSegmentCallback.onSegment] call (typically
     * persisted across sessions by the caller, e.g. keyed by language with a
     * TTL) to resume that context, or an empty array to fall back to
     * [initialPrompt]. Intended to be called once per session, right after
     * [reset] and before the first [process] call — within a session the
     * chain continues automatically as each utterance primes the next.
     */
    override fun setCarriedContext(tokenIds: IntArray) {
        if (handle == 0L) {
            return
        }
        setCarriedContext(handle, tokenIds)
    }

    /**
     * Replaces the [WhisperSegmentCallback] registered at creation time.
     */
    fun setCallback(callback: WhisperSegmentCallback) {
        if (handle == 0L) {
            throw RuntimeException("Recognizer is not initialized")
        }
        setCallback(handle, callback)
    }

    override fun close() {
        if (handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        destroy(handle)
        handle = 0L
    }

    /**
     * Returns a [SpeechRecognitionMetrics] snapshot containing per-session
     * counters (pass count, abort count, RTF) plus the last pass's snapshot.
     *
     * Returns `null` if the recognizer has not been initialized.
     */
    override fun metrics(): SpeechRecognitionMetrics? {
        if (handle == 0L) {
            return null
        }
        return getMetrics(handle)
    }

    private external fun create(
        modelHandle: Long,
        vadEnabled: Boolean,
        vadThreshold: Float,
        language: String,
        initialPrompt: String,
        noSpeechThreshold: Float,
        endpointSilenceMs: Int,
        maxUtteranceMs: Int,
    ): Long

    private external fun destroy(handle: Long)

    private external fun setCallback(handle: Long, callback: WhisperSegmentCallback)

    private external fun feed(handle: Long, pcm: FloatArray, nSamples: Int)

    private external fun process(handle: Long): Long

    private external fun flush(handle: Long): Long

    private external fun reset(handle: Long)

    private external fun setCarriedContext(handle: Long, tokenIds: IntArray)

    private external fun getMetrics(handle: Long): SpeechRecognitionMetrics?

    companion object {
        private val TAG: String = WhisperRecognizer::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}

/**
 * Receives recognized speech segments from the native recognizer. Each call
 * is one finished utterance — there is no partial/provisional variant in the
 * utterance-batch design (contrast with the older streaming recognizer,
 * which emitted an isFinal flag to distinguish a committed span from an
 * in-flight tail).
 *
 * @param text transcribed text for the utterance
 * @param startMs utterance start time in milliseconds, relative to when
 *   [WhisperRecognizer.feed] started receiving audio for this session
 * @param endMs utterance end time in milliseconds
 * @param contextTokenIds this utterance's decoded token ids, when its decode
 *   passed the native quality gate — suitable for persisting (e.g. in a
 *   [app.versta.translate.adapter.outbound.SpeechContextStore]) and feeding
 *   back via [WhisperRecognizer.setCarriedContext] to resume context in a
 *   later session. Empty when the decode did not pass the gate; callers
 *   should treat that as "no update" rather than "clear the existing
 *   context".
 */
interface WhisperSegmentCallback {
    fun onSegment(text: String, startMs: Long, endMs: Long, contextTokenIds: IntArray)
}
