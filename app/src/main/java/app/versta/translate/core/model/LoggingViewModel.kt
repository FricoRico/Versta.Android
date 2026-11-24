package app.versta.translate.core.model

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LoggingPreferenceRepository
import app.versta.translate.utils.FileLoggingTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

class LoggingViewModel(
    directory: File?,
    private val loggingPreferenceRepository: LoggingPreferenceRepository,
    private val watcherFactory: (File, () -> Unit) -> LogFileWatcher = ::createLogFileWatcher,
) : ViewModel() {
    private val _directory: File? = directory
    private var _file: File = File(directory, FileLoggingTree.LOG_FILE_NAME)
    private var _backup: File = File(directory, FileLoggingTree.BACKUP_FILE_NAME)
    private var watcher: LogFileWatcher? = null

    private val _logs = MutableStateFlow("")
    val logs: StateFlow<String> = _logs.asStateFlow()

    val loggingEnabled = loggingPreferenceRepository.isLoggingEnabled().distinctUntilChanged()

    /**
     * Enables or disables file logging for release builds; debug builds log
     * to a file regardless of this setting.
     */
    fun setLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            loggingPreferenceRepository.setLoggingEnabled(enabled)
        }
    }

    /**
     * Gets the content of the log file.
     */
    private fun readLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_file.exists()) {
                _logs.value = ""
                return@launch
            }

            // The writer caps the file, but only the tail is shown; decoding
            // the whole file on every write still wastes the heap during
            // long camera sessions.
            val length = _file.length()
            val start = (length - MAX_LOG_BYTES).coerceAtLeast(0L)
            val bytes =
                FileInputStream(_file).use { input ->
                    input.skip(start)
                    input.readBytes()
                }
            _logs.value =
                if (start > 0) {
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
    fun saveLogs(
        context: Context,
        uri: Uri,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    // Two capped generations exist after rotation; export the
                    // oldest first so the result stays chronological.
                    listOf(_backup, _file).forEach { file ->
                        if (!file.exists()) return@forEach
                        FileInputStream(file).use { input ->
                            input.copyTo(os)
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
            _backup.delete()
            readLogs()
        }
    }

    /**
     * Starts watching the log directory for changes to the log file.
     */
    private fun startObserver() {
        val directory = _directory ?: return

        watcher = watcherFactory(directory) { readLogs() }
        watcher?.startWatching()
    }

    /**
     * Stops the log file watcher.
     */
    private fun stopObserver() {
        watcher?.stopWatching()
        watcher = null
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

interface LogFileWatcher {
    fun startWatching()

    fun stopWatching()
}

/**
 * Watches the log DIRECTORY, not the file itself: FileLoggingTree creates
 * app.log lazily on the first message and the Logging screen's Clear deletes
 * it, so a file-targeted observer goes permanently silent the moment the
 * file is (re)created — which is exactly the release flow (toggle off at
 * first launch, no file yet, toggle on).
 */
internal fun createLogFileWatcher(
    directory: File,
    onChanged: () -> Unit,
): LogFileWatcher {
    val mask = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE or FileObserver.MOVED_TO

    val observer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(directory, mask) {
                override fun onEvent(
                    event: Int,
                    path: String?,
                ) {
                    onLogFileEvent(event, path, onChanged)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(
                    event: Int,
                    path: String?,
                ) {
                    onLogFileEvent(event, path, onChanged)
                }
            }
        }

    return object : LogFileWatcher {
        override fun startWatching() = observer.startWatching()

        override fun stopWatching() = observer.stopWatching()
    }
}

private fun onLogFileEvent(
    event: Int,
    path: String?,
    onChanged: () -> Unit,
) {
    if (path != FileLoggingTree.LOG_FILE_NAME) return

    val relevant = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.DELETE or FileObserver.MOVED_TO
    if (event and relevant != 0) {
        onChanged()
    }
}
