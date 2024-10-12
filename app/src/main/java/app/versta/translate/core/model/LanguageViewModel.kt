package app.versta.translate.core.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.core.entity.BundleMetadata
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.ModelMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

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

class LanguageViewModel(
    private val modelExtractor: ModelExtractor,
    private val languageDatabaseRepository: LanguageDatabaseRepository
) : ViewModel() {
    private val _languageSelectionState = MutableStateFlow<LanguageType?>(null)
    val languageSelectionState: StateFlow<LanguageType?> = _languageSelectionState

    private val _progressState = MutableStateFlow<ExtractionProgress>(ExtractionProgress.Idle)
    val progressState: StateFlow<ExtractionProgress> = _progressState.asStateFlow()

    val availableLanguages = languageDatabaseRepository.getLanguages()
    val sourceLanguages = languageDatabaseRepository.getSourceLanguages()

    fun getTargetLanguagesForSource(sourceLanguage: Locale) {
        val availableSourceLanguages =
            languageDatabaseRepository.getLanguageBySource(sourceLanguage)
    }

    fun setLanguageSelectionState(state: LanguageType?) {
        _languageSelectionState.value = state
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

        if (!bundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val languageMetadata = bundleMetadata.metadata.map {
            val languageMetadataFile = File(output.resolve(it.directory), "metadata.json")
            Json.decodeFromString<LanguageMetadata>(languageMetadataFile.readText()).setRootPath(
                path = output.toPath()
            )
        }

        if (languageMetadata.any { !it.isValid() }) {
            throw Exception("Invalid language metadata file")
        }

        return ModelMetadata(
            bundleMetadata = bundleMetadata,
            languageMetadata = languageMetadata
        )
    }
}