package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObjectCharacterRecognizerPreferenceDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : ObjectCharacterRecognizerPreferenceRepository {
    override val detectWidth: Flow<Int>
        get() = dataStore.data.map { preferences ->
            preferences[DETECT_WIDTH_KEY]?.toInt() ?: DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_WIDTH
        }

    override val detectHeight: Flow<Int>
        get() = dataStore.data.map { preferences ->
            preferences[DETECT_HEIGHT_KEY]?.toInt() ?: DEFAULT_OBJECT_CHARACTER_RECOGNIZER_DETECT_HEIGHT
        }

    override val recognizeWidth: Flow<Int>
        get() = dataStore.data.map { preferences ->
            preferences[RECOGNIZE_WIDTH_KEY]?.toInt() ?: DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_WIDTH
        }

    override val recognizeHeight: Flow<Int>
        get() = dataStore.data.map { preferences ->
            preferences[RECOGNIZE_HEIGHT_KEY]?.toInt() ?: DEFAULT_OBJECT_CHARACTER_RECOGNIZER_RECOGNIZE_HEIGHT
        }

    override val cropWidth: Flow<Int>
        get() = dataStore.data.map { preferences ->
            preferences[CROP_WIDTH_KEY]?.toInt() ?: DEFAULT_OBJECT_CHARACTER_RECOGNIZER_CROP_WIDTH
        }

    override val maxBatchSize: Flow<Int>
        get() = dataStore.data.map { preferences ->
            preferences[MAX_BATCH_SIZE_KEY]?.toInt() ?: DEFAULT_OBJECT_CHARACTER_RECOGNIZER_MAX_BATCH_SIZE
        }

    override suspend fun setDetectWidth(value: Int) {
        dataStore.edit { preferences ->
            preferences[DETECT_WIDTH_KEY] = value.toString()
        }
    }

    override suspend fun setDetectHeight(value: Int) {
        dataStore.edit { preferences ->
            preferences[DETECT_HEIGHT_KEY] = value.toString()
        }
    }

    override suspend fun setRecognizeWidth(value: Int) {
        dataStore.edit { preferences ->
            preferences[RECOGNIZE_WIDTH_KEY] = value.toString()
        }
    }

    override suspend fun setRecognizeHeight(value: Int) {
        dataStore.edit { preferences ->
            preferences[RECOGNIZE_HEIGHT_KEY] = value.toString()
        }
    }

    override suspend fun setCropWidth(value: Int) {
        dataStore.edit { preferences ->
            preferences[CROP_WIDTH_KEY] = value.toString()
        }
    }

    override suspend fun setMaxBatchSize(value: Int) {
        dataStore.edit { preferences ->
            preferences[MAX_BATCH_SIZE_KEY] = value.toString()
        }
    }

    companion object {
        private val DETECT_WIDTH_KEY = stringPreferencesKey("object_character_recognizer_detect_width")
        private val DETECT_HEIGHT_KEY = stringPreferencesKey("object_character_recognizer_detect_height")
        private val RECOGNIZE_WIDTH_KEY = stringPreferencesKey("object_character_recognizer_recognize_width")
        private val RECOGNIZE_HEIGHT_KEY = stringPreferencesKey("object_character_recognizer_recognize_height")
        private val CROP_WIDTH_KEY = stringPreferencesKey("object_character_recognizer_crop_width")
        private val MAX_BATCH_SIZE_KEY = stringPreferencesKey("object_character_recognizer_max_batch_size")
    }
}
