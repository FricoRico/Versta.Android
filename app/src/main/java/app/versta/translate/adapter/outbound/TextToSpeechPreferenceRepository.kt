package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceGender
import kotlinx.coroutines.flow.Flow

internal const val DEFAULT_SPEED = 1f
internal val DEFAULT_VOICE_GENDER = VoiceGender.Female

interface TextToSpeechPreferenceRepository {
    /**
     * Gets the speed of the speech.
     */
    fun getSpeed(): Flow<Float>

    /**
     * Sets the speed of the speech.
     * @param speed The speed of the speech.
     */
    suspend fun setSpeed(speed: Float)

    /**
     * Gets the preferred gender of the voice.
     */
    fun getGender(): Flow<VoiceGender>

    /**
     * Sets the preferred voice gender for synthesis.
     * @param gender The preferred voice gender.
     */
    suspend fun setGender(gender: VoiceGender)

    /**
     * Gets the thread count.
     */
    fun getThreadCount(): Flow<Int>

    /**
     * Sets the thread count.
     * @param count The count of threads.
     */
    suspend fun setThreadCount(count: Int)
}