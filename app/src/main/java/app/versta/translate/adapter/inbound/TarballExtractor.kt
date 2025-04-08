package app.versta.translate.adapter.inbound

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile
import androidx.core.net.toUri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class TarballExtractor(private val context: Context) : CompressedFileExtractor {
    /**
     * Extracts the contents of a .tar.gz file from a given Uri into the app's local storage.
     * @param uri The Uri of the .tar.gz file to extract.
     * @param outputDir The local directory where the contents should be extracted.
     * @param extractToDirectory Whether to extract the contents to a directory with the same name as the compressed file.
     */
    override fun extract(
        uri: Uri,
        outputDir: File,
        extractToDirectory: Boolean,
        listener: ExtractionProgressListener?
    ): File? {
        try {
            return extractFile(uri, outputDir, extractToDirectory, listener)
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Extracts the contents of a compressed archive file from a given [File] into the app's local storage.
     * @param file The File of the zip file to extract.
     * @param outputDir The local directory where the contents should be extracted.
     * @param extractToDirectory Whether to extract the contents to a directory with the same name as the compressed file.
     */
    override fun extract(
        file: File,
        outputDir: File,
        extractToDirectory: Boolean,
        listener: ExtractionProgressListener?
    ): File? {
        try {
            return extractFile(file.toUri(), outputDir, extractToDirectory, listener)
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Extracts the contents of a compressed archive file from a given [InputStream] into the app's local storage.
     * @param stream An already opened input stream
     * @param outputDir The local directory where the contents should be extracted.
     */
    override fun extract(
        stream: InputStream,
        outputDir: File,
    ): File {
        return extractInputStream(stream, outputDir)
    }

    /**
     * Opens a file from a compressed archive file.
     * @param uri The Uri of the archive file.
     * @param fileName The name of the file to open.
     */
    override fun openFile(uri: Uri, fileName: String): File? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        TarArchiveInputStream(GzipCompressorInputStream(stream)).use { input ->
            var entry = input.nextEntry

            while (entry != null) {
                if (entry.name == fileName) {
                    val metadataBytes = input.readBytes()
                    return File.createTempFile(fileName, null, context.cacheDir).apply {
                        writeBytes(metadataBytes)
                    }
                }

                entry = input.nextEntry
            }
        }

        return null
    }

    /**
     * Extracts the contents of a .tar.gz file into a specified directory.
     * @param uri The [Uri] object pointing to the .tar.gz file.
     * @param outputDir The directory where the contents should be extracted.
     */
    private fun extractFile(
        uri: Uri,
        outputDir: File,
        extractToDirectory: Boolean,
        listener: ExtractionProgressListener?
    ): File? {
        val stream = context.contentResolver.openInputStream(uri) ?: return null

        val total = if (listener != null) getTotalEntries(uri) else 0
        val extractionDir = if (extractToDirectory) {
            val fileName = getFileName(uri) ?: throw IllegalArgumentException("File name not found")
            val fileNameWithoutExtension = fileName.removeSuffix(".tar.gz")
            File(outputDir, fileNameWithoutExtension)
        } else outputDir

        return extractInputStream(stream, extractionDir, total, listener)
    }

    /**
     * Extracts the contents of a .tar.gz file from an InputStream into a specified directory.
     * @param stream The [InputStream] object pointing to the .tar.gz file.
     * @param outputDir The directory where the contents should be extracted.
     * @param listener The listener for progress updates during extraction.
     */
    private fun extractInputStream(
        stream: InputStream,
        outputDir: File,
        total: Int? = null,
        listener: ExtractionProgressListener? = null
    ): File {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        TarArchiveInputStream(GzipCompressorInputStream(stream)).use { input ->
            var entry = input.nextEntry
            var extracted = 0

            while (entry != null) {
                val path = File(outputDir, entry.name)

                if (entry.isDirectory) {
                    path.mkdirs()
                } else {
                    path.parentFile?.mkdirs()
                    FileOutputStream(path).use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }

                if (listener != null && total != null) {
                    extracted++
                    listener.onProgressUpdate(path, extracted, total)
                }

                entry = input.nextEntry
            }
        }

        return outputDir
    }

    /**
     * Helper method to retrieve the file name from a Uri.
     * @param uri The [Uri] to extract the file name from.
     * @return The file name or null if it can't be determined.
     */
    private fun getFileName(uri: Uri): String? {
        var result: String? = null

        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }
        }

        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }

        return result
    }

    /**
     * Helper method to retrieve the total number of entries in a .tar.gz file.
     * @param uri The [Uri] object pointing to the .tar.gz file.
     * @return The total number of entries in the .tar.gz file.
     */
    private fun getTotalEntries(uri: Uri): Int {
        var totalEntries = 0

        val stream = context.contentResolver.openInputStream(uri) ?: return 0
        TarArchiveInputStream(GzipCompressorInputStream(stream)).use { input ->
            while (input.nextEntry != null) {
                totalEntries++
            }
        }

        return totalEntries
    }

    companion object {
        private val TAG: String = TarballExtractor::class.java.simpleName
    }
}