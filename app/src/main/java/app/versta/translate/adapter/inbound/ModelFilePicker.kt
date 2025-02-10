package app.versta.translate.adapter.inbound

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object ModelFilePicker : FilePicker {
    private var filePicker: ActivityResultLauncher<Array<String>>? = null
    private var filePickerListener: FilePickerCallback? = null

    /**
     * Registers the file picker for the given activity.
     */
    fun registerForActivity(activity: ComponentActivity) {
        filePicker = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }

            filePickerListener?.onFilePicked(uri)
        }
    }

    /**
     * Opens a file picker to select a file.
     */
    override fun openFilePicker(listener: FilePickerCallback, fileTypes: Array<String>) {
        var types = fileTypes

        if (fileTypes.isEmpty()) {
            types = arrayOf("application/gzip", "application/tar+gzip", "application/x-gzip")
        }

        filePickerListener = listener
        filePicker?.launch(types)
    }
}