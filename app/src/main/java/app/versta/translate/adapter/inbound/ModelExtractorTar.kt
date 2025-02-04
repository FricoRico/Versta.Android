package app.versta.translate.adapter.inbound

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class ModelExtractorTar(private val context: Context) : ModelExtractor {
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
        listener: ModelExtractorProgressListener?
    ): File {
        var file: File? = null

        try {
            file = copyToInternalStorage(uri)
            return extractFile(file, outputDir, extractToDirectory, listener)
        } catch (e: Exception) {
            throw e
        } finally {
            // Clean up the temporary file
            if (file?.exists() == true) {
                file.delete()
            }
        }
    }

    /**
     * Copies the file from the given Uri to the app's internal storage.
     * @param uri The Uri of the file to copy.
     * @return The File object pointing to the copied file in internal storage.
     */
    private fun copyToInternalStorage(uri: Uri): File {
        val fileName = getFileName(uri) ?: "tmp.tar.gz"
        val destinationFile = File(context.filesDir, fileName)

        context.contentResolver.openInputStream(uri).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
        }

        return destinationFile
    }

    /**
     * Extracts the contents of a .tar.gz file into a specified directory.
     * @param file The File object pointing to the .tar.gz file.
     * @param outputDir The directory where the contents should be extracted.
     */
    private fun extractFile(
        file: File,
        outputDir: File,
        extractToDirectory: Boolean,
        listener: ModelExtractorProgressListener?
    ): File {
        val total = if (listener != null) getTotalEntries(file) else 0
        val extractionDir = if (extractToDirectory) {
            val fileNameWithoutExtension = file.name.removeSuffix(".tar.gz")
            File(outputDir, fileNameWithoutExtension)
        } else outputDir

        if (!extractionDir.exists()) {
            extractionDir.mkdirs()
        }

        TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(file))).use { input ->
            var entry = input.nextTarEntry
            var extracted = 0

            while (entry != null) {
                val path = File(extractionDir, entry.name)

                if (entry.isDirectory) {
                    path.mkdirs()
                } else {
                    path.parentFile?.mkdirs()
                    FileOutputStream(path).use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }

                if (listener != null) {
                    extracted++
                    listener.onProgressUpdate(path, extracted, total)
                }

                entry = input.nextTarEntry
            }
        }

        return extractionDir
    }

    /**
     * Helper method to retrieve the file name from a Uri.
     * @param uri The Uri to extract the file name from.
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
     * @param file The File object pointing to the .tar.gz file.
     * @return The total number of entries in the .tar.gz file.
     */
    private fun getTotalEntries(file: File): Int {
        var totalEntries = 0

        TarArchiveInputStream(GzipCompressorInputStream(FileInputStream(file))).use { input ->
            while (input.nextTarEntry != null) {
                totalEntries++
            }
        }

        return totalEntries
    }

    companion object {
        private val TAG: String = ModelExtractorTar::class.java.simpleName
    }
}