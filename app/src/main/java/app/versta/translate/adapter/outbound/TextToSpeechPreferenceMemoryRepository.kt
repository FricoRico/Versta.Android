package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceGender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class TextToSpeechPreferenceMemoryRepository : TextToSpeechPreferenceRepository {
    private var _enabled = false
    private var _speed = DEFAULT_SPEED
    private var _gender = DEFAULT_VOICE_GENDER
    private var _threadCount = Runtime.getRuntime().availableProcessors()

    /**
     * Gets whether text-to-speech is enabled.
     */
    override fun getTextToSpeechEnabled(): Flow<Boolean> {
        return flowOf(_enabled)
    }

    /**
     * Sets whether text-to-speech is enabled.
     * @param enabled True if text-to-speech is enabled, false otherwise.
     */
    override suspend fun setTextToSpeechEnabled(enabled: Boolean) {
        _enabled = enabled
    }

    /**
     * Gets the speed of the speech.
     */
    override fun getSpeed(): Flow<Float> {
        return flowOf(_speed)
    }

    /**
     * Sets the speed of the speech.
     * @param speed The speed of the speech.
     */
    override suspend fun setSpeed(speed: Float) {
        _speed = speed
    }

    /**
     * Gets the preferred gender of the voice.
     */
    override fun getGender(): Flow<VoiceGender> {
        return flowOf(_gender)
    }

    /**
     * Sets the preferred voice gender for synthesis.
     * @param gender The preferred voice gender.
     */
    override suspend fun setGender(gender: VoiceGender) {
        _gender = gender
    }

    /**
     * Gets the thread count.
     */
    override fun getThreadCount(): Flow<Int> {
        return flowOf(_threadCount)
    }

    /**
     * Sets the thread count.
     * @param count The count of threads.
     */
    override suspend fun setThreadCount(count: Int) {
        _threadCount = count
    }
}