package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.whisper.WhisperModel
import app.versta.translate.bridge.whisper.WhisperRecognizer
import app.versta.translate.bridge.whisper.WhisperRecognizerHandle
import app.versta.translate.bridge.whisper.WhisperSegmentCallback
import app.versta.translate.core.entity.SpeechRecognitionInferenceFiles
import app.versta.translate.core.entity.SpeechRecognitionSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * whisper.cpp-backed speech recognition, transcribing whole utterances
 * rather than fixed-size streaming windows. Audio is captured via
 * [MicrophoneCapture] and fed into the native recognizer's buffer; the
 * recognizer's own VAD segmenter decides when a complete utterance has
 * accumulated (confirmed trailing silence, or a forced cut on a very long
 * utterance) and transcribes it with a single whisper_full call. There is no
 * live partial/provisional text — [segments] only ever receives finished
 * utterances. Context is chained between utterances via whisper's own
 * decoder conditioning (see [WhisperRecognizer] and [SpeechContextStore]).
 *
 * [stop] cuts the microphone but keeps transcribing audio that is already
 * buffered — [finalizing] stays true until the last segment lands. [close] is
 * the hard-teardown path and does not wait for that drain.
 *
 * [rtf] reports the running real-time factor of the transcription path only.
 *
 * @param modelFactory         creates the native model handle for a model/vad
 *                             pair. Swappable for tests; the default builds a
 *                             real [WhisperModel].
 * @param recognizerFactory    creates the utterance-batch recognizer for a
 *                             loaded model. Swappable for tests; the default
 *                             builds a real [WhisperRecognizer].
 * @param captureFactory       creates the microphone capture feeding the
 *                             recognizer. Swappable for tests; the default
 *                             builds a real [MicrophoneCapture].
 * @param processDispatcher    the dispatcher the transcription loop runs on.
 *                             Defaults to [Dispatchers.Default] because the
 *                             native calls are blocking; tests inject a
 *                             test scheduler.
 */
class WhisperSpeechRecognition(
    private val modelFactory: (modelPath: Path, vadModelPath: Path, threads: Int) -> AutoCloseable =
        { modelPath, vadModelPath, threads -> WhisperModel(modelPath, vadModelPath, threads) },
    private val recognizerFactory: (
        model: AutoCloseable,
        callback: WhisperSegmentCallback,
        language: String?,
    ) -> WhisperRecognizerHandle = { model, callback, language ->
        WhisperRecognizer(model as WhisperModel, callback, language = language)
    },
    private val captureFactory: (recognizer: WhisperRecognizerHandle) -> CaptureHandle = { MicrophoneCapture(it) },
    private val processDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SpeechRecognitionInference, AutoCloseable {
    private var sourceLanguageIsoCode: String? = null

    override fun setSourceLanguage(isoCode: String?) {
        sourceLanguageIsoCode = isoCode
    }

    private var _model: AutoCloseable? = null
    private var _recognizer: WhisperRecognizerHandle? = null
    private var _capture: CaptureHandle? = null
    // Cached identity of the currently loaded native model+language pair, so
    // repeated start toggles within a screen visit skip the 75-811 MB mmap +
    // KleidiAI repack. Language alone changing (e.g. swapping source/target
    // mid-conversation) only recreates the recognizer, not the model — see
    // load() and teardownSession().
    private var _loadedModelPath: String? = null
    private var _loadedLanguage: String? = null

    // Per-language decoder context, keyed independently of _recognizer's own
    // lifetime so it survives exactly the teardown a language swap causes.
    // See the class doc on SpeechContextStore for why this cannot live
    // natively.
    private val _contextStore = SpeechContextStore()

    private val _segments = MutableStateFlow<List<SpeechRecognitionSegment>>(emptyList())
    override val segments = _segments.asStateFlow()

    private val _rtf = MutableStateFlow<Float?>(null)
    override val rtf = _rtf.asStateFlow()

    private val _listening = MutableStateFlow(false)
    override val listening = _listening.asStateFlow()

    private val _finalizing = MutableStateFlow(false)
    override val finalizing = _finalizing.asStateFlow()

    // Set by stop() to keep the process loop alive after the microphone is
    // cut, so audio already buffered is still committed instead of discarded.
    // Cleared by close() (hard teardown) and by the loop's finally block.
    @Volatile
    private var _draining = false

    private var _processJob: Job? = null

    // Serializes native whisper_full calls (process) against the post-loop reset
    // and deferred close teardown. Without this, stop() -> start() while a
    // final transcription is still blocked inside whisper_full lets the new
    // process job run whisper_full on the same whisper_context as the old
    // in-flight call — the native mutex only guards the pending-buffer
    // section, so the ggml scheduler state is mutated concurrently (crash /
    // heap corruption). A Mutex.withLock around process()/flush()/reset()
    // makes the new process wait for the old call to return.
    private val processMutex = Mutex()

    override fun load(files: SpeechRecognitionInferenceFiles, threads: Int) {
        val modelPath = files.model.toString()
        val modelChanged = _model == null || _loadedModelPath != modelPath
        val languageChanged = _loadedLanguage != sourceLanguageIsoCode

        if (_recognizer != null && !modelChanged && !languageChanged) {
            Timber.tag(TAG).d("load: model+recognizer already loaded, reusing")
            return
        }

        if (modelChanged) {
            // Full teardown: the model itself must be reloaded, so the
            // recognizer (which holds a pointer into the model's context)
            // cannot survive it either.
            teardownSession(closeModel = true)
        } else if (_recognizer != null) {
            // Model can be reused; only the language-specific recognizer
            // changes. This is the common case for a mid-conversation
            // language swap and is why it does not repeat the mmap + repack.
            teardownSession(closeModel = false)
        }

        val model = _model ?: modelFactory(files.model, files.vad, threads).also {
            Timber.tag(TAG).d("load: model=${files.model} vad=${files.vad} threads=$threads")
            _model = it
            _loadedModelPath = modelPath
        }

        val callback = object : WhisperSegmentCallback {
            override fun onSegment(
                text: String,
                startMs: Long,
                endMs: Long,
                contextTokenIds: IntArray,
            ) {
                val trimmed = text.trim()
                // contextTokenIds is non-empty only when the native quality
                // gate passed; record() already no-ops on an empty array, so
                // this is unconditional regardless of the degenerate check
                // below — the two gates judge different things (decode
                // confidence vs. display-worthy text) and should not be
                // conflated.
                _contextStore.record(sourceLanguageIsoCode, contextTokenIds)
                if (!isDegenerate(trimmed)) {
                    _segments.value = _segments.value + SpeechRecognitionSegment(trimmed, startMs, endMs)
                }
            }
        }
        val recognizer = recognizerFactory(model, callback, sourceLanguageIsoCode)

        _recognizer = recognizer
        _capture = captureFactory(recognizer)
        _loadedLanguage = sourceLanguageIsoCode
        Timber.tag(TAG).d("load: ready")
    }

    override fun start(scope: CoroutineScope) {
        val recognizer = _recognizer ?: throw IllegalStateException("Speech recognition is not loaded")
        val capture = _capture ?: throw IllegalStateException("Speech recognition is not loaded")

        // Rejecting while draining is load-bearing, not just defensive: the
        // previous session's job still owns the whisper_context and will call
        // reset() in its finally block, which would wipe the new session's
        // timeline out from under it.
        if (_listening.value || _draining) {
            Timber.tag(TAG).d("start: ignored (listening=${_listening.value} draining=${_draining})")
            return
        }

        // Resume this language's carried context (if any, still within TTL)
        // — see SpeechContextStore. Called once per session, before the
        // first process() call; within the session the native side chains
        // context from utterance to utterance on its own.
        recognizer.setCarriedContext(_contextStore.get(sourceLanguageIsoCode))

        // _listening flips only after the microphone is actually running: a
        // mic held by another app throws here, and flipping first would wedge
        // the session (listening=true with no capture and no job).
        try {
            capture.start(scope)
        } catch (e: IllegalStateException) {
            throw MicrophoneCaptureException(e)
        }

        _listening.value = true
        _segments.value = emptyList()
        _rtf.value = null

        Timber.tag(TAG).d("start: listening=true")

        _processJob = scope.launch(processDispatcher) {
            var drainStartNs = 0L
            var lastMetricsLogNs = 0L
            try {
                while (isActive && (_listening.value || _draining)) {
                    val draining = _draining
                    if (draining && drainStartNs == 0L) {
                        drainStartNs = System.nanoTime()
                    }
                    val consumed = processMutex.withLock { recognizer.process() }

                    // Throttled native-metrics log + RTF update: surface the
                    // full per-session + last-pass snapshot once per second.
                    // RTF is read from the native side (m.rtf) rather than
                    // computed here — it already counts only actually-decoded
                    // audio against decode-only compute (see
                    // transcribe_utterance in whisper.cc); a Kotlin-side copy
                    // that charged every poll's wall-clock time also counted
                    // dropped/VAD-trimmed audio, which inflated RTF exactly
                    // when the pipeline was dropping the most.
                    val nowNs = System.nanoTime()
                    if (nowNs - lastMetricsLogNs >= 1_000_000_000L) {
                        lastMetricsLogNs = nowNs
                        val m = recognizer.metrics()
                        if (m != null) {
                            _rtf.value = m.rtf.toFloat()
                            Timber.tag(TAG).d(
                                "metrics: pass=%d abort=%d vadSkip=%d audioSec=%.3f " +
                                        "computeMs=%.0f rtf=%.2f lastMs=%d lastWinMs=%d " +
                                        "lastCtx=%d lastTokens=%d budgetMs=%d lastRet=%d " +
                                        "lastFlush=%s encMs=%.1f decMs=%.1f " +
                                        "batMs=%.1f nEnc=%d nDec=%d nBat=%d",
                                m.passCount, m.abortCount, m.vadSkipCount,
                                m.processedAudioSec, m.commitComputeMs, m.rtf,
                                m.lastPassElapsedMs, m.lastPassWindowMs,
                                m.lastPassAudioCtx, m.lastPassMaxTokens,
                                m.lastPassBudgetMs, m.lastPassResult,
                                m.lastPassWasFlush, m.lastPassEncodeMs,
                                m.lastPassDecodeMs, m.lastPassBatchdMs,
                                m.lastPassNEncode, m.lastPassNDecode, m.lastPassNBatchd
                            )
                        }
                    }

                    if (draining) {
                        // process() returning 0 means no utterance is ready —
                        // the flush in the finally block handles whatever
                        // remains buffered.
                        if (consumed == 0L) {
                            Timber.tag(TAG).d("drain: buffered audio exhausted, flushing remainder")
                            break
                        }
                        val drainMs = (System.nanoTime() - drainStartNs) / 1_000_000L
                        if (drainMs >= MAX_DRAIN_MS) {
                            Timber.tag(TAG).w("drain: exceeded ${MAX_DRAIN_MS}ms, flushing remainder early")
                            break
                        }
                        // No poll delay: get the user their final text as fast as
                        // the device allows.
                        continue
                    }

                    delay(RECOGNITION_POLL_MS)
                }
            } catch (_: CancellationException) {
                // expected on close()
            } finally {
                // NonCancellable because on the close() path this runs inside an
                // already-cancelled coroutine: a contended (therefore suspending)
                // mutex acquisition would throw straight back out, skipping the
                // reset and leaving the next session on stale recognizer state.
                withContext(NonCancellable) {
                    try {
                        // Flush whatever remains buffered (there is no seam
                        // tail to carry — an utterance is either fully
                        // decoded or not decoded at all) so the user's last
                        // words are emitted, then reset for the next session.
                        // Held under the same Mutex as process() so a start()
                        // racing the teardown cannot run whisper_full
                        // concurrently or reset under an in-flight call.
                        processMutex.withLock {
                            recognizer.flush()
                            recognizer.reset()
                        }
                    } finally {
                        // Must run even if the flush failed, or the UI is stuck
                        // in `finalizing` with no job left alive to clear it.
                        _draining = false
                        _finalizing.value = false
                        _listening.value = false
                        Timber.tag(TAG).d("finalize: done (segments collected=${_segments.value.size})")
                    }
                }
            }
        }
    }

    override fun stop() {
        if (!_listening.value) {
            return
        }
        Timber.tag(TAG).d("stop: listening=false, draining buffered audio (segments collected=${_segments.value.size})")
        _listening.value = false
        // Microphone off immediately — the user asked to stop recording, not
        // to discard what they already said.
        _capture?.stop()
        // The process job is deliberately NOT cancelled: it keeps trying to
        // transcribe buffered audio until process() runs dry, then flushes in
        // its finally block. close() is the hard-teardown path.
        _draining = true
        _finalizing.value = true
    }

    override fun close() {
        // Hard teardown clears the carried context too: a screen leave (or
        // app backgrounding) is a much stronger signal than a language swap
        // that the conversation this context belonged to is over.
        _contextStore.clear()
        teardownSession(closeModel = true)
    }

    /**
     * Tears down the current recognizer/capture pair, optionally also the
     * [WhisperModel] itself. Shared by [close] (full teardown) and [load]'s
     * language-only-changed path (keeps the model resident so a mid-
     * conversation swap does not repeat the mmap + KleidiAI repack).
     *
     * Destroys native handles on a background thread only after both the
     * process worker and the capture coroutine have fully exited — this
     * prevents a use-after-free if the worker is still inside whisper_full
     * (native calls are not interrupted by cancellation). A fresh
     * fire-and-forget scope per call, since this instance is an app-scoped
     * singleton torn down repeatedly over its lifetime; a single long-lived
     * scope cancelled inside its own coroutine would silently no-op from the
     * second call onward, leaking the whisper context each time.
     */
    private fun teardownSession(closeModel: Boolean) {
        stop()
        // Unlike stop(), do NOT wait for buffered audio to drain — this runs
        // from a hard teardown (close()) or a load() reload, where blocking
        // for seconds of catch-up transcription that nobody will see is pure
        // latency. Clearing _draining first stops the loop re-entering the
        // drain branch; the finally block still flushes and resets.
        _draining = false
        _finalizing.value = false
        _processJob?.cancel()
        val job = _processJob
        val capture = _capture
        val recognizer = _recognizer
        val model = if (closeModel) _model else null
        _processJob = null
        _recognizer = null
        _capture = null
        _loadedLanguage = null
        if (closeModel) {
            _model = null
            _loadedModelPath = null
        }
        if (job == null && capture == null && recognizer == null && model == null) {
            return
        }
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            job?.join()
            capture?.join()
            recognizer?.close()
            model?.close()
        }
    }

    companion object {
        private const val TAG = "WhisperSpeechRecognition"
        private const val RECOGNITION_POLL_MS = 200L

        // Upper bound on post-stop draining. Hitting this means the device
        // cannot keep up; flush what is left rather than hold the UI in a
        // finalizing state indefinitely.
        private const val MAX_DRAIN_MS = 15_000L

        // Punctuation-only segments: a decode of near-silence can emit stray
        // punctuation with nothing else. Includes CJK clause punctuation, not
        // just ASCII, so this stays a no-op for languages without spaces.
        private const val DEGENERATE_PUNCTUATION =
            ".,?!;:'’“”\"、。！，．：；？"

        // Orphan contraction tails ("'s", "'d", "'m", "'re", "'ve", "'ll")
        // that a decode can produce on quiet or low-confidence audio without
        // anything else. CJK-safe by construction: matching requires an
        // apostrophe followed ONLY by one or two ASCII letters — a Chinese or
        // Japanese span may open with U+2019, but never continues into a
        // bare ASCII letter pair.
        private val ORPHAN_CONTRACTION = Regex("^['’][A-Za-z]{1,2}$")

        internal fun isDegenerate(text: String): Boolean {
            if (text.isEmpty()) {
                return true
            }
            if (text.all { it.isWhitespace() || it in DEGENERATE_PUNCTUATION }) {
                return true
            }
            return ORPHAN_CONTRACTION.matches(text)
        }
    }
}
