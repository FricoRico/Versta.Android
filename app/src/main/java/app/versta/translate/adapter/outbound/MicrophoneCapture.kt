package app.versta.translate.adapter.outbound

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import app.versta.translate.bridge.whisper.SpeechRecognitionEngine
import app.versta.translate.utils.SPECTRUM_BAND_COUNT
import app.versta.translate.utils.fftMagnitudes
import app.versta.translate.utils.spectrumBands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Continuous microphone capture at 16 kHz mono, feeding float PCM chunks to a
 * [SpeechRecognitionEngine].
 *
 * Audio is read on a dedicated coroutine and pushed via
 * [SpeechRecognitionEngine.feed]; the recognizer's own
 * [app.versta.translate.bridge.whisper.Whisper.process] should be
 * driven separately (e.g. on a timer) so capture and inference stay decoupled
 * and the microphone thread never blocks on model execution.
 */
class MicrophoneCapture(
    private val recognizer: SpeechRecognitionEngine,
    private val sampleRate: Int = WHISPER_SAMPLE_RATE,
) : AudioCapture {
    @Volatile
    private var _record: AudioRecord? = null

    @Volatile
    private var _running = false
    private var _job: Job? = null

    private val _spectrum = MutableStateFlow(FloatArray(SPECTRUM_BAND_COUNT))
    override val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun start(scope: CoroutineScope) {
        if (_running) {
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw IllegalStateException("Microphone not available (buffer size $minBuffer)")
        }

        val bufferSize = min(minBuffer * 4, Int.MAX_VALUE)
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Failed to initialize AudioRecord")
        }

        // Publish only after startRecording succeeds: a failed start must not
        // leave stop() calling stop()/release() on a never-started instance.
        try {
            record.startRecording()
        } catch (e: IllegalStateException) {
            record.release()
            throw e
        }
        _record = record
        _running = true
        _spectrum.value = FloatArray(SPECTRUM_BAND_COUNT)

        _job = scope.launch(Dispatchers.IO) {
            // Small read chunks on a big internal buffer: the spectrum for the
            // waveform updates every 64 ms instead of waiting for the whole
            // buffer (250+ ms), which is what made the visualization lag.
            val shorts = ShortArray(READ_CHUNK_SAMPLES)
            val floats = FloatArray(READ_CHUNK_SAMPLES)
            while (isActive && _running) {
                val read = record.read(shorts, 0, shorts.size)
                if (read <= 0) {
                    continue
                }
                for (i in 0 until read) {
                    floats[i] = shorts[i] / 32768.0f
                }
                val fftSize = Integer.highestOneBit(read)
                if (fftSize >= MIN_FFT_SIZE) {
                    _spectrum.value = floats.fftMagnitudes(fftSize).spectrumBands(sampleRate = sampleRate)
                }
                recognizer.feed(floats, read)
            }
        }
    }

    override fun stop() {
        _running = false
        // Cancel but keep the reference so join() can wait for the coroutine
        // to fully exit before the recognizer is destroyed.
        _job?.cancel()
        _record?.stop()
        _record?.release()
        _record = null
        _spectrum.value = FloatArray(SPECTRUM_BAND_COUNT)
    }

    /**
     * Suspends until the capture coroutine has fully exited. Call after
     * [stop] before tearing down the recognizer so no in-flight [feed] can
     * race the native destroy.
     */
    override suspend fun join() {
        _job?.join()
    }

    companion object {
        const val WHISPER_SAMPLE_RATE = 16000

        // Smallest FFT worth visualizing: 256 samples at 16 kHz = 16 ms of
        // audio and ~63 Hz per bin, enough to resolve the vocal range.
        private const val MIN_FFT_SIZE = 256

        // Spectrum update cadence: 1024 samples at 16 kHz = one FFT every 64 ms,
        // ~16 spectrum frames per second. The AudioRecord buffer stays 4x the
        // platform minimum so bursts of UI-jank cannot drop audio.
        private const val READ_CHUNK_SAMPLES = 1024
    }
}
