package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class ObjectCharacterRecognizerPreferenceMemoryRepository : ObjectCharacterRecognizerPreferenceRepository {
    private val _detectWidth = MutableStateFlow(DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_WIDTH)
    private val _detectHeight = MutableStateFlow(DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_HEIGHT)
    private val _recognizeWidth = MutableStateFlow(DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_WIDTH)
    private val _recognizeHeight = MutableStateFlow(DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_HEIGHT)
    private val _cropWidth = MutableStateFlow(DEFAULT_OBJECT_CHARACTER_RECOGNIZER_CROP_WIDTH)
    private val _maxBatchSize = MutableStateFlow(DEFAULT_OBJECT_CHARACTER_RECOGNIZER_MAX_BATCH_SIZE)

    override val detectWidth: Flow<Int> get() = _detectWidth
    override val detectHeight: Flow<Int> get() = _detectHeight
    override val recognizeWidth: Flow<Int> get() = _recognizeWidth
    override val recognizeHeight: Flow<Int> get() = _recognizeHeight
    override val cropWidth: Flow<Int> get() = _cropWidth
    override val maxBatchSize: Flow<Int> get() = _maxBatchSize

    override suspend fun setDetectWidth(value: Int) {
        _detectWidth.value = value
    }

    override suspend fun setDetectHeight(value: Int) {
        _detectHeight.value = value
    }

    override suspend fun setRecognizeWidth(value: Int) {
        _recognizeWidth.value = value
    }

    override suspend fun setRecognizeHeight(value: Int) {
        _recognizeHeight.value = value
    }

    override suspend fun setCropWidth(value: Int) {
        _cropWidth.value = value
    }

    override suspend fun setMaxBatchSize(value: Int) {
        _maxBatchSize.value = value
    }
}
