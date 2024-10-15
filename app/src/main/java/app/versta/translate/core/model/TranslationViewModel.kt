package app.versta.translate.core.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.service.TranslatorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

sealed class LoadingProgress {
    data object Idle : LoadingProgress()

    data object InProgress : LoadingProgress()

    data object Completed : LoadingProgress()
    data class Error(val exception: Exception) : LoadingProgress()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationViewModel(
    val translatorService: TranslatorService,
    private val languageDatabaseRepository: LanguageDatabaseRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: StateFlow<LoadingProgress> = _loadingProgress.asStateFlow()

    private val languages = languagePreferenceRepository.languagePair

    private val loadModelFlow = languages.filterNotNull().mapLatest { data ->
        languageDatabaseRepository.getLanguageModel(data).first()
    }

    fun load(files: LanguageModelFiles, languages: LanguagePair) {
        viewModelScope.launch(Dispatchers.IO) {
            _loadingProgress.value = LoadingProgress.InProgress

            try {
                translatorService.load(files, languages)
                _loadingProgress.value = LoadingProgress.Completed
            } catch (e: Exception) {
                _loadingProgress.value = LoadingProgress.Error(e)
            }
        }
    }

    init {
        viewModelScope.launch {
            loadModelFlow.collect {
                val pair = languages.first()

                if (it != null && pair != null) {
                    load(it, pair)
                }
            }
        }
    }
}