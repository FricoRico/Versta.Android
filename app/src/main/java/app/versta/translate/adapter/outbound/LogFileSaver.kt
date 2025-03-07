package app.versta.translate.adapter.outbound

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity

object LogFileSaver : FileSaver {
    private var fileSaver: ActivityResultLauncher<String>? = null
    private var fileSaverListener: FileSaverCallback? = null

    /**
     * Registers the file saver for the given activity.
     */
    fun registerForActivity(activity: ComponentActivity) {
        fileSaver = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument(),
        ) { uri: Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }

            fileSaverListener?.onFileSaved(uri)
        }
    }

    /**
     * Opens a file picker to select a location.
     */
    override fun saveFilePicker(listener: FileSaverCallback) {
        fileSaverListener = listener
        fileSaver?.launch("versta.log")
    }
}
