package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.VoiceModelMetadata
import app.versta.translate.core.entity.VoiceBundleMetadata
import app.versta.translate.core.entity.VoiceModel
import timber.log.Timber
import java.io.File
import java.util.UUID

const val DOWNLOAD_VOICE_STATUS_INTENT = "DOWNLOAD_VOICE_STATUS_UPDATE"

class DownloadVoiceWorker(context: Context, parameters: WorkerParameters) :
    DownloadWorker(context, parameters) {
    override val downloadStatusIntent = Intent(DOWNLOAD_VOICE_STATUS_INTENT)

    private val _extractionDirectory = context.filesDir

    private val _voiceExtractor = MainApplication.module.extractor
    private val _voiceRepository = MainApplication.module.voiceRepository

    /**
     * Extracts the downloaded language model.
     */
    override fun extractDownload(taskId: UUID, file: File) {
        var output: File? = null

        try {
            output = _voiceExtractor.extract(
                file = file,
                outputDir = _extractionDirectory,
            )

            if (!file.delete()) {
                Timber.tag(TAG).e("Deleting file ${file.absolutePath}")
            }

            val metadata = readMetadata(output)
            _voiceRepository.upsertVoiceModel(metadata)
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File?): VoiceModel {
        if (output == null) {
            throw Exception("Output directory is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val voiceBundleMetadata =
            _serializer.decodeFromString<VoiceBundleMetadata>(bundleMetadataFile.readText())

        if (!voiceBundleMetadata.isValid()) {
            throw Exception("Invalid metadata file")
        }

        val voiceMetadataFile =
            File(output.resolve(voiceBundleMetadata.metadata.directory), "metadata.json")
        val voiceMetadata =
            _serializer.decodeFromString<VoiceModelMetadata>(voiceMetadataFile.readText())
                .setRootPath(
                    path = output.resolve(voiceBundleMetadata.metadata.directory).toPath()
                )

        if (!voiceMetadata.isValid()) {
            throw Exception("Invalid voice metadata file")
        }

        return VoiceModel(
            bundle = voiceBundleMetadata,
            model = voiceMetadata
        )
    }

    companion object {
        private val TAG = DownloadVoiceWorker::class.java.simpleName
    }
}