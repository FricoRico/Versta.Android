package app.versta.translate.ui.component

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLUtils
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.camera.core.SurfaceRequest
import app.versta.translate.bridge.liverender.LiveGlRenderer
import app.versta.translate.core.entity.LiveOverlayTick
import app.versta.translate.utils.CameraFrameTransform
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.Executor

/**
 * GPU camera viewfinder: CameraX's `Preview` writes into an external-OES
 * texture owned here, and every camera frame wakes the GL thread, which
 * renders that frame into the window surface in the same pass the tracker
 * and (later) overlay composite run on. One producer, one pass: the preview
 * and its overlays can never drift apart, which is what makes the overlay
 * read as grounded on the scene.
 */
class VerstaGlSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    /** Sensor mount rotation in 90° quadrants (from
     *  `CameraInfo.sensorRotationDegrees`); updated by the caller. The
     *  sampling map applies its inverse — see [CameraFrameTransform]. */
    @Volatile
    var sensorRotationQuadrant: Int = 1

    /** Per camera frame, called ON the GL thread (before the window swap)
     *  with an upright, uncropped RGBA readback of that same frame
     *  (memory-order R,G,B,A, first row = display top). This is the live-OCR
     *  feed: one producer, one pass, so the tracked pose and the presented
     *  pixels describe the exact same image. Null disables the readback.
     *  The sink runs synchronously and must not outlive its call — the
     *  buffer is reused next frame. Returns the frame's overlay tick:
     *  homography for the same pass's overlay composite, plus a fresh bake
     *  when content moved. */
    @Volatile
    var liveFrameSink: ((buffer: ByteBuffer, width: Int, height: Int) -> LiveOverlayTick?)? = null

    private val reconciliationLock = Any()
    private val directExecutor = Executor { it.run() }
    private var cameraSurface: Surface? = null
    private var pendingRequest: SurfaceRequest? = null
    private var providedRequest: SurfaceRequest? = null

    private var glThread: GlThread? = null

    init {
        holder.addCallback(this)
    }

    fun setSurfaceRequest(request: SurfaceRequest, executor: Executor) {
        synchronized(reconciliationLock) {
            request.addRequestCancellationListener(executor) {
                synchronized(reconciliationLock) {
                    if (pendingRequest === request) pendingRequest = null
                    if (providedRequest === request) providedRequest = null
                }
            }
            val surface = cameraSurface
            if (surface != null) {
                provide(request, surface)
            } else {
                pendingRequest = request
            }
        }
    }

    private fun provide(request: SurfaceRequest, surface: Surface) {
        glThread?.setCameraBufferSize(request.resolution.width, request.resolution.height)

        runCatching {
            request.provideSurface(surface, directExecutor) {
                // Released by the GL thread at teardown.
            }
        }.onFailure { Timber.tag(TAG).w(it, "provideSurface failed") }
        providedRequest = request
        pendingRequest = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        glThread = GlThread(holder.surface).also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        glThread?.resize(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        synchronized(reconciliationLock) {
            cameraSurface?.release()
            cameraSurface = null
            // The surface CameraX holds is dead: invalidate so a fresh
            // SurfaceRequest arrives after surface recreation.
            runCatching { providedRequest?.invalidate() }
            providedRequest = null
            pendingRequest = null
        }
        glThread?.shutdown()
        glThread = null
    }

    private fun publishCameraSurface(surface: Surface) {
        synchronized(reconciliationLock) {
            cameraSurface = surface
            pendingRequest?.let { provide(it, surface) }
        }
    }

    private inner class GlThread(private val windowSurface: Surface) : Thread("versta-gl") {
        @Volatile private var running = true
        @Volatile private var surfaceW = 0
        @Volatile private var surfaceH = 0
        @Volatile private var bufferW = 0
        @Volatile private var bufferH = 0

        private val frameLock = Object()
        private var frameAvailable = false
        private var surfaceTexture: SurfaceTexture? = null

        private var readbackBuf: ByteBuffer? = null
        private var readbackW = 0
        private var readbackH = 0
        private var overlayW = 0
        private var overlayH = 0

        /** Epoch of the last accepted tick; a change wipes the retained
         *  overlay before it can composite (see render loop). */
        private var acceptedEpoch = -1L

        fun resize(w: Int, h: Int) {
            surfaceW = w; surfaceH = h
        }

        fun setCameraBufferSize(w: Int, h: Int) {
            bufferW = w; bufferH = h
            surfaceTexture?.setDefaultBufferSize(w, h)
        }

        fun shutdown() {
            synchronized(frameLock) {
                running = false
                frameLock.notifyAll()
            }
            runCatching { join(500) }
        }

        override fun run() {
            // The composite loop must out-schedule OCR/inference workers, or
            // the camera producer stalls on an unconsumed buffer.
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY)

            var eglDisplay = EGL14.EGL_NO_DISPLAY
            var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
            var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
            var renderer: LiveGlRenderer? = null
            var cameraTexId = 0

            fun teardown() {
                surfaceTexture?.release()
                surfaceTexture = null
                if (cameraTexId != 0) {
                    val ids = IntArray(1) { cameraTexId }
                    GLES20.glDeleteTextures(1, ids, 0)
                    cameraTexId = 0
                }
                renderer?.close()
                renderer = null
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(
                        eglDisplay, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT,
                    )
                    if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
            }

            // ESL context + window surface for this GL thread. False aborts
            // before the camera texture is even allocated.
            fun setupEgl(): Boolean {
                val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (display == EGL14.EGL_NO_DISPLAY) {
                    Timber.tag(TAG).e("eglGetDisplay failed")
                    return false
                }
                eglDisplay = display
                val version = IntArray(2)
                if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                    Timber.tag(TAG).e("eglInitialize failed")
                    return false
                }
                val attribs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 0,
                    EGL14.EGL_NONE,
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)
                val config = configs[0]
                if (numConfigs[0] <= 0 || config == null) {
                    Timber.tag(TAG).e("no EGL config for GLES2 window surface")
                    return false
                }
                eglContext = EGL14.eglCreateContext(
                    eglDisplay, config, EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
                )
                if (eglContext == EGL14.EGL_NO_CONTEXT) {
                    Timber.tag(TAG).e("eglCreateContext failed: %s", GLUtils.getEGLErrorString(EGL14.eglGetError()))
                    return false
                }
                eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, windowSurface, intArrayOf(EGL14.EGL_NONE), 0)
                if (eglSurface == EGL14.EGL_NO_SURFACE ||
                    !EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
                ) {
                    Timber.tag(TAG).e("EGL surface/makeCurrent failed")
                    return false
                }
                return true
            }

            // External-OES texture + its SurfaceTexture the camera writes
            // into; availability wakes the render loop below.
            fun setupCameraTexture(): SurfaceTexture {
                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                cameraTexId = texIds[0]
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE,
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE,
                )
                val st = SurfaceTexture(cameraTexId)
                st.setOnFrameAvailableListener {
                    synchronized(frameLock) {
                        frameAvailable = true
                        frameLock.notifyAll()
                    }
                }
                return st
            }

            if (!setupEgl()) { teardown(); return }
            val st = setupCameraTexture()
            surfaceTexture = st
            publishCameraSurface(Surface(st))

            val r = LiveGlRenderer()
            renderer = r

            while (true) {
                synchronized(frameLock) {
                    while (running && !frameAvailable) {
                        runCatching { frameLock.wait() }
                    }
                    if (!running) break
                    frameAvailable = false
                }

                st.updateTexImage()
                val sw = surfaceW
                val sh = surfaceH
                val bw = bufferW
                val bh = bufferH
                if (sw <= 0 || sh <= 0 || bw <= 0 || bh <= 0) continue

                // Undo the sensor mount rotation: consumer-visibility
                // quadrants are the inverse of the mount quadrants.
                val undoQuadrant = (4 - sensorRotationQuadrant) % 4

                var tick: LiveOverlayTick? = null
                liveFrameSink?.let { sink ->
                    val rbW: Int
                    val rbH: Int
                    if (undoQuadrant % 2 == 1) {
                        rbW = bh; rbH = bw
                    } else {
                        rbW = bw; rbH = bh
                    }
                    val scale = (TRACK_LONG_SIDE.toFloat() / maxOf(rbW, rbH)).coerceAtMost(1f)
                    val outW = (rbW * scale).toInt().coerceAtLeast(2)
                    val outH = (rbH * scale).toInt().coerceAtLeast(2)
                    if (readbackW != outW || readbackH != outH) {
                        readbackBuf = ByteBuffer.allocateDirect(outW * outH * 4)
                        readbackW = outW
                        readbackH = outH
                    }
                    val buf = readbackBuf ?: return@let
                    buf.clear()
                    // Aspect-matched full frame: no display crop applies, so
                    // the analysis image carries all upright content.
                    val uvFull = CameraFrameTransform.displayUvMatrix(
                        bw, bh, outW, outH, undoQuadrant,
                    )
                    if (r.readbackRgba(cameraTexId, outW, outH, uvFull, buf)) {
                        tick = sink(buf, outW, outH)
                    }
                }
                // The GL loop only learns about a session's end from ticks —
                // when the sink detaches (plain viewfinder) no tick arrives,
                // so drop the retained overlay here rather than trusting the
                // next session's first tick to wipe it before a composite.
                if (liveFrameSink == null) {
                    overlayW = 0
                    overlayH = 0
                    acceptedEpoch = -1L
                }

                val uv = CameraFrameTransform.displayUvMatrix(bw, bh, sw, sh, undoQuadrant)
                r.renderCamera(cameraTexId, sw, sh, uv)

                // Overlay composite, same pass as the camera frame it was
                // tracked on: content uploads only when the bake moves, then
                // every frame rewarps that texture by this frame's pose.
                // A tick from a new analysis session invalidates the retained
                // overlay synchronously on this thread — the previous
                // session's bake never composites under this session's
                // homography.
                tick?.let {
                    if (it.epoch != acceptedEpoch) {
                        acceptedEpoch = it.epoch
                        overlayW = 0
                        overlayH = 0
                    }
                }
                tick?.bake?.let { bake ->
                    if (r.setOverlayRgba(bake.width, bake.height, bake.bytes)) {
                        overlayW = bake.width
                        overlayH = bake.height
                    }
                }
                val homography = tick?.homography
                if (homography != null && overlayW > 0 && overlayH > 0) {
                    // Tracker space is the UPRIGHT frame; the camera matrix
                    // would wrongly chain the buffer's sensor rotation in.
                    val contentUv = CameraFrameTransform.contentUvMatrix(bw, bh, sw, sh, undoQuadrant)
                    CameraFrameTransform.overlayUvMatrix(
                        contentUv, readbackW, readbackH, homography, overlayW, overlayH,
                    )?.let { overlayUv ->
                        r.renderOverlay(sw, sh, overlayUv)
                    }
                }
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }

            teardown()
        }
    }

    companion object {
        private val TAG: String = VerstaGlSurfaceView::class.java.simpleName

        /** Long side of the upright analysis readback, matching the capture
         *  target the retired ImageAnalysis stream fed the tracker at. */
        private const val TRACK_LONG_SIDE = 960
    }
}
