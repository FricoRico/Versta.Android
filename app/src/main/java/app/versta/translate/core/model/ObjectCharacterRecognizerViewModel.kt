package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.ObjectCharacterRecognizerPreferenceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class ObjectCharacterRecognizerViewModel(
    private val preferenceRepository: ObjectCharacterRecognizerPreferenceRepository
) : ViewModel() {
    val detectWidth = preferenceRepository.detectWidth.distinctUntilChanged()
    val detectHeight = preferenceRepository.detectHeight.distinctUntilChanged()
    val recognizeWidth = preferenceRepository.recognizeWidth.distinctUntilChanged()
    val recognizeHeight = preferenceRepository.recognizeHeight.distinctUntilChanged()
    val cropWidth = preferenceRepository.cropWidth.distinctUntilChanged()
    val maxBatchSize = preferenceRepository.maxBatchSize.distinctUntilChanged()

    fun setDetectWidth(value: Int): Job {
        return viewModelScope.launch {
            preferenceRepository.setDetectWidth(value)
        }
    }

    fun setDetectHeight(value: Int): Job {
        return viewModelScope.launch {
            preferenceRepository.setDetectHeight(value)
        }
    }

    fun setRecognizeWidth(value: Int): Job {
        return viewModelScope.launch {
            preferenceRepository.setRecognizeWidth(value)
        }
    }

    fun setRecognizeHeight(value: Int): Job {
        return viewModelScope.launch {
            preferenceRepository.setRecognizeHeight(value)
        }
    }

    fun setCropWidth(value: Int): Job {
        return viewModelScope.launch {
            preferenceRepository.setCropWidth(value)
        }
    }

    fun setMaxBatchSize(value: Int): Job {
        return viewModelScope.launch {
            preferenceRepository.setMaxBatchSize(value)
        }
    }
}
