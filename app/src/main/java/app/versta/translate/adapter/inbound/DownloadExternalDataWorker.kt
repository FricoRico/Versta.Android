package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DataBundleMetadata
import app.versta.translate.core.entity.DataMetadata
import app.versta.translate.core.entity.DataModel
import app.versta.translate.core.entity.DownloadStatus
import timber.log.Timber
import java.io.File
import java.util.UUID

const val DOWNLOAD_EXTERNAL_DATA_STATUS_INTENT = "DOWNLOAD_EXTERNAL_DATA_STATUS_UPDATE"

class DownloadExternalDataWorker(context: Context, parameters: WorkerParameters) :
    DownloadWorker(context, parameters) {
    override val downloadStatusIntent = Intent(DOWNLOAD_EXTERNAL_DATA_STATUS_INTENT)

    private val _extractionDirectory = context.filesDir

    private val _dataExtractor = MainApplication.module.extractor
    private val _dataRepository = MainApplication.module.dataRepository

    override fun extractDownload(taskId: UUID, file: File) {
        var output: File? = null

        try {
            output = _dataExtractor.extract(
                file = file,
                outputDir = _extractionDirectory,
            )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            val metadata = readMetadata(output)
            _dataRepository.upsertData(metadata)
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    /**
     * Reads the metadata file from the extracted external.
     */
    private fun readMetadata(output: File?): DataModel {
        if (output == null) {
            throw Exception("Output file is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val dataBundleMetadata =
            _serializer.decodeFromString<DataBundleMetadata>(bundleMetadataFile.readText())

        if (!dataBundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val dataMetadataFile =
            File(output.resolve(dataBundleMetadata.metadata.directory), "metadata.json")
        val dataMetadata = _serializer.decodeFromString<DataMetadata>(dataMetadataFile.readText())
            .setRootPath(
                path = output.resolve(dataBundleMetadata.metadata.directory).toPath()
            )

        if (!dataMetadata.isValid()) {
            throw Exception("Invalid data metadata file")
        }

        return DataModel(
            bundle = dataBundleMetadata,
            contents = dataMetadata
        )
    }

    companion object {
        private val TAG = DownloadExternalDataWorker::class.java.simpleName
    }
}
