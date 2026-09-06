package app.versta.translate.adapter.outbound

import android.os.SystemClock
import app.versta.translate.core.entity.FontWeight
import app.versta.translate.core.entity.OcrAnalysisResult
import app.versta.translate.core.entity.OcrLineResult
import app.versta.translate.core.entity.ObjectCharacterRecogniserColors
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/** Shared line mapping used by the live analyzer and the stills pipeline. */
fun mapOcrLineResult(line: OcrLineResult): ObjectCharacterRecogniserResult {
    return ObjectCharacterRecogniserResult(
        points = line.box.points,
        score = line.score,
        text = line.text,
        colors = line.colors ?: ObjectCharacterRecogniserColors.DEFAULT,
        fontWeight = if (line.bold) FontWeight.BOLD else FontWeight.REGULAR,
        blockId = line.blockId,
        strip = line.strip,
    )
}

/**
 * Live-frame OCR pump, driven once per camera frame by the GL preview loop
 * ([app.versta.translate.ui.component.VerstaGlSurfaceView.liveFrameSink])
 * rather than by a separate CameraX analysis stream: feeding the tracker the
 * very pixels that get presented is what keeps the overlay grounded on the
 * scene. [process] runs on the GL thread; per-frame native work must fit the
 * frame budget, so ONLY cheap tracking ticks run inline — the native track
 * and analyze calls are identical while an anchor lives (`lockedTick`), and
 * the one heavy path, an anchorless acquire (full detect+recognize), is
 * handed a frame SNAPSHOT on a single-slot worker (drop-if-busy; reference:
 * translator-rs AcquireRequest/TrackerCompute). The GL thread never touches
 * native while an acquire is in flight: both calls take the engine mutex and
 * would re-create the stall.
 */
class ObjectCharacterRecognitionAnalyzer(
    private val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference,
    private val scope: CoroutineScope,
    private val acquireDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val minAcquireIntervalMs: Long = 250,
    private val maxAcquireIntervalMs: Long = 2_000,
    private val forcedRecognizer: () -> String? = { null },
    private val onFrameProcessed: (List<ObjectCharacterRecogniserResult>, Int, Int) -> Unit,
) {

    private class PendingAcquire(val result: OcrAnalysisResult, val epoch: Int)

    /** Whether the native tracker holds a live anchor (tracked lines or a
     *  completed acquire). Starts false: the very first frames acquire
     *  instead of probing empty tracks. */
    private var _anchored = false
    @Volatile private var _acquireInFlight = false
    @Volatile private var _pendingAcquire: PendingAcquire? = null
    private var _acquireEpoch = 0
    private var _lastAcquireAt = 0L

    /** Adaptive dispatch interval: doubles on every acquire that fails to
     *  produce a usable anchor (scene moving too much to lock), resets when
     *  one sticks — keeps a churn loop from saturating the CPU on-device. */
    private var _acquireBackoffMs = minAcquireIntervalMs

    // Snapshot buffer for the worker: owned by the analyzer, written only
    // while no acquire is in flight.
    private var _snapshot: ByteBuffer? = null

    // Content cursors: while the anchor lives, line CONTENT is immutable
    // (tracked frames re-pose the same canonical overlays), so presentation
    // publishes only when the native epoch/version cursors move — per-frame
    // marshaling + mapping was the measured GL sink cost (28–55 ms on-device).
    private var _lastAnchorEpoch = -1
    private var _lastContentVersion = -1

    fun reset() {
        _anchored = false
        _graceResults = emptyList()
        _graceFrames = 0
        _pendingAcquire = null
        _lastAnchorEpoch = -1
        _lastContentVersion = -1
        // Late worker results carry an older epoch and are dropped on apply.
        _acquireEpoch++
    }

    /**
     * Runs one frame: applies a completed acquire, ticks the anchor while it
     * lives (presenting line content ONLY when its native cursor moves), or
     * dispatches an async acquire while anchorless. Returns this frame's
     * canonical→current homography (9 floats) for the GL overlay composite,
     * or null when nothing is drawable.
     */
    fun process(input: ByteBuffer, width: Int, height: Int): FloatArray? {
        // 1) A finished acquire lands here: publish its content (fresh strip
        //    epoch → rebake downstream), then keep going — the current frame
        //    still wants its own ticked pose.
        _pendingAcquire?.let { pending ->
            _pendingAcquire = null
            if (pending.epoch == _acquireEpoch) {
                _anchored = pending.result.lines.isNotEmpty()
                if (_anchored) _acquireBackoffMs = minAcquireIntervalMs
                present(pending.result)
            }
        }

        // 2) Never call native while the worker holds the engine mutex —
        //    except the try-locked stillness probe, which just skips if busy.
        if (_acquireInFlight) {
            objectCharacterRecognitionInference.probeLive(input, width, height)
            present(OcrAnalysisResult(emptyList(), width, height))
            return null
        }

        // 3) Anchorless: feed the stillness gate EVERY frame (its diff
        //    window needs frame-rate samples; the throttled acquire worker
        //    alone starves it, and a closed quiet gate silences live OCR
        //    entirely), then snapshot this frame for the worker.
        if (!_anchored) {
            objectCharacterRecognitionInference.probeLive(input, width, height)
            dispatchAcquire(input, width, height)
            present(OcrAnalysisResult(emptyList(), width, height))
            return null
        }

        // 4) Anchored: scalar tick. Null = anchor lost mid-tick → re-acquire.
        val tick = objectCharacterRecognitionInference.tickLive(input, width, height)
        if (tick == null) {
            _anchored = false
            dispatchAcquire(input, width, height)
            present(OcrAnalysisResult(emptyList(), width, height))
            return null
        }

        // Content presentation is cursor-gated: line payload crosses JNI
        // only when the anchor swapped or fresh strips landed.
        if (tick.anchorEpoch != _lastAnchorEpoch || tick.contentVersion != _lastContentVersion) {
            _lastAnchorEpoch = tick.anchorEpoch
            _lastContentVersion = tick.contentVersion
            objectCharacterRecognitionInference.pullLiveContent(width, height)?.let { pulled ->
                present(pulled)
            }
        }

        return tick.homography
    }

    /**
     * Hands one frame's pixels to the acquire worker. The copy is the whole
     * point: the caller's readback buffer is reused next frame. Latest-wins
     * is implicit in the single in-flight flag — a dropped snapshot is never
     * more than one camera frame stale on the next dispatch.
     */
    private fun dispatchAcquire(input: ByteBuffer, width: Int, height: Int) {
        val nowMs = SystemClock.elapsedRealtime()
        if (_acquireInFlight || nowMs - _lastAcquireAt < _acquireBackoffMs) return
        _lastAcquireAt = nowMs
        _acquireBackoffMs = (_acquireBackoffMs * 2).coerceAtMost(maxAcquireIntervalMs)
        _acquireInFlight = true
        val epoch = ++_acquireEpoch

        val snap = ByteBuffer.allocateDirect(width * height * 4).let { fresh ->
            val current = _snapshot
            if (current != null && current.capacity() == fresh.capacity()) current else fresh
        }
        _snapshot = snap
        snap.clear()
        val src = input.duplicate()
        src.clear()
        snap.put(src)
        snap.clear()

        val forced = forcedRecognizer()
        scope.launch(acquireDispatcher) {
            val result = runCatching {
                objectCharacterRecognitionInference.analyzeLive(snap, width, height, forced)
            }.getOrNull()
            // Epoch guards against a reset between dispatch and completion.
            if (result != null && epoch == _acquireEpoch) {
                _pendingAcquire = PendingAcquire(result, epoch)
            }
            _acquireInFlight = false
        }
    }

    /** The loss-hide grace + present hop (reference LOSS_HIDE_AFTER_FRAMES). */
    private fun present(result: OcrAnalysisResult) {
        val results = result.lines.map(::mapOcrLineResult)
        val present = if (results.isEmpty() && _graceResults.isNotEmpty() && _graceFrames < GRACE_FRAMES) {
            _graceFrames++
            _graceResults
        } else {
            if (results.isNotEmpty()) {
                _graceResults = results
                _graceFrames = 0
            }
            results
        }
        // Presented synchronously on the GL thread: every async hop on this
        // path is a display frame of overlay lag.
        onFrameProcessed(present, result.width, result.height)
    }

    private var _graceResults: List<ObjectCharacterRecogniserResult> = emptyList()
    private var _graceFrames = 0

    companion object {
        /** Frames an empty result keeps emitting the last overlay (reference:
         *  translator-live LOSS_HIDE_AFTER_FRAMES). */
        private const val GRACE_FRAMES = 4
    }
}
