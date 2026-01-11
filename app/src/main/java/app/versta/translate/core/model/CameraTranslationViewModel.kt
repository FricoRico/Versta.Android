package app.versta.translate.core.model

import android.content.Context
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import app.versta.translate.adapter.outbound.OcrPostProcessor
import app.versta.translate.core.entity.CameraTranslationResult
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

@OptIn(FlowPreview::class)
class CameraTranslationViewModel(
    private val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference,
    private val translationViewModel: TranslationViewModel,
    private val objectCharacterRecognizerRepository: ObjectCharacterRecognitionRepository,
    private val languageViewModel: LanguageViewModel,
    private val languagePreferenceRepository: LanguagePreferenceRepository,
    private val ocrPostProcessor: OcrPostProcessor? = null
) : ViewModel() {
    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequests: StateFlow<SurfaceRequest?> = _surfaceRequests.asStateFlow()

    private val _singleCapture = MutableStateFlow(true)

    private val _viewFinderReady = MutableStateFlow(false)
    val viewFinderReady: StateFlow<Boolean> = _viewFinderReady.asStateFlow()

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

    private val _zoomPresets = MutableStateFlow<List<Float>>(emptyList())
    val zoomPresets: StateFlow<List<Float>> = _zoomPresets.asStateFlow()

    private var _camera: Camera? = null
    private var _cameraProvider: ProcessCameraProvider? = null
    private val _cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequests.update { newSurfaceRequest }
        }
    }

    private val _results = MutableStateFlow<List<CameraTranslationResult>>(emptyList())
    val detectedBoxes: StateFlow<List<CameraTranslationResult>> = _results.asStateFlow()

    private val _trackingImageAnalyzerExecutor = Executors.newCachedThreadPool()
    private var _trackingImageAnalyzerUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .setResolutionSelector(
            ResolutionSelector.Builder().apply {
                setResolutionStrategy(
                    ResolutionStrategy(
                        Size(960, 960),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
            }.build()
        )
        .build()

    // TODO: Make thread count configurable from preferences
    private val _threads = flowOf(4)

    private val _language = languagePreferenceRepository.getSourceLanguage().distinctUntilChanged()

    private val _detectorData = _language.filterNotNull().map { language ->
        if (language !is app.versta.translate.core.entity.Language) {
            return@map null
        }

        objectCharacterRecognizerRepository.getObjectCharacterRecognitionDetectorByLanguage(language = language)
    }

    private val _recognizerData = _language.filterNotNull().map { language ->
        if (language !is app.versta.translate.core.entity.Language) {
            return@map null
        }

        objectCharacterRecognizerRepository.getObjectCharacterRecognizerByLanguage(language = language)
    }.distinctUntilChanged()

    private val _loadingProgress = MutableStateFlow<LoadingProgress>(LoadingProgress.Idle)
    val loadingProgress: Flow<LoadingProgress> = _loadingProgress.asStateFlow().sample(10)

    private val _cameraTranslationState = MutableStateFlow<ReadyState>(ReadyState.NotReady)
    val cameraTranslationState: StateFlow<ReadyState> = _cameraTranslationState.asStateFlow()

    private val _loadMutex = Mutex()

    private val _objectCharacterRecognitionAnalyzer = ObjectCharacterRecognitionAnalyzer(
        objectCharacterRecognitionInference = objectCharacterRecognitionInference,
        postProcessor = ocrPostProcessor,
        beforeFrameProcessing = {
            viewModelScope.launch {
                _cameraProvider?.unbindAll()
            }
        },
        onFrameProcessed = { objects, timestamp ->
            val languages = languageViewModel.languagePair.first()
            if (languages == null) {
                return@ObjectCharacterRecognitionAnalyzer
            }

            val results = objects.filter {
                it.score >= 0.8f && it.text.isNotBlank()
            }.map {
                val translated = translationViewModel.translate(it.text, languages)

                CameraTranslationResult(
                    points = it.points,
                    score = it.score,
                    text = it.text,
                    translated = translated,
                    colors = it.colors,
                    fontSize = it.fontSize,
                    lineHeight = it.lineHeight,
                    fontWeight = it.fontWeight
                )
            }

            _results.value = results
            _translating.value = false
        }
    )

    private fun bindViewFinder(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner
    ): Camera? {
        try {
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(_cameraPreviewUseCase)
                .build()

            return cameraProvider.bindToLifecycle(
                lifecycleOwner = lifecycleOwner,
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup = useCaseGroup
            )
        } catch (e: Exception) {
            Timber.Forest.tag("PreviewViewModel").e(e, "Error binding to camera lifecycle")
            return null
        }
    }

    private fun bindAnalysisCapture(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner
    ): Camera? {
        try {
            _trackingImageAnalyzerUseCase.setAnalyzer(
                _trackingImageAnalyzerExecutor,
                _objectCharacterRecognitionAnalyzer
            )

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(_trackingImageAnalyzerUseCase)
                .build()

            return cameraProvider.bindToLifecycle(
                lifecycleOwner = lifecycleOwner,
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup = useCaseGroup
            )
        } catch (e: Exception) {
            Timber.Forest.tag("PreviewViewModel").e(e, "Error binding to camera lifecycle")
            return null
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
        _zoomPresets.value = generateZoomPresets(min.coerceAtLeast(0.01f), max)
    }

    private fun initializeCamera(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner
    ): Camera? {
        try {
            cameraProvider.unbindAll() // Only unbind once during initial setup

            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(_cameraPreviewUseCase)
                .addUseCase(_trackingImageAnalyzerUseCase)
                .build()

            _camera = cameraProvider.bindToLifecycle(
                lifecycleOwner = lifecycleOwner,
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
                useCaseGroup = useCaseGroup
            )
            return _camera
        } catch (e: Exception) {
            Timber.Forest.tag("PreviewViewModel").e(e, "Error binding to camera lifecycle")
            return null
        }
    }

    fun enableAnalyzer() {
        _trackingImageAnalyzerUseCase.setAnalyzer(
            _trackingImageAnalyzerExecutor,
            _objectCharacterRecognitionAnalyzer
        )
        _translating.value = true
    }

    fun disableAnalyzer() {
        _trackingImageAnalyzerUseCase.clearAnalyzer()
        _translating.value = false
    }

    suspend fun capture(appContext: Context, lifecycleOwner: LifecycleOwner) {
        _results.value = emptyList()
        _cameraProvider = ProcessCameraProvider.Companion.awaitInstance(appContext)
        _viewFinderReady.value = false

        if (_camera == null) {
            initializeCamera(_cameraProvider!!, lifecycleOwner)
            initZoomState()
        }

        enableAnalyzer()
    }

    suspend fun viewfinder(appContext: Context, lifecycleOwner: LifecycleOwner) {
        _results.value = emptyList()
        _cameraProvider = ProcessCameraProvider.Companion.awaitInstance(appContext)
        _viewFinderReady.value = true

        initializeCamera(_cameraProvider!!, lifecycleOwner)
        initZoomState()

        disableAnalyzer()
    }

    private fun generateZoomPresets(min: Float, max: Float): List<Float> {
        if (max <= min || min <= 0f) return listOf(min.coerceAtLeast(0.01f))

        val common = listOf(0.5f, 1f, 2f, 5f, 10f, 30f, 100f)
        val chosen = common.filter { it in min..max }

        val withExtremes = (chosen + listOf(min, max)).distinct().sorted()
        if (withExtremes.size >= 3) return withExtremes

        // fallback to log-spaced presets (6 steps)
        val steps = 6
        val lnMin = ln(min)
        val lnMax = ln(max)
        return (0 until steps).map { i ->
            exp(lnMin + i * (lnMax - lnMin) / (steps - 1))
        }.map { it.coerceIn(min, max) }.distinct().sorted()
    }

    fun ratioToSliderPosition(ratio: Float): Float {
        val min = _minZoomRatio.value.coerceAtLeast(0.0001f)
        val max = _maxZoomRatio.value.coerceAtLeast(min + 1e-6f)
        val lnMin = ln(min)
        val lnMax = ln(max)
        return ((ln(ratio.coerceIn(min, max)) - lnMin) / (lnMax - lnMin)).coerceIn(0f, 1f)
    }

    fun sliderPositionToRatio(position: Float): Float {
        val min = _minZoomRatio.value.coerceAtLeast(0.0001f)
        val max = _maxZoomRatio.value.coerceAtLeast(min + 1e-6f)
        val lnMin = ln(min)
        val lnMax = ln(max)
        val lnVal = lnMin + position.coerceIn(0f, 1f) * (lnMax - lnMin)
        return exp(lnVal).coerceIn(min, max)
    }

    private fun updateZoomPresetsFromCurrentState() {
        val zoomState = _camera?.cameraInfo?.zoomState?.value
        val min = zoomState?.minZoomRatio ?: _minZoomRatio.value
        val max = zoomState?.maxZoomRatio ?: _maxZoomRatio.value
        _zoomPresets.value = generateZoomPresets(min.coerceAtLeast(0.01f), max)
    }

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
            Timber.tag("PreviewViewModel").e(e, "Failed to set zoom ratio")
        } finally {
            _currentZoomRatio.value = clamped
        }
    }

    fun nearestPresetForPosition(position: Float): Pair<Float, Float>? {
        val presets = _zoomPresets.value
        if (presets.isEmpty()) return null
        val targetRatio = sliderPositionToRatio(position)
        val nearest = presets.minByOrNull { abs(it - targetRatio) } ?: presets.first()
        return nearest to ratioToSliderPosition(nearest)
    }

    fun focusOnPoint(surfaceBounds: Size, x: Float, y: Float) {
        // Create point for CameraX's CameraControl.startFocusAndMetering() and submit...
    }

    fun cancelTranslation() {
        objectCharacterRecognitionInference.cancel()
    }

    suspend fun load(detect: ObjectCharacterRecognitionDetectorWithFiles, recognize: ObjectCharacterRecognitionRecognizerWithFiles) {
        cancelTranslation()

        viewModelScope.async(Dispatchers.IO) {
            _loadMutex.withLock {
                _cameraTranslationState.value = ReadyState.NotReady
                _loadingProgress.value = LoadingProgress.InProgress

                try {
                    objectCharacterRecognitionInference.load(detect = detect, recognize = recognize, threads = 4)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e)
                    _loadingProgress.value = LoadingProgress.Error(e)
                }
            }
        }.await()
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
            combine(_detectorData, _recognizerData) { detector, recognizer ->
                Pair(detector, recognizer)
            }.collect { (detector, recognizer) ->
                if (detector == null || recognizer == null) {
                    close()
                    return@collect
                }

                load(detector, recognizer)
            }
        }
    }

    init {
        reload()
    }

    companion object {
        private val TAG: String = CameraTranslationViewModel::class.java.simpleName
    }
}