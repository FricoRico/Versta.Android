package app.versta.translate.adapter.inbound

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object ModelFilePickerLauncher: ModelFilePicker {
    private var filePicker: ActivityResultLauncher<Array<String>>? = null
    private var filePickerListener: ModelFilePickerCallback? = null

    /**
     * Registers the file picker for the given activity.
     */
    fun registerForActivity(activity: ComponentActivity) {
        filePicker = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
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
    override fun openFilePicker(listener: ModelFilePickerCallback, fileTypes: Array<String>) {
        filePickerListener = listener
        filePicker?.launch(fileTypes)
    }
}