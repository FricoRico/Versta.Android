package app.versta.translate.adapter.inbound

import android.net.Uri

interface ModelFilePicker {
    /**
     * Opens a file picker to select a file.
     * @param listener The callback to be invoked when a file is picked.
     */
    fun openFilePicker(listener: ModelFilePickerCallback, fileTypes: Array<String> = arrayOf("*/*"))
}

interface ModelFilePickerCallback {
    /**
     * Callback for when a file has been picked.
     * @param uri The Uri of the picked file.
     */
    fun onFilePicked(uri: Uri)
}