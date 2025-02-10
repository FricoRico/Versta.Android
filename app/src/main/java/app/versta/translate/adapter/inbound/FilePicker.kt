package app.versta.translate.adapter.inbound

import android.net.Uri

interface FilePicker {
    /**
     * Opens a file picker to select a file.
     * @param listener The callback to be invoked when a file is picked.
     */
    fun openFilePicker(listener: FilePickerCallback, fileTypes: Array<String> = emptyArray())
}

interface FilePickerCallback {
    /**
     * Callback for when a file has been picked.
     * @param uri The Uri of the picked file.
     */
    fun onFilePicked(uri: Uri)
}