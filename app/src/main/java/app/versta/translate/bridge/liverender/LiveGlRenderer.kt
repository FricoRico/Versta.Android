package app.versta.translate.bridge.liverender

import timber.log.Timber
import java.nio.ByteBuffer

/**
 * GLES2 renderer for the live camera surface: per camera frame the GL thread
 * calls [renderCamera] with the frame's external-OES texture and the
 * display→buffer UV matrix. The renderer must only be used on the thread
 * holding the current EGL context.
 */
class LiveGlRenderer : AutoCloseable {
    private var _handle: Long = construct()

    private external fun construct(): Long
    private external fun destroy(handle: Long)
    private external fun renderCameraNative(
        handle: Long,
        textureId: Int,
        surfaceWidth: Int,
        surfaceHeight: Int,
        uvMatrix: FloatArray,
    ): Boolean

    private external fun readbackRgbaNative(
        handle: Long,
        textureId: Int,
        outWidth: Int,
        outHeight: Int,
        uvMatrix: FloatArray,
        out: ByteBuffer,
    ): Boolean

    private external fun setOverlayRgbaNative(
        handle: Long,
        width: Int,
        height: Int,
        rgba: ByteArray,
    ): Boolean

    private external fun renderOverlayNative(
        handle: Long,
        surfaceWidth: Int,
        surfaceHeight: Int,
        uvMatrix: FloatArray,
    ): Boolean


    /**
     * Draws the camera frame fullscreen. [uvMatrix] is a row-major 3×3 over
     * display-normalized top-left-origin coordinates (see
     * `CameraFrameTransform.displayUvMatrix`).
     */
    fun renderCamera(textureId: Int, surfaceWidth: Int, surfaceHeight: Int, uvMatrix: FloatArray): Boolean {
        check(_handle != 0L) { "LiveGlRenderer is closed" }
        check(uvMatrix.size == 9) { "uvMatrix must hold 9 floats" }
        return renderCameraNative(_handle, textureId, surfaceWidth, surfaceHeight, uvMatrix)
    }

    /**
     * Renders the camera frame's full upright content offscreen and reads it
     * back into [out] (must be a direct buffer of ≥ outWidth×outHeight×4
     * bytes, position 0): memory-order R,G,B,A, first row = display top —
     * the layout the OCR engine's live pipeline consumes with rotation 0.
     */
    fun readbackRgba(textureId: Int, outWidth: Int, outHeight: Int, uvMatrix: FloatArray, out: ByteBuffer): Boolean {
        check(_handle != 0L) { "LiveGlRenderer is closed" }
        check(uvMatrix.size == 9) { "uvMatrix must hold 9 floats" }
        check(out.isDirect) { "readback target must be a direct ByteBuffer" }
        return readbackRgbaNative(_handle, textureId, outWidth, outHeight, uvMatrix, out)
    }

    /**
     * (Re)uploads the baked overlay content (premultiplied memory-order
     * R,G,B,A over the full canonical frame). Content-change cadence only —
     * never per frame.
     */
    fun setOverlayRgba(width: Int, height: Int, rgba: ByteArray): Boolean {
        check(_handle != 0L) { "LiveGlRenderer is closed" }
        check(rgba.size >= width * height * 4) { "overlay payload too small" }
        return setOverlayRgbaNative(_handle, width, height, rgba)
    }

    /**
     * Composites the uploaded overlay over the camera pass. [uvMatrix] is the
     * frame's overlay UV (display → canonical texels, genuinely projective —
     * see `CameraFrameTransform.overlayUvMatrix`). False without an upload.
     */
    fun renderOverlay(surfaceWidth: Int, surfaceHeight: Int, uvMatrix: FloatArray): Boolean {
        check(_handle != 0L) { "LiveGlRenderer is closed" }
        check(uvMatrix.size == 9) { "uvMatrix must hold 9 floats" }
        return renderOverlayNative(_handle, surfaceWidth, surfaceHeight, uvMatrix)
    }

    override fun close() {
        if (_handle == 0L) {
            Timber.tag(TAG).w("LiveGlRenderer is already closed")
            return
        }
        destroy(_handle)
        _handle = 0L
    }

    companion object {
        private val TAG: String = LiveGlRenderer::class.java.simpleName

        init {
            System.loadLibrary("app_versta_translate_bridge")
        }
    }
}
