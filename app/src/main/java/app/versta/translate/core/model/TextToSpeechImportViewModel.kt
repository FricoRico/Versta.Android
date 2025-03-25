package app.versta.translate.core.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.ExtractionProgressListener
import app.versta.translate.adapter.outbound.TextToSpeechRepository
import app.versta.translate.core.entity.TextToSpeechAnalysisProgress
import app.versta.translate.core.entity.TextToSpeechBundleMetadata
import app.versta.translate.core.entity.TextToSpeechImportProgress
import app.versta.translate.core.entity.TextToSpeechModelMetadata
import app.versta.translate.core.entity.TextToSpeechModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File

class TextToSpeechImportViewModel(
    private val modelExtractor: CompressedFileExtractor,
    private val textToSpeechRepository: TextToSpeechRepository,
) : ViewModel() {
    private val _serializer = Json { ignoreUnknownKeys = true }

    private val _importProgressState =
        MutableStateFlow<TextToSpeechImportProgress>(TextToSpeechImportProgress.Idle)
    val importProgressState: StateFlow<TextToSpeechImportProgress> =
        _importProgressState.asStateFlow()

    private val _analysisProgressState =
        MutableStateFlow<TextToSpeechAnalysisProgress>(TextToSpeechAnalysisProgress.Idle)
    val analysisProgressState: StateFlow<TextToSpeechAnalysisProgress> =
        _analysisProgressState.asStateFlow()

    /**
     * Listener for extraction progress updates.
     */
    private val importListener: ExtractionProgressListener =
        object : ExtractionProgressListener {
            override fun onProgressUpdate(file: File, extracted: Int, total: Int) {
                _importProgressState.update {
                    TextToSpeechImportProgress.InProgress(
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
            _importProgressState.value = TextToSpeechImportProgress.Started

            var output: File? = null

            try {
                output = modelExtractor.extract(
                    uri = uri,
                    outputDir = outputDir,
                    listener = importListener
                )

                val metadata = readMetadata(output)

                textToSpeechRepository.upsertTextToSpeechModel(metadata)
                _importProgressState.value = TextToSpeechImportProgress.Completed(metadata)
            } catch (e: Exception) {
                output?.deleteRecursively()
                _importProgressState.value = TextToSpeechImportProgress.Error(e)
                Timber.tag(TAG).e(e)
            }
        }
    }

    fun analyze(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _analysisProgressState.value = TextToSpeechAnalysisProgress.InProgress

            try {
                val output = modelExtractor.openFile(uri, "metadata.json")
                    ?: throw Exception("Metadata file not found")
                val languageBundleMetadata =
                    _serializer.decodeFromString<TextToSpeechBundleMetadata>(output.readText())

                if (!languageBundleMetadata.isValid()) {
                    throw Exception("Invalid metadata file")
                }

                _analysisProgressState.value =
                    TextToSpeechAnalysisProgress.Completed(languageBundleMetadata, uri)
            } catch (e: Exception) {
                _analysisProgressState.value = TextToSpeechAnalysisProgress.Error(e)
                Timber.tag(TAG).e(e)
            }
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File?): TextToSpeechModel {
        if (output == null) {
            throw Exception("Output directory is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val languageBundleMetadata =
            _serializer.decodeFromString<TextToSpeechBundleMetadata>(bundleMetadataFile.readText())

        if (!languageBundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val languageMetadataFile =
            File(output.resolve(languageBundleMetadata.metadata.directory), "metadata.json")
        val languageMetadata =
            _serializer.decodeFromString<TextToSpeechModelMetadata>(languageMetadataFile.readText())
                .setRootPath(
                    path = output.resolve(languageBundleMetadata.metadata.directory).toPath()
                )

        if (!languageMetadata.isValid()) {
            throw Exception("Invalid language metadata file")
        }

        return TextToSpeechModel(
            bundle = languageBundleMetadata,
            model = languageMetadata
        )
    }

    companion object {
        private val TAG: String = TextToSpeechImportViewModel::class.java.simpleName
    }
}