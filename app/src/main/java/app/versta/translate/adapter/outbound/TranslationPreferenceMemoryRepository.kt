package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class TranslationPreferenceMemoryRepository : TranslationPreferenceRepository {
    private var cacheSize = DEFAULT_CACHE_SIZE
    private var cacheEnabled = DEFAULT_CACHE_ENABLED
    private var threadCount = Runtime.getRuntime().availableProcessors() / 2
    private var numberOfBeams = DEFAULT_NUMBER_OF_BEAMS
    private var maxSequenceLength = DEFAULT_MAX_SEQUENCE_LENGTH
    private var minProbability = DEFAULT_MIN_PROBABILITY
    private var repetitionPenalty = DEFAULT_REPETITION_PENALTY

    /**
     * Gets the cache size.
     */
    override fun getCacheSize(): Flow<Int> = flowOf(cacheSize)

    /**
     * Sets the cache size.
     * @param size The size of the cache.
     */
    override suspend fun setCacheSize(size: Int) {
        cacheSize = size
    }

    /**
     * Gets the cache enabled status.
     */
    override fun getCacheEnabled(): Flow<Boolean> = flowOf(cacheEnabled)

    /**
     * Sets the cache enabled status.
     * @param enabled The enabled status of the cache.
     */
    override suspend fun setCacheEnabled(enabled: Boolean) {
        cacheEnabled = enabled
    }

    /**
     * Gets the number of beams.
     */
    override fun getNumberOfBeams(): Flow<Int> = flowOf(numberOfBeams)

    /**
     * Sets the number of beams.
     * @param beams The number of beams.
     */
    override suspend fun setNumberOfBeams(beams: Int) {
        numberOfBeams = beams
    }

    /**
     * Gets the thread count.
     */
    override fun getThreadCount(): Flow<Int> = flowOf(threadCount)

    /**
     * Sets the thread count.
     * @param count The count of threads.
     */
    override suspend fun setThreadCount(count: Int) {
        threadCount = count
    }

    /**
     * Gets the maximum sequence length.
     */
    override fun getMaxSequenceLength(): Flow<Int> = flowOf(maxSequenceLength)

    /**
     * Sets the maximum sequence length.
     * @param length The length of the sequence.
     */
    override suspend fun setMaxSequenceLength(length: Int) {
        maxSequenceLength = length
    }

    /**
     * Gets the minimum probability.
     */
    override fun getMinProbability(): Flow<Float> = flowOf(minProbability)

    /**
     * Sets the minimum probability.
     * @param probability The probability.
     */
    override suspend fun setMinProbability(probability: Float) {
        minProbability = probability
    }

    /**
     * Gets the penalty for repeating tokens.
     */
    override fun getRepetitionPenalty(): Flow<Float> {
        return flowOf(repetitionPenalty)
    }

    /**
     * Sets the penalty for repeating tokens.
     * @param penalty the penalty.
     */
    override suspend fun setRepetitionPenalty(penalty: Float) {
        repetitionPenalty = penalty
    }
}