package app.versta.translate.adapter.inbound

import android.content.Context
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelMetadata
import timber.log.Timber
import java.io.File
import java.util.UUID

class DownloadLanguageWorker(context: Context, parameters: WorkerParameters) :
    DownloadWorker(context, parameters) {
    private val _extractionDirectory = context.filesDir

    private val _languageExtractor = MainApplication.module.extractor
    private val _languageRepository = MainApplication.module.languageRepository

    /**
     * Extracts the downloaded language model.
     */
    override fun extractDownload(taskId: UUID, file: File) {
        var output: File? = null

        try {
            output = _languageExtractor.extract(
                file = file,
                outputDir = _extractionDirectory,
            )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            val metadata = readMetadata(output)
            _languageRepository.upsertLanguageModels(metadata)
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
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
        private val TAG = DownloadLanguageWorker::class.java.simpleName
    }
}