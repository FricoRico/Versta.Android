package app.versta.translate.adapter.outbound

import kotlinx.coroutines.CoroutineScope

/**
 * Lifecycle contract for audio capture feeding a recognizer. Implemented by
 * [MicrophoneCapture]; defined as an interface so
 * [WhisperSpeechRecognition] can drive a test double that never touches
 * [android.media.AudioRecord].
 */
interface CaptureHandle {
    fun start(scope: CoroutineScope)

    fun stop()

    suspend fun join()
}
