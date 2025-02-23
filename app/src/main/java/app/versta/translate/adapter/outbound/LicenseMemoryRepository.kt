package app.versta.translate.adapter.outbound

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class LicenseMemoryRepository : LicenseRepository {
    private var license: Boolean = false

    /**
     * Checks if the user has bought a license.
     */
    override fun hasLicense(): Flow<Boolean> {
        return flowOf(license)
    }

    /**
     * Sets the license status.
     */
    override suspend fun setLicense(license: Boolean) {
        this.license = license
    }
}