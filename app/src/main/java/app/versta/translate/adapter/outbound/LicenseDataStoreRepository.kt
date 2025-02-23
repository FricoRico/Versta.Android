package app.versta.translate.adapter.outbound

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LicenseDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) : LicenseRepository {
    /**
     * Checks if the user has bought a license.
     */
    override fun hasLicense(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[LICENSE_KEY] ?: false
        }
    }

    /**
     * Sets the license status.
     */
    override suspend fun setLicense(license: Boolean) {
        dataStore.edit { preferences ->
            preferences[LICENSE_KEY] = license
        }
    }

    companion object {
        val LICENSE_KEY = booleanPreferencesKey("license")
    }
}