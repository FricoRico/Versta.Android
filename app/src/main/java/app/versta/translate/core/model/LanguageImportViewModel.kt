package app.versta.translate.core.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.ExtractionProgressListener
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageAnalysisProgress
import app.versta.translate.core.entity.LanguageImportProgress
import app.versta.translate.core.entity.LanguageModelMetadata
import app.versta.translate.core.entity.LanguageModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class LanguageImportViewModel(
    private val modelExtractor: CompressedFileExtractor,
    private val languageRepository: LanguageRepository,
) : ViewModel() {
    private val _serializer = Json { ignoreUnknownKeys = true }

    private val _importProgressState =
        MutableStateFlow<LanguageImportProgress>(LanguageImportProgress.Idle)
    val importProgressState: StateFlow<LanguageImportProgress> = _importProgressState.asStateFlow()

    private val _analysisProgressState =
        MutableStateFlow<LanguageAnalysisProgress>(LanguageAnalysisProgress.Idle)
    val analysisProgressState: StateFlow<LanguageAnalysisProgress> =
        _analysisProgressState.asStateFlow()

    /**
     * Listener for extraction progress updates.
     */
    private val importListener: ExtractionProgressListener =
        object : ExtractionProgressListener {
            override fun onProgressUpdate(file: File, extracted: Int, total: Int) {
                _importProgressState.update {
                    LanguageImportProgress.InProgress(
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
            _importProgressState.value = LanguageImportProgress.Started

            var output: File? = null

            try {
                output = modelExtractor.extract(
                    uri = uri,
                    outputDir = outputDir,
                    listener = importListener
                )

                val metadata = readMetadata(output)

                languageRepository.upsertLanguageModels(metadata)
                _importProgressState.value = LanguageImportProgress.Completed(metadata)
            } catch (e: Exception) {
                output?.deleteRecursively()
                _importProgressState.value = LanguageImportProgress.Error(e)
                Timber.tag(TAG).e(e)
            }
        }
    }

    fun analyze(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _analysisProgressState.value = LanguageAnalysisProgress.InProgress

            try {
                val output = modelExtractor.openFile(uri, "metadata.json")
                    ?: throw Exception("Metadata file not found")
                val languageBundleMetadata = _serializer.decodeFromString<LanguageBundleMetadata>(output.readText())

                if (!languageBundleMetadata.isValid()) {
                    throw Exception("Invalid metadata file")
                }

                _analysisProgressState.value =
                    LanguageAnalysisProgress.Completed(languageBundleMetadata, uri)
            } catch (e: Exception) {
                _analysisProgressState.value = LanguageAnalysisProgress.Error(e)
                Timber.tag(TAG).e(e)
            }
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File?): LanguageModel {
        if (output == null) {
            throw Exception("Output file is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val languageBundleMetadata =
            _serializer.decodeFromString<LanguageBundleMetadata>(bundleMetadataFile.readText())

        if (!languageBundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val languageModelMetadata = languageBundleMetadata.metadata.map {
            val languageMetadataFile = File(output.resolve(it.directory), "metadata.json")

            _serializer.decodeFromString<LanguageModelMetadata>(languageMetadataFile.readText())
                .setRootPath(
                    path = output.resolve(it.directory).toPath()
                )
        }

        if (languageModelMetadata.any { !it.isValid() }) {
            throw Exception("Invalid language metadata file")
        }

        return LanguageModel(
            bundle = languageBundleMetadata,
            languages = languageModelMetadata
        )
    }

    companion object {
        private val TAG: String = LanguageImportViewModel::class.java.simpleName
    }
}