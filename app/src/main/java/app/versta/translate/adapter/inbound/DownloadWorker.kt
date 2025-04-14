package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication
import app.versta.translate.MainApplication.Companion.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import app.versta.translate.MainApplication.Companion.DOWNLOAD_NOTIFICATION_ID
import app.versta.translate.R
import app.versta.translate.core.entity.DOWNLOAD_STATUS_INTENT
import app.versta.translate.core.entity.DownloadStatus
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelMetadata
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.net.URI
import java.util.ArrayDeque
import java.util.Queue
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

internal data class DownloadQueue(
    val id: UUID,
    val name: String?,
    val uri: URI,
    val checksum: URI,
)

class DownloadWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private val _workManager = WorkManager.getInstance(context)
    private val _broadcastManager = LocalBroadcastManager.getInstance(context)

    private var _previousProgress = 0
    private val _notification =
        NotificationCompat.Builder(applicationContext, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.download_progress_notification_title))
            .setTicker(context.getString(R.string.download_progress_notification_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_delete, context.getString(R.string.cancel), _workManager.createCancelPendingIntent(
                    id
                )
            )

    private val _extractionDirectory = context.filesDir
    private val _downloadDirectory = context.cacheDir.resolve("downloads")

    private val _languageExtractor = MainApplication.module.extractor
    private val _languageRepository = MainApplication.module.languageRepository

    private val _downloadClient = HttpDownloadClient(_downloadDirectory)

    override suspend fun doWork(): Result {
        val taskId = UUID.fromString(inputData.getString("taskId"))

        val name = inputData.getString("name")
        val uri = inputData.getString("uri")?.let { URI(it) }
        val checksum = inputData.getString("checksum")?.let { URI(it) }

        if (uri == null || checksum == null) {
            return Result.failure()
        }


        _queue.add(
            DownloadQueue(
                id = taskId,
                name = name,
                uri = uri,
                checksum = checksum,
            )
        )

        setForegroundAsync(createForegroundInfo(name))
        handleDownloadQueue()

        return Result.success()
    }

    /**
     * Handles the download queue.
     */
    private suspend fun handleDownloadQueue() {
        if (_downloading || _queue.isEmpty()) {
            return
        }
        _downloading = true

        try {
            _queue.poll()?.let { task ->
                _downloadClient.download(task.uri, task.checksum, object : DownloadListener {
                    override fun onProgressUpdate(downloaded: Long, total: Long) {
                        if (isStopped) {
                            setStatus(task.id, DownloadStatus.Cancelled)
                            throw CancellationException("Download cancelled")
                        }

                        setProgress(task.name, downloaded, total)
                        setStatus(task.id, DownloadStatus.Progress(downloaded, total))
                    }

                    override fun onCompletion(file: File) {
                        setForegroundAsync(createForegroundInfo(task.name))
                        setStatus(task.id, DownloadStatus.Processing)

                        extractDownload(task.id, file)

                        setStatus(task.id, DownloadStatus.Completed)
                        removeDownloadTask(task.id)
                    }

                    override fun onError(exception: Exception) {
                        setStatus(task.id, DownloadStatus.Error(exception))
                        removeDownloadTask(task.id)
                    }
                })
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                return
            }

            Timber.tag(TAG).e(e)
        } finally {
            _downloading = false
            handleDownloadQueue()
        }
    }

    /**
     * Extracts the downloaded language model.
     */
    private fun extractDownload(taskId: UUID, file: File) {
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
     * Removes the download task from the queue.
     */
    private fun removeDownloadTask(taskId: UUID) {
        _queue.removeIf { it.id == taskId }
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

    private fun setStatus(taskId: UUID, status: DownloadStatus) {
        val intent = Intent(DOWNLOAD_STATUS_INTENT)

        intent.putExtra("taskId", taskId.toString())
        intent.putExtra("status", status)

        _broadcastManager.sendBroadcast(intent)
    }

    private fun createForegroundInfo(name: String?, progress: Int = 0): ForegroundInfo {
        val notification = _notification
            .setProgress(100, progress, progress == 0)
            .apply {
                if (name != null) {
                    setContentText(
                        applicationContext.getString(
                            R.string.download_progress_notification_content,
                            name
                        ))
                }
            }
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                DOWNLOAD_NOTIFICATION_ID,
                notification,
                FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(DOWNLOAD_NOTIFICATION_ID, notification)
        }
    }

    private fun setProgress(name: String?, downloaded: Long, total: Long) {
        val progress = (downloaded * 100 / total).toInt()
        if (progress == _previousProgress) {
            return
        }

        _previousProgress = progress
        setForegroundAsync(createForegroundInfo(name, progress))
    }

    companion object {
        private var _downloading = false
        private val _queue: Queue<DownloadQueue> = ArrayDeque()

        private val _serializer = Json { ignoreUnknownKeys = true }

        private val TAG = DownloadWorker::class.java.simpleName
    }
}