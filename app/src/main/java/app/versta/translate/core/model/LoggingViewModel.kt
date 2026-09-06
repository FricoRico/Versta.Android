package app.versta.translate.core.model

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class LoggingViewModel(directory: File?) : ViewModel() {
    private var _file: File = File(directory, "app.log")
    private lateinit var observer: FileObserver

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    /**
     * Gets the content of the log file.
     */
    private fun readLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_file.exists()) {
                _logs.value = ""
                return@launch
            }

            // The log grows unbounded over a session; read only the tail end —
            // a full read on a long camera session crashes with OOM.
            val length = _file.length()
            val start = (length - MAX_LOG_BYTES).coerceAtLeast(0L)
            val bytes = FileInputStream(_file).use { input ->
                input.skip(start)
                input.readBytes()
            }
            _logs.value = if (start > 0) {
                // Skip to the next newline so the tail starts mid-line break.
                val newline = bytes.indexOf('\n'.code.toByte())
                bytes.decodeToString(if (newline >= 0) newline + 1 else 0)
            } else {
                bytes.decodeToString()
            }
        }
    }

    /**
     * Saves the content of the log file.
     */
    fun saveLogs(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FileInputStream(_file).use { fis ->
                    context.contentResolver.openOutputStream(uri).use { os ->
                        if (os == null) {
                            return@launch
                        }

                        val buffer = ByteArray(1024)
                        var length: Int
                        while ((fis.read(buffer).also { length = it }) > 0) {
                            os.write(buffer, 0, length)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error saving log file")
            }
        }
    }

    /**
     * Clears the content of the log file.
     */
    fun clearLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            _file.delete()
            readLogs()
        }
    }

    /**
     * Starts a FileObserver to monitor changes in the log file.
     */
    private fun startObserver() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        observer = object : FileObserver(_file, MODIFY or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                if (event and (MODIFY or DELETE) != 0) {
                    readLogs()
                }
            }
        }

        observer.startWatching()
    }

    /**
     * Stops the FileObserver.
     */
    private fun stopObserver() {
        observer.stopWatching()
    }

    override fun onCleared() {
        super.onCleared()
        stopObserver()
    }

    init {
        readLogs()
        startObserver()
    }

    companion object {
        private val TAG: String = LoggingViewModel::class.java.simpleName
        private const val MAX_LOG_BYTES = 512 * 1024L
    }
}