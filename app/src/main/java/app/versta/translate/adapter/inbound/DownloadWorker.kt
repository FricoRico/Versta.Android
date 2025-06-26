package app.versta.translate.adapter.inbound

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.versta.translate.MainApplication.Companion.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import app.versta.translate.MainApplication.Companion.DOWNLOAD_NOTIFICATION_ID
import app.versta.translate.R
import app.versta.translate.core.entity.DownloadStatus
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

abstract class DownloadWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    protected open val downloadStatusIntent: Intent? = null

    private val _workManager = WorkManager.getInstance(context)

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

    private val _downloadDirectory = context.cacheDir.resolve("downloads")

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
    protected open fun extractDownload(taskId: UUID, file: File) {}

    /**
     * Removes the download task from the queue.
     */
    private fun removeDownloadTask(taskId: UUID) {
        _queue.removeIf { it.id == taskId }
    }

    internal fun setStatus(taskId: UUID, status: DownloadStatus) {
        if (downloadStatusIntent == null) {
            Timber.tag(TAG).e("Download status intent is null")
            return
        }

        downloadStatusIntent?.putExtra("taskId", taskId.toString())
        downloadStatusIntent?.putExtra("status", status)

        context.sendBroadcast(downloadStatusIntent)
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

        internal val _serializer = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

        private val TAG = DownloadWorker::class.java.simpleName
    }
}