package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class LicenseViewModel : ViewModel() {
    private val _isTrialLicense = MutableStateFlow(true)
    val isTrialLicense = _isTrialLicense
}