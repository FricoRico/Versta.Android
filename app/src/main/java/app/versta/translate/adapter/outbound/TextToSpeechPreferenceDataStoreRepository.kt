package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.versta.translate.core.entity.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TextToSpeechPreferenceDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : TextToSpeechPreferenceRepository {

    /**
     * Gets the speed of the speech.
     */
    override fun getSpeed(): Flow<Float> {
        return dataStore.data.map { preferences ->
            preferences[SPEED_KEY]?.toFloat() ?: DEFAULT_SPEED
        }
    }

    /**
     * Sets the speed of the speech.
     * @param speed The speed of the speech.
     */
    override suspend fun setSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[SPEED_KEY] = speed.toString()
        }
    }

    /**
     * Gets the preferred gender of the voice.
     */
    override fun getGender(): Flow<VoiceGender> {
        return dataStore.data.map { preferences ->
            enumValues<VoiceGender>().find {
                it.name.equals(
                    preferences[VOICE_GENDER_KEY],
                    ignoreCase = true
                )
            } ?: DEFAULT_VOICE_GENDER
        }
    }

    /**
     * Sets the preferred voice gender for synthesis.
     * @param gender The preferred voice gender.
     */
    override suspend fun setGender(gender: VoiceGender) {
        dataStore.edit { preferences ->
            preferences[VOICE_GENDER_KEY] = gender.toString()
        }
    }

    /**
     * Gets the thread count.
     */
    override fun getThreadCount(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[THREAD_COUNT_KEY]?.toInt() ?: Runtime.getRuntime().availableProcessors()
        }
    }

    /**
     * Sets the thread count.
     * @param count The count of threads.
     */
    override suspend fun setThreadCount(count: Int) {
        dataStore.edit { preferences ->
            preferences[THREAD_COUNT_KEY] = count.toString()
        }
    }

    companion object {
        private val SPEED_KEY = stringPreferencesKey("speech_speed")
        private val VOICE_GENDER_KEY = stringPreferencesKey("speech_voice_gender")
        private val THREAD_COUNT_KEY = stringPreferencesKey("speech_thread_count")
    }
}