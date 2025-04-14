package app.versta.translate.adapter.inbound

import java.io.File
import java.net.URI

interface DownloadListener {
    fun onProgressUpdate(downloaded: Long, total: Long)
    fun onCompletion(file: File)
    fun onError(exception: Exception)
}

interface DownloadClient {
    /**
     * Download the file and checksum file if provided.
     */
    suspend fun download(uri: URI, checksum: URI?, listener: DownloadListener)
}