package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorModel
import app.versta.translate.core.entity.ObjectCharacterRecognitionModule
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerMetadata
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerModel
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

            val (detectors, recognizers) = readMetadata(output)
            detectors.forEach { detector ->
                _ocrRepository.upsertObjectCharacterRecognitionDetector(detector)
            }
            recognizers.forEach { recognizer ->
                _ocrRepository.upsertObjectCharacterRecognitionRecognizer(recognizer)
            }
        } catch (e: Exception) {
            output?.deleteRecursively()
            setStatus(taskId, DownloadStatus.Error(e))
            Timber.tag(TAG).e(e, "Extracting file ${file.absolutePath}")
        }
    }

    /**
     * Reads the metadata file from the extracted model.
     */
    private fun readMetadata(output: File?): Pair<List<ObjectCharacterRecognitionDetectorModel>, List<ObjectCharacterRecognitionRecognizerModel>> {
        if (output == null) {
            throw Exception("Output directory is null")
        }

        val bundleMetadataFile = File(output, "metadata.json")
        val bundleMetadataText = bundleMetadataFile.readText()
        val bundleMetadata = _serializer.decodeFromString<ObjectCharacterRecognitionBundleMetadata>(bundleMetadataText)

        if (!bundleMetadata.isValid()) {
            throw Exception("Invalid bundle metadata file")
        }

        val detectorModels = bundleMetadata.metadata
            .filter { it.module == ObjectCharacterRecognitionModule.Detector }
            .map { detectorMetadataFile ->
                val detectorMetadataPath = File(output.resolve(detectorMetadataFile.directory), "metadata.json")
                val detectorMetadata = _serializer.decodeFromString<ObjectCharacterRecognitionDetectorMetadata>(detectorMetadataPath.readText())
                    .setRootPath(output.resolve(detectorMetadataFile.directory).toPath())

                if (!detectorMetadata.isValid()) {
                    throw Exception("Invalid detector metadata file at ${detectorMetadataFile.directory}")
                }

                ObjectCharacterRecognitionDetectorModel(
                    bundle = bundleMetadata,
                    model = detectorMetadata
                )
            }

        val recognizerModels = bundleMetadata.metadata
            .filter { it.module == ObjectCharacterRecognitionModule.Recognizer }
            .map { recognizerMetadataFile ->
                val recognizerMetadataPath = File(output.resolve(recognizerMetadataFile.directory), "metadata.json")
                val recognizerMetadata = _serializer.decodeFromString<ObjectCharacterRecognitionRecognizerMetadata>(recognizerMetadataPath.readText())
                    .setRootPath(output.resolve(recognizerMetadataFile.directory).toPath())

                if (!recognizerMetadata.isValid()) {
                    throw Exception("Invalid recognizer metadata file at ${recognizerMetadataFile.directory}")
                }

                ObjectCharacterRecognitionRecognizerModel(
                    bundle = bundleMetadata,
                    model = recognizerMetadata
                )
            }

        return Pair(detectorModels, recognizerModels)
    }

    companion object {
        private val TAG = DownloadObjectCharacterRecognitionWorker::class.java.simpleName
    }
}

