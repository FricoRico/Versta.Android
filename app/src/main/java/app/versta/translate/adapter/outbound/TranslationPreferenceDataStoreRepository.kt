package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TranslationPreferenceDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : TranslationPreferenceRepository {
    /**
     * Gets the cache size.
     */
    override fun getCacheSize(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[CACHE_SIZE_KEY]?.toInt() ?: DEFAULT_CACHE_SIZE
        }
    }

    /**
     * Sets the cache size.
     * @param size The size of the cache.
     */
    override suspend fun setCacheSize(size: Int) {
        dataStore.edit { preferences ->
            preferences[CACHE_SIZE_KEY] = size.toString()
        }
    }

    /**
     * Gets the cache enabled status.
     */
    override fun getCacheEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[CACHE_ENABLED_KEY]?.toBoolean() ?: DEFAULT_CACHE_ENABLED
        }
    }

    /**
     * Sets the cache enabled status.
     * @param enabled The enabled status of the cache.
     */
    override suspend fun setCacheEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[CACHE_ENABLED_KEY] = enabled.toString()
        }
    }

    /**
     * Gets the number of beams.
     */
    override fun getNumberOfBeams(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[NUMBER_OF_BEAMS_KEY]?.toInt() ?: DEFAULT_NUMBER_OF_BEAMS
        }
    }

    /**
     * Sets the number of beams.
     * @param beams The number of beams.
     */
    override suspend fun setNumberOfBeams(beams: Int) {
        dataStore.edit { preferences ->
            preferences[NUMBER_OF_BEAMS_KEY] = beams.toString()
        }
    }

    /**
     * Gets the thread count.
     */
    override fun getThreadCount(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[THREAD_COUNT_KEY]?.toInt() ?: (Runtime.getRuntime().availableProcessors() / 2)
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

    /**
     * Gets the maximum sequence length.
     */
    override fun getMaxSequenceLength(): Flow<Int> {
        return dataStore.data.map { preferences ->
            preferences[MAX_SEQUENCE_LENGTH_KEY]?.toInt() ?: DEFAULT_MAX_SEQUENCE_LENGTH
        }
    }

    /**
     * Sets the maximum sequence length.
     * @param length The length of the sequence.
     */
    override suspend fun setMaxSequenceLength(length: Int) {
        dataStore.edit { preferences ->
            preferences[MAX_SEQUENCE_LENGTH_KEY] = length.toString()
        }
    }

    /**
     * Gets the minimum probability.
     */
    override fun getMinProbability(): Flow<Float> {
        return dataStore.data.map { preferences ->
            preferences[MIN_PROBABILITY_KEY]?.toFloat() ?: DEFAULT_MIN_PROBABILITY
        }
    }

    /**
     * Sets the minimum probability.
     * @param probability The probability.
     */
    override suspend fun setMinProbability(probability: Float) {
        dataStore.edit { preferences ->
            preferences[MIN_PROBABILITY_KEY] = probability.toString()
        }
    }

    /**
     * Gets the penalty for repeating tokens.
     */
    override fun getRepetitionPenalty(): Flow<Float> {
        return dataStore.data.map { preferences ->
            preferences[REPETITION_PENALTY_KEY]?.toFloat() ?: DEFAULT_REPETITION_PENALTY
        }
    }

    /**
     * Sets the penalty for repeating tokens.
     * @param penalty the penalty.
     */
    override suspend fun setRepetitionPenalty(penalty: Float) {
        dataStore.edit { preferences ->
            preferences[REPETITION_PENALTY_KEY] = penalty.toString()
        }
    }

    companion object {
        private val CACHE_SIZE_KEY = stringPreferencesKey("cache_size")
        private val CACHE_ENABLED_KEY = stringPreferencesKey("cache_enabled")
        private val NUMBER_OF_BEAMS_KEY = stringPreferencesKey("number_of_beams")
        private val THREAD_COUNT_KEY = stringPreferencesKey("thread_count")
        private val MAX_SEQUENCE_LENGTH_KEY = stringPreferencesKey("max_sequence_length")
        private val MIN_PROBABILITY_KEY = stringPreferencesKey("min_probability")
        private val REPETITION_PENALTY_KEY = stringPreferencesKey("repetition_penalty")
    }
}