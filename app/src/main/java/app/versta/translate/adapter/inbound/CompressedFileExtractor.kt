package app.versta.translate.adapter.inbound

import android.net.Uri
import java.io.File

interface ExtractionProgressListener {
    /**
     * Callback for progress updates during extraction.
     * @param file The file currently being extracted.
     * @param extracted The number of files that have been extracted so far.
     * @param total The total number of files in the archive.
     */
    fun onProgressUpdate(file: File, extracted: Int, total: Int)
}

interface CompressedFileExtractor {
    /**
     * Extracts the contents of a compressed archive file from a given Uri into the app's local storage.
     * @param uri The Uri of the zip file to extract.
     * @param outputDir The local directory where the contents should be extracted.
     * @param extractToDirectory Whether to extract the contents to a directory with the same name as the compressed file.
     */
    fun extract(
        uri: Uri,
        outputDir: File,
        extractToDirectory: Boolean = true,
        listener: ExtractionProgressListener? = null
    ): File

    /**
     * Opens a file from a compressed archive file.
     * @param uri The Uri of the archive file.
     * @param fileName The name of the file to open.
     */
    fun openFile(uri: Uri, fileName: String): File?
}