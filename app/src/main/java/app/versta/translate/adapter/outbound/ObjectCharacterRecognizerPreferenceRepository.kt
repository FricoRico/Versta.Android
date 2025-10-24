package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow

const val DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_WIDTH = 640
const val DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_HEIGHT = 640
const val DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_WIDTH = 960
const val DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_HEIGHT = 960
const val DEFAULT_OBJECT_CHARACTER_RECOGNIZER_CROP_WIDTH = 640
const val DEFAULT_OBJECT_CHARACTER_RECOGNIZER_MAX_BATCH_SIZE = 24

interface ObjectCharacterRecognizerPreferenceRepository {
    val detectWidth: Flow<Int>
    val detectHeight: Flow<Int>
    val recognizeWidth: Flow<Int>
    val recognizeHeight: Flow<Int>
    val cropWidth: Flow<Int>
    val maxBatchSize: Flow<Int>

    suspend fun setDetectWidth(value: Int)
    suspend fun setDetectHeight(value: Int)
    suspend fun setRecognizeWidth(value: Int)
    suspend fun setRecognizeHeight(value: Int)
    suspend fun setCropWidth(value: Int)
    suspend fun setMaxBatchSize(value: Int)
}
