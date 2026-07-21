package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.SpeechRecognitionBundleMetadata
import app.versta.translate.core.entity.SpeechRecognitionMetadata
import app.versta.translate.core.entity.SpeechRecognitionModel
import timber.log.Timber
import java.io.File
import java.util.UUID

const val DOWNLOAD_SPEECH_RECOGNITION_STATUS_INTENT = "DOWNLOAD_SPEECH_RECOGNITION_STATUS_UPDATE"

class DownloadSpeechRecognitionWorker(context: Context, parameters: WorkerParameters) :
    DownloadWorker(context, parameters) {
    override val downloadStatusIntent = Intent(DOWNLOAD_SPEECH_RECOGNITION_STATUS_INTENT)

    private val _extractionDirectory = context.filesDir

    private val _extractor = MainApplication.module.extractor
    private val _speechRecognitionRepository = MainApplication.module.speechRecognitionRepository

    override fun extractDownload(taskId: UUID, file: File) {
        var output: File? = null

        try {
            output = _extractor.extract(
                file = file,
                outputDir = _extractionDirectory,
            )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            val model = readMetadata(output)
            _speechRecognitionRepository.upsertSpeechRecognitionModel(model)
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    private fun readMetadata(output: File?): SpeechRecognitionModel {
        if (output == null) {
            throw Exception("Output directory is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val bundleMetadataText = bundleMetadataFile.readText()
        val bundleMetadata = _serializer.decodeFromString<SpeechRecognitionBundleMetadata>(bundleMetadataText)

        if (!bundleMetadata.isValid()) {
            throw Exception("Invalid bundle metadata file")
        }

        val modelMetadata = bundleMetadata.metadata.firstOrNull()
            ?: throw Exception("No speech recognition module metadata found")

        val modelMetadataPath = File(output.resolve(modelMetadata.directory), "metadata.json")
        val modelMetadataText = modelMetadataPath.readText()
        val modelMetadataParsed = _serializer.decodeFromString<SpeechRecognitionMetadata>(modelMetadataText)
            .setRootPath(output.resolve(modelMetadata.directory).toPath())

        if (!modelMetadataParsed.isValid()) {
            throw Exception("Invalid speech recognition metadata file at ${modelMetadata.directory}")
        }

        return SpeechRecognitionModel(
            bundle = bundleMetadata,
            model = modelMetadataParsed,
        )
    }

    companion object {
        private val TAG = DownloadSpeechRecognitionWorker::class.java.simpleName
    }
}
