package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow

internal const val DEFAULT_LOGGING_ENABLED = false

interface LoggingPreferenceRepository {
    /**
     * Whether release builds write application logs to a file on the device.
     * Debug builds always log to a file regardless of this setting.
     */
    fun isLoggingEnabled(): Flow<Boolean>

    /**
     * Enables or disables file logging for release builds.
     */
    suspend fun setLoggingEnabled(enabled: Boolean)
}
