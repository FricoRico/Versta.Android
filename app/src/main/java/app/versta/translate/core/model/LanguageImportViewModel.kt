package app.versta.translate.core.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.ExtractionProgressListener
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.entity.BundleMetadata
import app.versta.translate.core.entity.LanguageAnalysisProgress
import app.versta.translate.core.entity.LanguageImportProgress
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
            }
        }
    }

    fun analyze(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _analysisProgressState.value = LanguageAnalysisProgress.InProgress

            try {
                val output = modelExtractor.openFile(uri, "metadata.json")
                    ?: throw Exception("Metadata file not found")
                val bundleMetadata = _serializer.decodeFromString<BundleMetadata>(output.readText())

                // TODO: Improve error handling
                if (!bundleMetadata.isValid()) {
                    throw Exception("Invalid metadata file")
                }

                _analysisProgressState.value =
                    LanguageAnalysisProgress.Completed(bundleMetadata, uri)
            } catch (e: Exception) {
                _analysisProgressState.value = LanguageAnalysisProgress.Error(e)
            }
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File): ModelMetadata {
        val bundleMetadataFile = File(output, "metadata.json")
        val bundleMetadata =
            _serializer.decodeFromString<BundleMetadata>(bundleMetadataFile.readText())

        // TODO: Improve error handling
        if (!bundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val languageMetadata = bundleMetadata.metadata.map {
            val languageMetadataFile = File(output.resolve(it.directory), "metadata.json")

            _serializer.decodeFromString<LanguageMetadata>(languageMetadataFile.readText())
                .setRootPath(
                    path = output.resolve(it.directory).toPath()
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