package app.versta.translate.adapter.outbound

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle contract for audio capture feeding a recognizer. Implemented by
 * [MicrophoneCapture]; defined as an interface so
 * [WhisperSpeechRecognition] can drive a test double that never touches
 * [android.media.AudioRecord].
 */
interface AudioCapture {
    /**
     * Latest input spectrum, folded into log-spaced vocal-range bands
     * (bin values normalized to [0, 1], dBFS-mapped), emitted once per
     * captured buffer while running. Resets to silence on [stop]. Drives
     * voice-activity UI such as the spectrum waveform.
     */
    val spectrum: StateFlow<FloatArray>

    fun start(scope: CoroutineScope)

    fun stop()

    suspend fun join()
}
