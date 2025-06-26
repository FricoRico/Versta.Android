package app.versta.translate.bridge.speech

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.pathString

class OpenJTalk() : AutoCloseable {
    private var _handle: MutableStateFlow<Long?> = MutableStateFlow(null)

    fun load(data: Path) {
        if (_handle.value != null) {
            close()
        }

        _handle.value = construct(data.pathString)
    }

    fun isReady(): Boolean {
        return _handle.value != null
    }

    fun isReadyStateFlow(): Flow<Boolean> {
        return _handle.map { it != null }
    }

    override fun close() {
        if (_handle.value == null) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        close(_handle.value!!)
        _handle.value = null
    }

    fun phonemize(text: String): String {
        if (_handle.value == null) {
            throw IllegalStateException("Not initialized")
        }

        return phonemize(_handle.value!!, text)
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