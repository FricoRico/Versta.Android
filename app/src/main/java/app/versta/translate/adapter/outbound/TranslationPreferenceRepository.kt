package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow

internal const val DEFAULT_NUMBER_OF_BEAMS = 4
internal const val DEFAULT_MAX_SEQUENCE_LENGTH = 256
internal const val DEFAULT_MIN_PROBABILITY = 0.1f
internal const val DEFAULT_REPETITION_PENALTY = 0.15f
internal const val DEFAULT_CACHE_SIZE = 1024
internal const val DEFAULT_CACHE_ENABLED = true

interface TranslationPreferenceRepository {
    /**
     * Gets the translation cache size (number of remembered translations).
     */
    fun getCacheSize(): Flow<Int>

    /**
     * Sets the translation cache size (number of remembered translations).
     * @param size The number of translations to remember.
     */
    suspend fun setCacheSize(size: Int)

    /**
     * Gets whether the translation cache is enabled.
     */
    fun getCacheEnabled(): Flow<Boolean>

    /**
     * Sets whether the translation cache is enabled.
     * @param enabled The enabled state of the cache.
     */
    suspend fun setCacheEnabled(enabled: Boolean)

    /**
     * Gets the number of beams.
     */
    fun getNumberOfBeams(): Flow<Int>

    /**
     * Sets the number of beams.
     * @param beams The number of beams.
     */
    suspend fun setNumberOfBeams(beams: Int)

    /**
     * Gets the thread count.
     */
    fun getThreadCount(): Flow<Int>

    /**
     * Sets the thread count.
     * @param count The count of threads.
     */
    suspend fun setThreadCount(count: Int)

    /**
     * Gets the maximum sequence length.
     */
    fun getMaxSequenceLength(): Flow<Int>

    /**
     * Sets the maximum sequence length.
     * @param length The length of the sequence.
     */
    suspend fun setMaxSequenceLength(length: Int)

    /**
     * Gets the minimum probability.
     */
    fun getMinProbability(): Flow<Float>

    /**
     * Sets the minimum probability.
     * @param probability The probability.
     */
    suspend fun setMinProbability(probability: Float)

    /**
     * Gets the penalty for repeating tokens.
     */
    fun getRepetitionPenalty(): Flow<Float>

    /**
     * Sets the penalty for repeating tokens.
     * @param penalty the penalty.
     */
    suspend fun setRepetitionPenalty(penalty: Float)
}