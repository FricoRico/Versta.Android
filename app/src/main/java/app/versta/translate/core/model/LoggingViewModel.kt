package app.versta.translate.core.model

import android.content.Context
import android.net.Uri
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

            _logs.value = _file.readText()
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
    }
}