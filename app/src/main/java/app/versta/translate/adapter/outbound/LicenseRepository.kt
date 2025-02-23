package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow

interface LicenseRepository {
    /**
     * Checks if the user has bought a license.
     */
    fun hasLicense(): Flow<Boolean>

    /**
     * Sets the license status.
     */
    suspend fun setLicense(license: Boolean)
}