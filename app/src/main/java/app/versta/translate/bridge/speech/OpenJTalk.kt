package app.versta.translate.bridge.speech

import android.content.Context
import timber.log.Timber
import kotlin.io.path.pathString

const val OPEN_JTALK_DATA_DIR = "open-jtalk-data"

class OpenJTalk(context: Context) : AutoCloseable {
    private var _handle: Long

    init {
        _handle = construct(
            context.filesDir.resolve(EXTERNAL_DATA_DIR).resolve(OPEN_JTALK_DATA_DIR)
                .toPath().pathString
        )

        if (_handle == 0L) {
            throw RuntimeException("Failed to initialize BeamSearch")
        }
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        close(_handle)
        _handle = 0L
    }

    fun phonemize(text: String): String {
        if (_handle == 0L) {
            throw IllegalStateException("Not initialized")
        }

        return phonemize(_handle, text)
    }

    private external fun construct(path: String): Long
    private external fun phonemize(handle: Long, text: String): String
    private external fun close(handle: Long): Boolean

    companion object {
        private val TAG: String = OpenJTalk::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}