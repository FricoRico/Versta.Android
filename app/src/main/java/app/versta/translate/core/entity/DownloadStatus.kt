package app.versta.translate.core.entity

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data object Queued : DownloadStatus()

    data class Progress(val downloaded: Long, val total: Long) : DownloadStatus()
    data object Processing : DownloadStatus()

    data object Completed : DownloadStatus()
    data class Error(val exception: Exception) : DownloadStatus()
}