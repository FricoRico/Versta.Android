package app.versta.translate.adapter.outbound

import android.net.Uri

interface FileSaver {
    /**
     * Opens a file picker to select a location.
     * @param listener The callback to be invoked when a file is picked.
     */
    fun saveFilePicker(listener: FileSaverCallback)
}

interface FileSaverCallback {
    /**
     * Callback for when a file has been saved.
     * @param uri The Uri of the saved file.
     */
    fun onFileSaved(uri: Uri)
}