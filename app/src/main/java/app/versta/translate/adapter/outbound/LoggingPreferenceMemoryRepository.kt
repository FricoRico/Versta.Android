package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class LoggingPreferenceMemoryRepository(
    initial: Boolean = DEFAULT_LOGGING_ENABLED,
) : LoggingPreferenceRepository {
    private val enabled = MutableStateFlow(initial)

    /**
     * Whether release builds write application logs to a file on the device.
     */
    override fun isLoggingEnabled(): Flow<Boolean> = enabled

    /**
     * Enables or disables file logging for release builds.
     */
    override suspend fun setLoggingEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}
