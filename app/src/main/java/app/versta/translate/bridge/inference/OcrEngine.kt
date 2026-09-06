package app.versta.translate.bridge.inference

import app.versta.translate.core.entity.OcrDetectedLine
import timber.log.Timber
import java.nio.Buffer

/**
 * OCR engine over the native PP-OCR pipeline (MNN runtime): detection,
 * orientation resolution, dewarping, script routing, recognition and glyph
 * metrics. All models live in one bundle directory of modules.
 */
class OcrEngine : AutoCloseable {
    private var _handle: Long

    init {
        _handle = construct()
        if (_handle == 0L) {
            throw RuntimeException("Failed to initialize OcrEngine")
        }
    }

    private external fun construct(): Long
    private external fun close(handle: Long): Boolean

    private external fun setDetector(handle: Long, modelPath: String, threads: Int): Boolean

    /**
     * @param routes One recognizer key per PULC script class for auto routing
     * (order: arabic, chinese, cyrillic, devanagari, japanese, kannada, korean,
     * tamil, telugu, latin); empty string = no recognizer for that class.
     */
    private external fun setScriptClassifier(handle: Long, modelPath: String, routes: Array<String>): Boolean

    private external fun setGlyphMatte(handle: Long, modelPath: String): Boolean

    private external fun setAligner(handle: Long, modelPath: String): Boolean

    /**
     * Registers a recognizer under [key] (its module directory name). The
     * model is loaded lazily on first use.
     */
    private external fun addRecognizer(handle: Long, key: String, modelPath: String, vocabPath: String): Boolean

    private external fun analyze(
        handle: Long,
        input: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        rotationDegrees: Int,
        profile: Int,
        forcedRecognizer: String?
    ): Array<OcrDetectedLine>?

    /**
     * One presenter tick against the live anchor. Returns 11 floats:
     * 9 canonical→frame homography + anchor epoch + content version — line
     * CONTENT never crosses here. Null when anchorless.
     */
    private external fun tick(
        handle: Long,
        input: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        rotationDegrees: Int
    ): FloatArray?

    /**
     * The last emitted live overlays (marshaled with strip pixels only when
     * their epoch built them). Call only when tick reports a version move.
     */
    private external fun liveContent(handle: Long): Array<OcrDetectedLine>?

    fun setDetector(modelPath: String, threads: Int): Boolean = setDetector(_handle, modelPath, threads)

    fun setScriptClassifier(modelPath: String, routes: Array<String>): Boolean =
        setScriptClassifier(_handle, modelPath, routes)

    fun setGlyphMatte(modelPath: String): Boolean = setGlyphMatte(_handle, modelPath)

    fun setAligner(modelPath: String): Boolean = setAligner(_handle, modelPath)

    fun addRecognizer(key: String, modelPath: String, vocabPath: String): Boolean =
        addRecognizer(_handle, key, modelPath, vocabPath)

    /**
     * Runs the full pipeline over one RGBA frame.
     *
     * @param rotationDegrees Display rotation of the frame as reported by CameraX.
     * @param profile 0 = still (max quality), 1 = live (noise-strict).
     * @param forcedRecognizer Module key to force all lines to (skips script
     * classification); null/empty = auto.
     */
    fun analyze(
        input: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        rotationDegrees: Int,
        profile: Int,
        forcedRecognizer: String?
    ): Array<OcrDetectedLine>? = analyze(_handle, input, inputWidth, inputHeight, rotationDegrees, profile, forcedRecognizer)

    fun tick(
        input: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        rotationDegrees: Int = 0
    ): FloatArray? {
        if (_handle == 0L) return null
        return tick(_handle, input, inputWidth, inputHeight, rotationDegrees)
    }

    fun liveContent(): Array<OcrDetectedLine>? {
        if (_handle == 0L) return null
        return liveContent(_handle)
    }

    /**
     * Feeds the stillness gate with one anchorless frame (gray + diff only,
     * ~ms; try-locks the engine — false when the acquire worker holds it).
     */
    private external fun probeLive(
        handle: Long,
        input: Buffer,
        inputWidth: Int,
        inputHeight: Int,
        rotationDegrees: Int,
    ): Boolean

    fun probeLive(input: Buffer, inputWidth: Int, inputHeight: Int): Boolean {
        if (_handle == 0L) return false
        return probeLive(_handle, input, inputWidth, inputHeight, 0)
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("OcrEngine is already closed")
            return
        }

        close(_handle)
        _handle = 0L
    }

    companion object {
        private val TAG: String = OcrEngine::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
