package app.versta.translate.core.entity

/**
 * Per-session metrics snapshot returned from the native whisper.cpp layer.
 *
 * Session counters are cumulative since the last
 * [app.versta.translate.bridge.whisper.WhisperRecognizer.reset] and are used
 * to derive [rtf] as `processedAudioSec * 1000 / commitComputeMs`.
 * [passCount] counts whisper_full calls (one per transcribed utterance);
 * [vadSkipCount] counts process() calls where the VAD segmenter found no
 * utterance ready yet (including the safety-valve drop of prolonged
 * silence/noise) — neither of those invoke whisper_full, so they do not add
 * to [commitComputeMs].
 *
 * [lastPassResult] mirrors the raw `whisper_full` return value; non-zero
 * means the pass aborted (see [abortCount]).
 */
data class SpeechRecognitionMetrics(
    val passCount: Long,
    val abortCount: Long,
    val vadSkipCount: Long,
    val processedAudioSec: Double,
    val commitComputeMs: Double,
    val rtf: Double,
    val lastPassElapsedMs: Long,
    val lastPassWindowMs: Long,
    val lastPassNSamples: Long,
    val lastPassAudioCtx: Int,
    val lastPassMaxTokens: Int,
    val lastPassBudgetMs: Long,
    val lastPassResult: Int,
    val lastPassWasFlush: Boolean,
    val lastPassEncodeMs: Float,
    val lastPassDecodeMs: Float,
    val lastPassBatchdMs: Float,
    val lastPassNEncode: Int,
    val lastPassNDecode: Int,
    val lastPassNBatchd: Int,
)
