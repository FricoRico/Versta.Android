package app.versta.translate.bridge.whisper

import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Owns a native whisper.cpp context plus an optional Silero VAD context.
 * Loads the model in [init] and releases it with [close].
 */
class WhisperModel(
    modelPath: Path,
    vadModelPath: Path,
    nThreads: Int,
) : AutoCloseable {
    @Volatile
    private var handle: Long

    init {
        handle = create(modelPath.pathString, vadModelPath.pathString, nThreads)

        if (handle == 0L) {
            throw RuntimeException("Failed to load whisper model from $modelPath")
        }
    }

    fun getHandle(): Long {
        if (handle == 0L) {
            throw RuntimeException("Whisper model is not loaded")
        }
        return handle
    }

    override fun close() {
        if (handle == 0L) {
            Timber.tag(TAG).w("Already closed")
            return
        }

        destroy(handle)
        handle = 0L
    }

    private external fun create(modelPath: String, vadModelPath: String, nThreads: Int): Long

    private external fun destroy(handle: Long)

    companion object {
        private val TAG: String = WhisperModel::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
