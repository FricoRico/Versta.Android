package app.versta.translate.core.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.DOWNLOAD_LANGUAGE_STATUS_INTENT
import app.versta.translate.adapter.inbound.DownloadLanguageWorker
import app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.bridge.utils.LanguageDetect
import app.versta.translate.core.entity.AUTO_DETECT_UNKNOWN_CODE
import app.versta.translate.core.entity.AutoDetectLanguage
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ExternalLanguageDownloadTask
import app.versta.translate.core.entity.ExternalLanguagePairDefinition
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageOption
import app.versta.translate.core.entity.LanguagePair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LanguageType {
    Source, Target
}

class LanguageViewModel(
    context: Context,
    private val languageRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
    private val externalLanguageModelsRepository: ExternalLanguageModelsRepository,
) : ViewModel() {
    val pivotTranslationEnabled =
        languagePreferenceRepository.getPivotTranslation().distinctUntilChanged()

    private val _languageDetector = LanguageDetect()

    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState.asStateFlow()

    private val _languageSuggestionState = MutableStateFlow(false)
    val languageSuggestionState: StateFlow<Boolean> = _languageSuggestionState.asStateFlow()

    private val _languageSuggestionOnCompleteCallback =
        MutableStateFlow<((ExternalLanguagePairDefinition) -> Unit)?>(null)
    val languageSuggestionOnCompleteCallback: StateFlow<((ExternalLanguagePairDefinition) -> Unit)?> =
        _languageSuggestionOnCompleteCallback.asStateFlow()

    private val _autoDetectInput = MutableStateFlow("")
    val autoDetectLanguage = MutableStateFlow<Language?>(null)

    private val downloadManager = DownloadManager<ExternalLanguageDownloadTask>(
        context = context,
        statusIntentAction = DOWNLOAD_LANGUAGE_STATUS_INTENT,
        workerClass = DownloadLanguageWorker::class.java
    )

    val downloadTasks: StateFlow<List<ExternalLanguageDownloadTask>> =
        downloadManager.downloadTasks.asStateFlow()

    private val _importedLanguages = languageRepository.getLanguages().distinctUntilChanged()
    val importedLanguagePairs = languageRepository.getLanguagePairs().distinctUntilChanged()

    val sourceLanguage = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()
    val targetLanguage = languagePreferenceRepository.getTargetLanguage().distinctUntilChanged()

    val languageModels = externalLanguageModelsRepository.getDefinitions().distinctUntilChanged()
    val languageModelsByState =
        externalLanguageModelsRepository.getDefinitionsByState(_importedLanguages)
            .distinctUntilChanged()

    val languageOptions = languagePreferenceRepository.getLanguagePair().distinctUntilChanged()
    val languagePair =
        combine(sourceLanguage, targetLanguage, autoDetectLanguage) { source, target, detected ->
            if (source == null || target == null) {
                return@combine null
            }

            when (source) {
                is AutoDetectLanguage -> {
                    if (detected == null) {
                        return@combine null
                    }

                    return@combine LanguagePair(
                        source = detected,
                        target = target
                    )
                }

                is Language -> {
                    return@combine LanguagePair(
                        source = source,
                        target = target
                    )
                }

                else -> {
                    return@combine null
                }
            }
        }.distinctUntilChanged()
    val languageModelFiles = combine(languagePair, pivotTranslationEnabled) { pair, pivot ->
        if (pair == null) {
            return@combine null
        }

        languageRepository.getLanguageModel(pair, pivot)
    }.distinctUntilChanged()

    val sourceLanguages = combine(importedLanguagePairs, targetLanguage) { pairs, target ->
        pairs.asSequence()
            .map { it.source }
            .distinctBy { it.isoCode }.plus(
                AutoDetectLanguage()
            )
            .sortedBy { language -> if (language is AutoDetectLanguage) 0 else 1 }.toList()
    }.distinctUntilChanged()

    val targetLanguages = combine(importedLanguagePairs, sourceLanguage) { pairs, source ->
        if (pivotTranslationEnabled.first()) {
            return@combine pairs.filter { it.target != source }
                .map { it.target }.distinctBy { it.isoCode }
        }

        if (source is Language) {
            return@combine languageRepository.getTargetLanguagesBySource(source)
        }

        pairs.map { it.target }
    }.distinctUntilChanged()

    val canSwapLanguages = combine(
        sourceLanguage, targetLanguage, importedLanguagePairs
    ) { source, target, pairs ->
        if (source == null || target == null) {
            return@combine false
        }

        if (pivotTranslationEnabled.first()) {
            return@combine pairs.any { it.source == target } && pairs.any { it.target == source }
        }

        pairs.any { it.source == target && it.target == source }
    }.distinctUntilChanged()

    /**
     * Returns a flow of [ExternalLanguagePairDefinition] that contains the definitions of the
     * external language model for the given [LanguagePair].
     */
    fun getLanguageDefinition(pair: LanguagePair): Flow<ExternalLanguagePairDefinition> {
        return externalLanguageModelsRepository.getDefinition(pair).distinctUntilChanged()
    }

    /**
     * Sets the language selection drawer state.
     */
    fun setLanguageSelectionState(state: LanguageType?) {
        _languageSelectionState.value = state
    }

    /**
     * Sets the language suggestion drawer state.
     */
    fun setLanguageSuggestionState(
        enabled: Boolean,
        onComplete: ((ExternalLanguagePairDefinition) -> Unit)? = null
    ) {
        _languageSuggestionState.value = enabled
        _languageSuggestionOnCompleteCallback.value = onComplete
    }

    suspend fun modelAvailable(): Boolean {
        return languageModelFiles.first() != null
    }

    /**
     * Sets the source language.
     */
    fun setSourceLanguage(language: LanguageOption): Job {
        return viewModelScope.launch {
            val currentSourceLanguage = sourceLanguage.first()
            val currentTargetLanguage = targetLanguage.first()
            val currentCanSwapLanguages = canSwapLanguages.first()

            languagePreferenceRepository.setSourceLanguage(language)


            // If there is a target language available for the current source language, set it instead
            // of clearing the target language.
            if (currentSourceLanguage is Language && currentCanSwapLanguages && currentTargetLanguage == language) {
                languagePreferenceRepository.setTargetLanguage(currentSourceLanguage)
                return@launch
            }

            if (language !is Language) {
                return@launch
            }

            // If the current target language is not available for the new source language, clear the
            // current target language.
            if (!currentCanSwapLanguages && currentTargetLanguage == language) {
                clearTargetLanguage()
            }
        }
    }

    /**
     * Sets the target language.
     */
    fun setTargetLanguage(language: Language): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.setTargetLanguage(language)
        }
    }

    /**
     * Swaps the source and target languages.
     */
    fun swapLanguages(): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.swapLanguages()
        }
    }

    /**
     * Clears the target language.
     */
    private fun clearTargetLanguage(): Job {
        return viewModelScope.launch {
            languagePreferenceRepository.clearTargetLanguage()
        }
    }

    /**
     * Clears the language selection if the language pair is the same as the current one.
     */
    fun removeLanguageModel(pair: LanguagePair, bidirectional: Boolean) {
        viewModelScope.launch {
            languageRepository.deleteLanguageModel(pair, bidirectional)
            languagePreferenceRepository.clearLanguageSelectionForPair(pair)
        }
    }

    /**
     * Sets the pivot translation option based on the given boolean value.
     */
    fun setPivotTranslationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            languagePreferenceRepository.setPivotTranslation(enabled)

            languagePreferenceRepository.clearSourceLanguage()
            languagePreferenceRepository.clearTargetLanguage()
        }
    }

    /**
     * Queues a download for the given language model.
     */
    fun queueDownload(
        model: ExternalLanguagePairDefinition,
        onComplete: (ExternalLanguagePairDefinition) -> Unit = {}
    ) {
        val task = ExternalLanguageDownloadTask(
            model = model,
            status = DownloadStatus.Queued,
            onComplete = onComplete
        )
        downloadManager.queueDownload(task)
    }

    /**
     * Cancels all pending downloads.
     */
    fun cancelDownload() {
        downloadManager.cancelDownload()
    }

    /**
     * Detect the language of the given text.
     */
    fun detectLanguage(text: String) {
        val result = _languageDetector.detectLanguage(text)

        if (result == null || !result.isReliable) {
            return
        }

        viewModelScope.launch {
            if (sourceLanguages.first()
                    .any { it.isoCode == result.language } || languageModels.first()
                    .any { it.pair.source.isoCode == result.language }
            ) {
                setAutoDetectLanguage(result.language)
            }
        }
    }

    /**
     * Set the detected language.
     */
    private fun setAutoDetectLanguage(isoCode: String) {
        if (isoCode == AUTO_DETECT_UNKNOWN_CODE) {
            autoDetectLanguage.value = null
            return
        }

        autoDetectLanguage.value = Language.fromIsoCode(isoCode)
    }

    @OptIn(FlowPreview::class)
    private fun autoDetectLanguage() {
        viewModelScope.launch(Dispatchers.Default) {
            _autoDetectInput.debounce(300).collect {
                detectLanguage(it)
            }
        }
    }

    /**
     * Set the input.
     */
    fun setAutoDetectInput(text: String) {
        _autoDetectInput.value = text
    }

    init {
        downloadManager.register()
        autoDetectLanguage()
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.unregister()
    }
}