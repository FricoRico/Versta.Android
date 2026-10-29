package app.versta.translate.utils

import android.util.Log
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale.getDefault

/**
 * Persists Timber output to `app.log` in the app's external files directory.
 *
 * The writer stays open across lines and rotates to a single backup generation
 * once the file exceeds [maxBytes], so the log never grows past two capped
 * files. Failures disable the tree for the rest of the process and are never
 * reported back through Timber — logging the failure would re-enter this tree
 * and overflow the stack on a broken or full disk.
 */
class FileLoggingTree(
    directory: File?,
    private val maxBytes: Long = MAX_LOG_BYTES,
) : Timber.Tree(),
    AutoCloseable {
    private val logFile = directory?.let { File(it, LOG_FILE_NAME) }
    private val backupFile = directory?.let { File(it, BACKUP_FILE_NAME) }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", getDefault())

    private var writer: BufferedWriter? = null
    private var bytes = 0L
    private var disabled = directory == null

    override fun log(
        priority: Int,
        tag: String?,
        message: String,
        t: Throwable?,
    ) {
        synchronized(this) {
            if (disabled) return

            val line =
                String.format(
                    getDefault(),
                    "%s %s/%s: %s\n",
                    dateFormat.format(Date()),
                    logLevel(priority),
                    tag,
                    message,
                )

            writeLine(line)
        }
    }

    private fun writeLine(line: String) {
        val file = logFile ?: return

        val ready =
            when {
                writer == null -> openWriter()
                isStale(file) -> reopenWriter()
                else -> true
            }
        if (!ready) return

        val lineBytes = line.toByteArray(Charsets.UTF_8).size.toLong()

        if (bytes > 0 && bytes + lineBytes > maxBytes) {
            rotate()
        }

        if (!disabled) {
            try {
                writer?.append(line)
                writer?.flush()
                bytes += lineBytes
            } catch (_: IOException) {
                // Never reported through Timber: that re-enters this tree and
                // recurses until the stack overflows on a full/broken disk.
                disable()
            }
        }
    }

    // The Logging screen can clear the file behind the open writer; a vanished
    // or resized file means the counter is stale and writes would land in an
    // unlinked handle.
    private fun isStale(file: File): Boolean = file.length() != bytes || (bytes == 0L && !file.exists())

    override fun close() {
        synchronized(this) {
            disable()
        }
    }

    private fun openWriter(append: Boolean = true): Boolean {
        val file = logFile ?: return false

        return try {
            writer =
                BufferedWriter(
                    OutputStreamWriter(FileOutputStream(file, append), Charsets.UTF_8),
                )
            bytes = if (append) file.length() else 0L
            true
        } catch (_: IOException) {
            disable()
            false
        }
    }

    private fun reopenWriter(): Boolean {
        closeWriter()
        return openWriter(append = false)
    }

    private fun rotate() {
        closeWriter()

        val file = logFile ?: return
        val backup = backupFile ?: return

        if (backup.exists()) backup.delete()

        // Rename replaces the backup atomically on POSIX; on failure the old
        // file is truncated below, keeping the log bounded either way.
        file.renameTo(backup)

        openWriter(append = false)
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (_: IOException) {
            // A close failure on a broken target leaves nothing useful behind;
            // never reported through Timber (recursion hazard, see log()).
        }
        writer = null
    }

    private fun disable() {
        disabled = true
        closeWriter()
    }

    private fun logLevel(priority: Int): String =
        when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }

    companion object {
        const val LOG_FILE_NAME = "app.log"
        const val BACKUP_FILE_NAME = "app.log.1"
        const val MAX_LOG_BYTES = 1024L * 1024L

        /**
         * File logging runs in debug builds; release builds only write logs to
         * disk while the diagnostics toggle on the Logging screen is enabled.
         */
        fun shouldEnableFileLogging(
            isDebugBuild: Boolean,
            diagnosticsEnabled: Boolean,
        ): Boolean = isDebugBuild || diagnosticsEnabled
    }
}
