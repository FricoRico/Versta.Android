package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LoggingPreferenceDataStoreRepository(
    private val dataStore: DataStore<Preferences>,
) : LoggingPreferenceRepository {
    override fun isLoggingEnabled(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[LOGGING_ENABLED_KEY] ?: DEFAULT_LOGGING_ENABLED
        }

    override suspend fun setLoggingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[LOGGING_ENABLED_KEY] = enabled
        }
    }

    companion object {
        private val LOGGING_ENABLED_KEY = booleanPreferencesKey("logging_enabled")
    }
}
