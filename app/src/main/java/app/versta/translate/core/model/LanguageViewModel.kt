package app.versta.translate.core.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.entity.BundleMetadata
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.ModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

interface ModelExtractor {
    /**
     * Extracts the contents of a compressed archive file from a given Uri into the app's local storage.
     * @param uri The Uri of the zip file to extract.
     * @param outputDir The local directory where the contents should be extracted.
     * @param extractToDirectory Whether to extract the contents to a directory with the same name as the compressed file.
     */
    fun extract(
        uri: Uri,
        outputDir: File,
        extractToDirectory: Boolean = true,
        listener: ModelExtractorProgressListener? = null
    ): File
}

interface ModelExtractorProgressListener {
    /**
     * Callback for progress updates during extraction.
     * @param file The file currently being extracted.
     * @param extracted The number of files that have been extracted so far.
     * @param total The total number of files in the archive.
     */
    fun onProgressUpdate(file: File, extracted: Int, total: Int)
}


interface ModelFilePicker {
    /**
     * Opens a file picker to select a file.
     * @param listener The callback to be invoked when a file is picked.
     */
    fun openFilePicker(listener: ModelFilePickerCallback, fileTypes: Array<String> = arrayOf("*/*"))
}

interface ModelFilePickerCallback {
    /**
     * Callback for when a file has been picked.
     * @param uri The Uri of the picked file.
     */
    fun onFilePicked(uri: Uri)
}

sealed class ExtractionProgress {
    data object Idle : ExtractionProgress()
    data object Started : ExtractionProgress()

    data class InProgress(val current: String, val extracted: Int, val total: Int) :
        ExtractionProgress()

    data class Completed(val metadata: ModelMetadata) : ExtractionProgress()
    data class Error(val exception: Exception) : ExtractionProgress()
}

enum class LanguageType {
    Source,
    Target
}

@OptIn(ExperimentalCoroutinesApi::class)
class LanguageViewModel(
    private val modelExtractor: ModelExtractor,
    private val languageDatabaseRepository: LanguageRepository,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState

    private val _progressState = MutableStateFlow<ExtractionProgress>(ExtractionProgress.Idle)
    val progressState: StateFlow<ExtractionProgress> = _progressState.asStateFlow()

    val sourceLanguage = languagePreferenceRepository.getSourceLanguage()
    val targetLanguage = languagePreferenceRepository.getTargetLanguage()

    val canSwapLanguages = sourceLanguage.combine(targetLanguage) { source, target ->
        source != null && target != null
    }

    val availableLanguages = languageDatabaseRepository.getLanguages()
    val sourceLanguages = languageDatabaseRepository.getSourceLanguages()
    val targetLanguages = sourceLanguage
        .flatMapLatest {
            if (it != null) {
                languageDatabaseRepository.getTargetLanguagesBySource(it)
            } else {
                flowOf(emptyList())
            }
        }

    /**
     * Sets the language selection state.
     */
    fun setLanguageSelectionState(state: LanguageType?) {
        _languageSelectionState.value = state
    }

    /**
     * Sets the source language.
     */
    fun setSourceLanguage(language: Language): Job {
        return viewModelScope.launch {
            val current = sourceLanguage.first()
            languagePreferenceRepository.setSourceLanguage(language)

            // If there is a target language available for the current source language, set it instead
            // of clearing the target language.
            if (current != null && targetLanguages.first().contains(current)) {
                languagePreferenceRepository.setTargetLanguage(current)
                return@launch
            }

            // If the current target language is not available for the new source language, clear the
            // current target language.
            languageDatabaseRepository.getTargetLanguagesBySource(language)
                .collectLatest { languages ->
                    if (languages.none { it == targetLanguage.first() }) {
                        clearTargetLanguage()
                    }
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
     * Listener for extraction progress updates.
     */
    private val importListener: ModelExtractorProgressListener =
        object : ModelExtractorProgressListener {
            override fun onProgressUpdate(file: File, extracted: Int, total: Int) {
                _progressState.update {
                    ExtractionProgress.InProgress(
                        current = file.name,
                        extracted = extracted,
                        total = total
                    )
                }
            }
        }

    /**
     * Imports a model from the given Uri.
     */
    fun import(uri: Uri, outputDir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _progressState.value = ExtractionProgress.Started

            var output: File? = null

            try {
                output = modelExtractor.extract(
                    uri = uri,
                    outputDir = outputDir,
                    listener = importListener
                )

                val metadata = readMetadata(output)

                languageDatabaseRepository.upsertLanguageModels(metadata)
                _progressState.value = ExtractionProgress.Completed(metadata)
            } catch (e: Exception) {
                output?.deleteRecursively()
                _progressState.value = ExtractionProgress.Error(e)
            }
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File): ModelMetadata {
        val bundleMetadataFile = File(output, "metadata.json")
        val bundleMetadata = Json.decodeFromString<BundleMetadata>(bundleMetadataFile.readText())

        // TODO: Improve error handling
        if (!bundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val languageMetadata = bundleMetadata.metadata.map {
            val languageMetadataFile = File(output.resolve(it.directory), "metadata.json")
            Json.decodeFromString<LanguageMetadata>(languageMetadataFile.readText()).setRootPath(
                path = output.toPath()
            )
        }

        // TODO: Improve error handling
        if (languageMetadata.any { !it.isValid() }) {
            throw Exception("Invalid language metadata file")
        }

        return ModelMetadata(
            bundleMetadata = bundleMetadata,
            languageMetadata = languageMetadata
        )
    }
}