package app.versta.translate.core.model

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Size
import android.view.View
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.ExtendableBuilder
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionAnalyzer
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionInference
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionRepository
import app.versta.translate.adapter.outbound.mapOcrLineResult
import app.versta.translate.core.entity.BakedOverlay
import app.versta.translate.core.entity.CameraTranslationBlockLine
import app.versta.translate.core.entity.CameraTranslationResult
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.core.entity.LiveOverlayTick
import app.versta.translate.core.entity.OcrRenderStrip

import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import app.versta.translate.core.entity.Language
import app.versta.translate.utils.CameraFrameTransform

class CameraTranslationViewModel(
    private val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference,
    private val translationViewModel: TranslationViewModel,
    private val objectCharacterRecognizerRepository: ObjectCharacterRecognitionRepository,
    private val languageViewModel: LanguageViewModel,
    private val languagePreferenceRepository: LanguagePreferenceRepository
) : ViewModel() {
    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequests: StateFlow<SurfaceRequest?> = _surfaceRequests.asStateFlow()

    private val _viewfinderMode = MutableStateFlow(false)
    val viewfinderMode: StateFlow<Boolean> = _viewfinderMode.asStateFlow()

    /** Sensor→display upright rotation in 90° quadrants for the GL viewfinder. */
    private val _sensorRotationQuadrant = MutableStateFlow(1)
    val sensorRotationQuadrant: StateFlow<Int> = _sensorRotationQuadrant.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating.asStateFlow()

    private val _minZoomRatio = MutableStateFlow(1f)
    val minZoomRatio: StateFlow<Float> = _minZoomRatio.asStateFlow()

    private val _maxZoomRatio = MutableStateFlow(1f)
    val maxZoomRatio: StateFlow<Float> = _maxZoomRatio.asStateFlow()

    private val _currentZoomRatio = MutableStateFlow(1f)
    val currentZoomRatio: StateFlow<Float> = _currentZoomRatio.asStateFlow()



    private var _camera: Camera? = null
    private var _cameraProvider: ProcessCameraProvider? = null

    // Preview stays at a modest target: the GL loop reads an upright
    // full-frame copy of the very buffer it presents back for the tracker,
    // so analyzer-frame points and the viewfinder always describe the same
    // sensor crop (the view's overlay mapping is built on that).
    private val _cameraResolutionSelector = ResolutionSelector.Builder().apply {
        setResolutionStrategy(
            ResolutionStrategy(
                Size(960, 960),
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
            )
        )
    }.build()

    // Use cases are built in initializeCamera: video stabilization support is
    // camera-dependent and only knowable once the provider exists. Stabilized
    // input frames shrink the per-frame pose deltas the tracker sees.
    private lateinit var _cameraPreviewUseCase: Preview
    private lateinit var _imageCaptureUseCase: ImageCapture

    /**
     * Legacy [CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON] on the shared
     * capture request: Camera2 applies one stabilization transform per request
     * to ALL output streams. The newer preview-crop mode (CameraX
     * setPreviewStabilizationEnabled / PREVIEW_STABILIZATION) deliberately
     * stabilizes only the preview output — and since our analysis rides the
     * preview surface itself, plain per-request stabilization keeps the
     * tracked overlay glued to a stabilized viewfinder.
     */
    private fun stabilizationSupported(cameraProvider: ProcessCameraProvider): Boolean {
        val info = runCatching {
            CameraSelector.DEFAULT_BACK_CAMERA
                .filter(cameraProvider.availableCameraInfos)
                .firstOrNull()
        }.getOrNull() ?: return false
        val modes = runCatching {
            Camera2CameraInfo.from(info).getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES
            )
        }.getOrNull() ?: return false
        return modes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
    }

    private fun <B : ExtendableBuilder<*>> stabilized(builder: B, enabled: Boolean): B {
        if (enabled) {
            Camera2Interop.Extender(builder).setCaptureRequestOption(
                CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON,
            )
        }
        return builder
    }

    private fun buildUseCases(stabilize: Boolean) {
        _cameraPreviewUseCase = stabilized(
            Preview.Builder().setResolutionSelector(_cameraResolutionSelector), stabilize
        ).build().apply {
            setSurfaceProvider { newSurfaceRequest ->
                _surfaceRequests.update { newSurfaceRequest }
            }
        }
        _imageCaptureUseCase = stabilized(
            ImageCapture.Builder().setResolutionSelector(_cameraResolutionSelector), stabilize
        ).build()
    }

    private val _language = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()

    private val _bundleData = objectCharacterRecognizerRepository.getModules()
        .distinctUntilChanged()
        .map { objectCharacterRecognizerRepository.getCompleteBundle() }

    private val _forcedRecognizer = _language.map { language ->
        if (language !is Language) {
            return@map null
        }

        objectCharacterRecognizerRepository.getRecognizerForLanguage(language = language)
            ?.path?.fileName?.toString()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Hot-path overlay state is Compose snapshot state written directly from
    // the GL viewfinder thread: publishing through MutableStateFlow + a
    // coroutine hop costs one to two display frames of lag under motion, which
    // reads as the crops trailing the viewfinder ("not grounded").
    var frameWidth by mutableIntStateOf(0)
        private set
    var frameHeight by mutableIntStateOf(0)
        private set
    var detectedBoxes by mutableStateOf<List<CameraTranslationResult>>(emptyList())
        private set

    /** Deliberately async of the geometry: quads present instantly on each
     *  tracked frame while misses resolve on a background job, then the next
     *  publish picks the text up. */
    var blockTranslations by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private val _stillBusy = MutableStateFlow(false)
    val stillBusy: StateFlow<Boolean> = _stillBusy.asStateFlow()

    private val _loadMutex = Mutex()

    /** Eager cache of the active language pair for the analyzer hot path —
     *  the previous [first] suspension hit DataStore plumbing per frame. */
    private val _languagePairCache = languageViewModel.languagePair
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _objectCharacterRecognitionAnalyzer = ObjectCharacterRecognitionAnalyzer(
        objectCharacterRecognitionInference = objectCharacterRecognitionInference,
        scope = viewModelScope,
        forcedRecognizer = { _forcedRecognizer.value },
        onFrameProcessed = { objects, width, height ->
            // Live-lane publish gate: frames whose sink entry predates the
            // last viewfinder()/capture() epoch bump are stale by definition.
            if (_sinkEpoch == _analysisEpoch) {
                onAnalyzerFrame(objects, width, height)
            }
        }
    )

    /** Sources with a translation job in flight; failures remove the entry so
     *  the next frame retries. */
    private val _inFlightTranslations = ConcurrentHashMap.newKeySet<String>()

    /** Erased-background patches: pixels arrive on acquires, corners re-pose per frame. */
    private val _stripStore = OcrStripStore()

    /** Rasters the live overlay into canonical space (content-change only). */
    private val _overlayBaker = OcrOverlayBaker()

    /** Bumped when the overlay's CONTENT changes (fresh strips epoch or a new
     *  translation) — never per tracker tick. Read on the GL thread, also
     *  written asynchronously by translation completion. */
    @Volatile
    private var _contentVersion = 0
    private var _bakedVersion = -1

    /** Last live publish in block form, the baker's input (GL thread only). */
    private var _lastLiveBlocks: List<CameraTranslationResult> = emptyList()

    /** Bumped on every capture/viewfinder transition: live frames already in
     *  flight when the user resets the overlay die instead of re-populating
     *  the just-cleared boxes (a post-reset publish is what painted the
     *  frozen stills-lane overlay over the fresh viewfinder). */
    @Volatile
    private var _analysisEpoch = 0L

    /** Epoch the running GL sink frame entered with; compared against
     *  [_analysisEpoch] in the analyzer's publish callback. */
    @Volatile
    private var _sinkEpoch = 0L

    /** Session-scoped presentation gate: the overlay composites only once
     *  the tracker has held its anchor for [AnchorWarmupGate.DEFAULT_WARMUP_TICKS]
     *  ticks (reset on every session transition). */
    private val _warmupGate = AnchorWarmupGate()

    /**
     * Presents one analyzed frame. Runs ON the GL viewfinder thread —
     * synchronously, so the pose hits Compose state in the same frame it was
     * computed; everything heavy (fresh MT) is fanned out asynchronously.
     * Synchronized against [takeStill], which presents on a UI coroutine.
     */
    @Synchronized
    private fun onAnalyzerFrame(
        objects: List<ObjectCharacterRecogniserResult>,
        width: Int,
        height: Int,
    ) {
        val languages = _languagePairCache.value ?: return

        // Paragraph blocks translate as one unit (joined in the engine's
        // reading order); the renderer wraps the result back across the
        // block's line quads. Confidence gates the BLOCK (mean line score) —
        // a per-line gate strips weak lines out of a good paragraph, and
        // their quads are exactly what the wrap needs.
        // A fresh acquire's pixels invalidate every cached patch once per
        // frame (block ids only hold within one anchor) and are new overlay
        // content → rebake.
        if (objects.any { it.strip?.bytes != null }) {
            _stripStore.reset()
            _contentVersion++
        }
        val results = objects.filter {
            it.text.isNotBlank()
        }.groupBy {
            it.blockId
        }.filterValues { lines ->
            lines.map { it.score }.average() >= BLOCK_SCORE_MIN
        }.map { (blockId, lines) ->
            val source = lines.joinToString(" ") { it.text }
            requestTranslation(source, languages)

            val strips = _stripStore.accept(blockId, lines)
            CameraTranslationResult(
                source = source,
                lines = lines.mapIndexed { index, line ->
                    CameraTranslationBlockLine(
                        points = line.points,
                        colors = line.colors,
                        fontWeight = line.fontWeight,
                        strip = strips[index],
                    )
                },
            )
        }

        frameWidth = width
        frameHeight = height
        detectedBoxes = results
        _lastLiveBlocks = results
        _translating.value = false
    }

    private fun requestTranslation(source: String, languages: LanguagePair) {
        if (blockTranslations.containsKey(source)) return
        if (!_inFlightTranslations.add(source)) return
        val epoch = _analysisEpoch
        viewModelScope.launch(Dispatchers.Default) {
            val translated = runCatching {
                translationViewModel.translate(source, languages)
            }.getOrNull()
            _inFlightTranslations.remove(source)
            // Started in an ended session? The reset already cleared the
            // overlay content — this result belongs to the old scene.
            if (translated != null && epoch == _analysisEpoch) {
                blockTranslations = boundedTranslations(blockTranslations + (source to translated))
                // New text = new overlay content; the next live frame rebakes.
                _contentVersion++
            }
        }
    }

    private fun boundedTranslations(map: Map<String, String>): Map<String, String> {
        if (map.size <= CACHE_MAX) return map
        return LinkedHashMap(map).apply { remove(keys.first()) }
    }

    /**
     * Captures one photo, runs the stills pipeline (docaligner rectification +
     * full typography) and presents the lines over the frozen frame.
     */
    suspend fun takeStill(appContext: Context) {
        if (_stillBusy.value) return
        _stillBusy.value = true

        try {
            val capture = suspendCoroutine<Pair<Bitmap, Int>?> { cont ->
                _imageCaptureUseCase.takePicture(
                    ContextCompat.getMainExecutor(appContext),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val rotation = image.imageInfo.rotationDegrees
                            val bitmap = image.toBitmap()
                            image.close()
                            cont.resume(bitmap to rotation)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Timber.tag(TAG).e(exception, "Still capture failed")
                            cont.resume(null)
                        }
                    }
                )
            } ?: return

            val (bitmap, rotation) = capture
            val buffer = ByteBuffer.allocateDirect(bitmap.width * bitmap.height * 4)
            bitmap.copyPixelsToBuffer(buffer)
            buffer.rewind()

            val result = try {
                objectCharacterRecognitionInference.analyzeStill(
                    buffer, bitmap.width, bitmap.height, rotation, _forcedRecognizer.value
                )
            } catch (e: IllegalStateException) {
                // Engine still loading (models detected asynchronously after the
                // camera opens); abort the still instead of crashing.
                Timber.tag(TAG).e(e, "Still capture aborted: OCR engine not loaded")
                return
            }

            val mapped = result.lines.map { line ->
                mapOcrLineResult(line)
            }

            // Pause live analysis while the still result is presented; the user
            // exits back to the viewfinder with the restart button.
            _liveTrackingActive.value = false
            _translating.value = false
            onAnalyzerFrame(mapped, result.width, result.height)
        } finally {
            _stillBusy.value = false
        }
    }

    fun torch(value: Boolean) {
        _torchEnabled.value = value
        _camera?.cameraControl?.enableTorch(value)
    }

    private fun initZoomState() {
        val zoomState = _camera?.cameraInfo?.zoomState?.value
        val min = zoomState?.minZoomRatio ?: 1f
        val max = zoomState?.maxZoomRatio ?: 1f
        val current = zoomState?.zoomRatio ?: 1f
        _minZoomRatio.value = min
        _maxZoomRatio.value = max
        _currentZoomRatio.value = current
    }

    private fun initializeCamera(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner
    ): Camera? {
        cameraProvider.unbindAll() // Only unbind once during initial setup

        // ON video stabilization, guarded + fallback: steadies the analysis
        // stream so handheld pose deltas the tracker absorbs stay small.
        var stabilize = stabilizationSupported(cameraProvider)
        var lastError: Exception? = null
        var attempt = 0
        while (attempt < 2) {
            attempt++
            buildUseCases(stabilize)
            try {
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(_cameraPreviewUseCase)
                    .addUseCase(_imageCaptureUseCase)
                    .build()

                _camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner = lifecycleOwner,
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                    useCaseGroup = useCaseGroup
                )
                Timber.tag(TAG).i(
                    "Camera bound (video stabilization %s)",
                    if (stabilize) "on" else "unavailable"
                )
                _sensorRotationQuadrant.value =
                    _camera?.cameraInfo?.sensorRotationDegrees?.let {
                        ((it / 90) % 4 + 4) % 4
                    } ?: 1
                return _camera
            } catch (e: Exception) {
                lastError = e
                if (!stabilize) break
                Timber.tag(TAG).e(e, "Bind with video stabilization failed; retrying without")
                stabilize = false
            }
        }
        Timber.tag(TAG).e(lastError, "Error binding to camera lifecycle")
        return null
    }

    /** Live tracking on/off: the GL viewfinder forwards each presented
     *  camera frame's upright readback to the analyzer only while set. */
    private val _liveTrackingActive = MutableStateFlow(false)
    val liveTrackingActive: StateFlow<Boolean> = _liveTrackingActive.asStateFlow()

    /**
     * Entry point for the GL viewfinder's per-frame readback: runs ON the GL
     * thread, synchronously, so the tracked pose is computed from the very
     * frame the preview presents (no cross-use-case offset).
     *
     * Returns the frame's overlay tick: the canonical→frame homography the GL
     * pass composites with, and — only when content moved (fresh strips epoch
     * or newly-resolved translations) — a fresh canonical-space bake.
     */
    fun onLiveGlFrame(buffer: ByteBuffer, width: Int, height: Int): LiveOverlayTick? {
        if (!_liveTrackingActive.value) return null
        val epoch = _analysisEpoch
        _sinkEpoch = epoch
        val homography = _objectCharacterRecognitionAnalyzer.process(buffer, width, height)
        if (epoch != _analysisEpoch) return null
        if (!_warmupGate.admit(homography != null)) return LiveOverlayTick(null, null, epoch)
        var bake: BakedOverlay? = null
        if (homography != null && _bakedVersion < _contentVersion && _lastLiveBlocks.isNotEmpty()) {
            val startNs = SystemClock.elapsedRealtimeNanos()
            val inverse = CameraFrameTransform.invert3(homography)
            if (inverse != null) {
                bake = _overlayBaker.bake(
                    blocks = _lastLiveBlocks,
                    translations = blockTranslations,
                    inverseHomography = inverse,
                    frameWidth = width,
                    frameHeight = height,
                )
                _bakedVersion = _contentVersion
                Timber.tag(TAG).d(
                    "OCR bake: v%d, %.1f ms, %d bytes",
                    _contentVersion,
                    (SystemClock.elapsedRealtimeNanos() - startNs) / 1e6f,
                    bake?.bytes?.size ?: 0,
                )
            }
        }
        return LiveOverlayTick(homography, bake, epoch)
    }

    /** Every piece of overlay content keyed to the ended session: translated
     *  text, in-flight MT jobs, erased strips, the bake. Resetting on the
     *  transition keeps the next session's first frames from presenting the
     *  previous scene's translation. */
    private fun clearOverlayContent() {
        blockTranslations = emptyMap()
        _inFlightTranslations.clear()
        _stripStore.reset()
        _bakedVersion = -1
        _contentVersion++
    }

    /**
     * One transition body for both ends of the live toggle. The fence
     * ordering is load-bearing (settled in the overlay-staleness hunt):
     * disabling stops the GL frame sink FIRST so in-flight frames die at the
     * epoch gate before state clears; enabling publishes the sink LAST. The
     * rebind differs per side — viewfinder always re-binds the use cases,
     * capture keeps an existing binding.
     *
     * @param toLive true = capture (live translation), false = viewfinder.
     */
    private suspend fun transitionCameraState(
        appContext: Context,
        lifecycleOwner: LifecycleOwner,
        toLive: Boolean
    ) {
        if (!toLive) {
            _liveTrackingActive.value = false
            _translating.value = false
        }
        _analysisEpoch++
        _warmupGate.reset()
        detectedBoxes = emptyList()
        _lastLiveBlocks = emptyList()
        clearOverlayContent()
        _objectCharacterRecognitionAnalyzer.reset()
        _cameraProvider = ProcessCameraProvider.Companion.awaitInstance(appContext)
        _viewfinderMode.value = !toLive

        if (_camera == null || !toLive) {
            initializeCamera(_cameraProvider!!, lifecycleOwner)
            initZoomState()
        }

        if (toLive) {
            _liveTrackingActive.value = true
            _translating.value = true
        }
    }

    suspend fun capture(appContext: Context, lifecycleOwner: LifecycleOwner) =
        transitionCameraState(appContext, lifecycleOwner, toLive = true)

    suspend fun viewfinder(appContext: Context, lifecycleOwner: LifecycleOwner) =
        transitionCameraState(appContext, lifecycleOwner, toLive = false)

    fun applyZoomDelta(zoomFactor: Float) {
        val current = _currentZoomRatio.value
        val newRatio = (current * zoomFactor).coerceIn(_minZoomRatio.value, _maxZoomRatio.value)
        setZoomRatio(newRatio)
    }

    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(_minZoomRatio.value, _maxZoomRatio.value)
        val camera = _camera ?: run {
            _currentZoomRatio.value = clamped
            return
        }

        try {
            camera.cameraControl.setZoomRatio(clamped)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set zoom ratio")
        } finally {
            _currentZoomRatio.value = clamped
        }
    }

    /** Tap-to-focus: meter AF+AE on the tapped viewfinder point, auto-cancelling
     *  back to continuous after a few seconds. The point's surface dimensions
     *  are the presenting view's — the preview fills it edge to edge. */
    fun focusOnPoint(view: View, x: Float, y: Float) {
        val camera = _camera ?: return
        if (view.width <= 0 || view.height <= 0) return
        val point = DisplayOrientedMeteringPointFactory(
            view.display,
            camera.cameraInfo,
            view.width.toFloat(),
            view.height.toFloat(),
        ).createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            point,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE,
        ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
        camera.cameraControl.startFocusAndMetering(action)
    }

    fun cancelTranslation() {
        objectCharacterRecognitionInference.cancel()
    }

    suspend fun load(bundle: ObjectCharacterRecognitionBundleWithFiles) {
        cancelTranslation()

        _loadMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    // TODO: Make thread count configurable from preferences
                    objectCharacterRecognitionInference.load(bundle = bundle, threads = 4)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                }
            }
        }
    }

    private fun close() {
        try {
            objectCharacterRecognitionInference.close()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to close model")
        }
    }

    fun reload() {
        viewModelScope.launch {
            _bundleData.collect { bundle ->
                if (bundle == null) {
                    close()
                    return@collect
                }

                load(bundle)
            }
        }
    }

    init {
        reload()
    }

    companion object {
        private val TAG: String = CameraTranslationViewModel::class.java.simpleName

        /** Minimum mean line score for a block's translations to be presented. */
        private const val BLOCK_SCORE_MIN = 0.5

        /** Entries kept in the per-block translation map. */
        private const val CACHE_MAX = 64
    }
}