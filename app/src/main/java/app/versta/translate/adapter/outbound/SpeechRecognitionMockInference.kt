package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.SpeechRecognitionInferenceFiles
import app.versta.translate.core.entity.SpeechRecognitionSegment
import app.versta.translate.utils.SPECTRUM_BAND_COUNT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SpeechRecognitionMockInference : SpeechRecognitionInference {
    override fun setSourceLanguage(isoCode: String?) {
        return
    }

    override fun load(files: SpeechRecognitionInferenceFiles, threads: Int) {
        return
    }

    override fun start(scope: CoroutineScope) {
        return
    }

    override fun stop() {
        return
    }

    override val segments: Flow<List<SpeechRecognitionSegment>> = MutableStateFlow(emptyList())
    override val rtf: Flow<Float?> = MutableStateFlow(null)
    override val listening: Flow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val finalizing: Flow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val spectrum: Flow<FloatArray> =
        MutableStateFlow(FloatArray(SPECTRUM_BAND_COUNT)).asStateFlow()

    override fun close() {
        return
    }
}
