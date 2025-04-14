package app.versta.translate.adapter.inbound

import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.wait
import okio.buffer
import okio.sink
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class HttpDownloadClient(val output: File) : DownloadClient {
    /**
     * Download the file and checks the file against the checksum if provided.
     */
    override suspend fun download(uri: URI, checksum: URI?, listener: DownloadListener) {
        return suspendCoroutine { coroutine ->
            ensureOutputDirectory(output)

            val request = Request.Builder().url(uri.toString()).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    listener.onError(e)
                    coroutine.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!response.isSuccessful) {
                                throw Exception("Unexpected code $response")
                            }

                            val body = response.body ?: throw Exception("Response body is empty")
                            val file = uri.path?.substringAfterLast('/')?.let {
                                output.resolve(it)
                            }
                            if (file == null) {
                                throw Exception("Output file could not be resolved")
                            }

                            val sink = file.sink().buffer()
                            val source = body.source()

                            var downloaded = 0L
                            val total = body.contentLength()

                            var bytesRead: Long

                            while (source.read(sink.buffer, 8192).also
                                { bytesRead = it } != -1L
                            ) {
                                downloaded += bytesRead

                                sink.emit()
                                listener.onProgressUpdate(downloaded, total)
                            }

                            sink.close()
                            body.close()

                            if (checksum != null && !verify(file.inputStream(), checksum)) {
                                throw Exception("Download verification failed")
                            }

                            listener.onCompletion(file)
                            coroutine.resume(Unit)
                        }
                    } catch (e: Exception) {
                        listener.onError(e)
                        coroutine.resumeWithException(e)
                    }
                }
            })
        }
    }

    /**
     * Verifies the downloaded file against the remote checksum.
     */
    private fun verify(stream: InputStream, checksum: URI): Boolean {
        val request = Request.Builder().url(checksum.toString()).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Unexpected code $response")
        }

        val body = response.body ?: throw Exception("Response body is empty")
        val expected = body.string().trim()

        val hash = MessageDigest.getInstance("SHA-256")
        stream.use {
            val buffer = ByteArray(8192)
            var bytesRead = it.read(buffer)

            while (bytesRead != -1) {
                hash.update(buffer, 0, bytesRead)
                bytesRead = it.read(buffer)
            }
        }
        val actual = hash.digest().joinToString("") {
            "%02x".format(it)
        }

        return actual == expected
    }

    /**
     * Ensures the output directory exists.
     */
    private fun ensureOutputDirectory(path: File) {
        if (!path.exists()) {
            path.mkdirs()
        }
    }

    /**
     * Clears the output directory of any existing files.
     */
    private fun clearOutputDirectory(path: File) {
        path.toPath().removeAll {
            it.endsWith(".tar.gz")
        }
    }

    init {
        ensureOutputDirectory(output)
        clearOutputDirectory(output)
    }

    companion object {
        private val client = OkHttpClient()

        private val TAG = HttpDownloadClient::class.java.simpleName
    }
}