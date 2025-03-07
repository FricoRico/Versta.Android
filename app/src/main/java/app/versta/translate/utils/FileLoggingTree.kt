package app.versta.translate.utils

import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale.getDefault

class FileLoggingTree(directory: File?) : Timber.Tree() {
    private val logFile = File(directory, "app.log")

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val logMessage: String = java.lang.String.format(
                getDefault(),
                "%s %s/%s: %s\n",
                DATE_FORMAT.format(Date()),
                logLevel(priority),
                tag,
                message
            )

            synchronized(this) {
                val writer = FileWriter(logFile, true)
                writer.append(logMessage)
                writer.close()
            }
        } catch (e: IOException) {
            Timber.tag(TAG).e(e, "Error writing to log file")
        }
    }

    private fun logLevel(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> "?"
        }
    }

    companion object {
        private val TAG: String = FileLoggingTree::class.java.simpleName
        private val DATE_FORMAT: SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", getDefault())
    }
}