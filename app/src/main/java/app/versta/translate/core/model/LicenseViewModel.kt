package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LicenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class DialogState {
    Open,
    Closed,
    Confirm
}

class LicenseViewModel(
    private val licenseRepository: LicenseRepository
) : ViewModel() {
    private val _licenseDialogState = MutableStateFlow(DialogState.Closed)
    val licenseDialogState: StateFlow<DialogState> = _licenseDialogState

    val hasLicense = licenseRepository.hasLicense().distinctUntilChanged()

    /**
     * Sets the license status.
     */
    fun setLicense(license: Boolean) {
        viewModelScope.launch {
            licenseRepository.setLicense(license)
        }
    }

    /**
     * Shows the license dialog.
     */
    fun setLicenseDialogState(state: DialogState) {
        _licenseDialogState.value = state
    }
}