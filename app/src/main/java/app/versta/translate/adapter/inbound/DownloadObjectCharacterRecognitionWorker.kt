package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionModuleModel
import timber.log.Timber
import java.io.File
import java.util.UUID

const val DOWNLOAD_OBJECT_CHARACTER_RECOGNITION_STATUS_INTENT = "DOWNLOAD_OBJECT_CHARACTER_RECOGNITION_STATUS_UPDATE"

class DownloadObjectCharacterRecognitionWorker(context: Context, parameters: WorkerParameters) :
    DownloadWorker(context, parameters) {
    override val downloadStatusIntent = Intent(DOWNLOAD_OBJECT_CHARACTER_RECOGNITION_STATUS_INTENT)

    private val _extractionDirectory = context.filesDir

    private val _ocrExtractor = MainApplication.module.extractor
    private val _ocrRepository = MainApplication.module.objectCharacterRecognizerRepository

    /**
     * Extracts the downloaded OCR model.
     */
    override fun extractDownload(taskId: UUID, file: File) {
        var output: File? = null

        try {
            output = _ocrExtractor.extract(
                file = file,
                outputDir = _extractionDirectory,
            )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            readMetadata(output).forEach { module ->
                _ocrRepository.upsertModule(module)
            }
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    /**
     * Reads the bundle manifest and every module manifest, returning one model
     * per module directory listed in the bundle.
     */
    private fun readMetadata(output: File?): List<ObjectCharacterRecognitionModuleModel> {
        if (output == null) {
            throw Exception("Output directory is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val bundleMetadataText = bundleMetadataFile.readText()
        val bundleMetadata = _serializer.decodeFromString<ObjectCharacterRecognitionBundleMetadata>(bundleMetadataText)

        if (!bundleMetadata.isValid()) {
            throw Exception("Invalid bundle metadata file")
        }

        return bundleMetadata.metadata.map { metadataFile ->
            val modulePath = output.resolve(metadataFile.directory)
            val moduleMetadataFile = File(modulePath, "metadata.json")
            val moduleMetadata = _serializer.decodeFromString<ObjectCharacterRecognitionModuleMetadata>(moduleMetadataFile.readText())

            if (!moduleMetadata.isValid(modulePath.toPath())) {
                throw Exception("Invalid or incomplete module metadata at ${metadataFile.directory}")
            }

            ObjectCharacterRecognitionModuleModel(
                bundle = bundleMetadata,
                model = moduleMetadata,
                directory = metadataFile.directory,
                root = modulePath.toPath()
            )
        }
    }

    companion object {
        private val TAG = DownloadObjectCharacterRecognitionWorker::class.java.simpleName
    }
}
