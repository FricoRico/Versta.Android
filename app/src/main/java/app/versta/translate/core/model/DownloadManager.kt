package app.versta.translate.core.model

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.widget.Toast
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.workDataOf
import app.versta.translate.R
import app.versta.translate.adapter.inbound.DownloadWorker
import app.versta.translate.core.entity.DownloadStatus
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.internal.platform.PlatformRegistry.applicationContext
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Generic download task interface that download implementations must implement.
 */
interface DownloadTask {
    val id: UUID
    val status: DownloadStatus

    fun copyWithStatus(status: DownloadStatus): DownloadTask
    fun getWorkData(): Map<String, Any>
    fun getName(): String
    fun onComplete() {}
}

/**
 * Reusable download manager for handling background downloads with WorkManager.
 *
 * @param T The type of download task that extends DownloadTask
 */
class DownloadManager<T : DownloadTask>(
    private val context: Context,
    private val statusIntentAction: String,
    private val workerClass: Class<out DownloadWorker>
) {
    private var downloadWorker: WorkRequest? = null
    val downloadTasks = MutableStateFlow<List<T>>(emptyList())

    /**
     * Broadcast receiver for download status updates.
     */
    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val taskId = UUID.fromString(intent.getStringExtra("taskId"))
            val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("status", DownloadStatus::class.java)
            } else {
                intent.getSerializableExtra("status") as? DownloadStatus
            }

            status?.let {
                updateDownloadStatus(context, taskId, it)
            }
        }
    }

    /**
     * Queues a download for the given task.
     */
    fun queueDownload(task: T) {
        val existingTask = downloadTasks.value.firstOrNull { it.id == task.id }

        val taskToQueue = if (existingTask != null) {
            @Suppress("UNCHECKED_CAST")
            existingTask.copyWithStatus(DownloadStatus.Queued) as T
        } else {
            @Suppress("UNCHECKED_CAST")
            task.copyWithStatus(DownloadStatus.Queued) as T
        }

        if (existingTask == null) {
            downloadTasks.value += taskToQueue
        } else {
            updateTaskInList(taskToQueue)
        }

        val manager = WorkManager.getInstance(context)
        val worker = OneTimeWorkRequest.Builder(workerClass)
            .setInputData(workDataOf(*taskToQueue.getWorkData().toList().toTypedArray()))
            .build()

        manager.enqueue(worker)
        manager.getWorkInfoByIdLiveData(worker.id)

        if (downloadWorker == null) {
            downloadWorker = worker
        }
    }

    /**
     * Cancels all pending downloads.
     */
    fun cancelDownload() {
        downloadWorker?.let {
            WorkManager.getInstance(context).cancelWorkById(it.id)
        }
        downloadWorker = null
    }

    /**
     * Updates the download status of a task.
     */
    private fun updateDownloadStatus(context: Context, taskId: UUID, status: DownloadStatus) {
        when (status) {
            is DownloadStatus.Completed -> {
                triggerDownloadTaskCallback(taskId)
                removeDownloadTask(taskId)
            }

            is DownloadStatus.Error -> {
                when (status.exception) {
                    is CancellationException -> {}

                    is SocketException,
                    is SocketTimeoutException,
                    is UnknownHostException -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.download_error_no_internet),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    else -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.download_error_unknown),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                setDownloadStatus(taskId, status)
            }

            is DownloadStatus.Cancelled -> {
                clearDownloadTasks()
            }

            else -> {
                setDownloadStatus(taskId, status)
            }
        }
    }

    /**
     * Sets the download status of a task.
     */
    private fun setDownloadStatus(taskId: UUID, status: DownloadStatus) {
        downloadTasks.value = downloadTasks.value.map { task ->
            if (task.id == taskId) {
                @Suppress("UNCHECKED_CAST")
                task.copyWithStatus(status) as T
            } else {
                task
            }
        }
    }

    /**
     * Updates a task in the list.
     */
    private fun updateTaskInList(task: T) {
        downloadTasks.value = downloadTasks.value.map { existingTask ->
            if (existingTask.id == task.id) task else existingTask
        }
    }

    /**
     * Triggers the completion callback for a download task.
     */
    private fun triggerDownloadTaskCallback(taskId: UUID) {
        downloadTasks.value.find { it.id == taskId }?.onComplete()
    }

    /**
     * Removes the download task from the queue.
     */
    private fun removeDownloadTask(taskId: UUID) {
        downloadTasks.value = downloadTasks.value.filter { it.id != taskId }
    }

    /**
     * Clears all download tasks.
     */
    fun clearDownloadTasks() {
        downloadTasks.value = emptyList()
    }

    /**
     * Registers the broadcast receiver. Call this in ViewModel init.
     */
    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                downloadStatusReceiver,
                IntentFilter(statusIntentAction),
                Context.RECEIVER_EXPORTED
            )
        } else {
            context.registerReceiver(
                downloadStatusReceiver,
                IntentFilter(statusIntentAction)
            )
        }
    }

    /**
     * Unregisters the broadcast receiver. Call this in ViewModel onCleared.
     */
    fun unregister() {
        context.unregisterReceiver(downloadStatusReceiver)
    }
}
