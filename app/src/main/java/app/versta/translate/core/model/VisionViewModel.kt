package app.versta.translate.core.model

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.versta.translate.adapter.outbound.ObjectCharacterRecognizerAnalyzer
import app.versta.translate.core.entity.ObjectCharacterRecognitionResult
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * ViewModel for the Vision screen that manages camera preview and OCR analysis.
 * Handles the instantiation of the ObjectCharacterRecognizerAnalyzer and
 * manages translation of detected text.
 */
class VisionViewModel(
    private val languageViewModel: LanguageViewModel,
    private val translationViewModel: TranslationViewModel
) : ViewModel() {
    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequests: StateFlow<SurfaceRequest?> = _surfaceRequests.asStateFlow()

    private val _cameraPreviewUseCase = Preview.Builder().build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequests.update { newSurfaceRequest }
        }
    }

    private val _detectedBoxes = MutableStateFlow<List<ObjectCharacterRecognitionResult>>(emptyList())
    val detectedBoxes: StateFlow<List<ObjectCharacterRecognitionResult>> = _detectedBoxes.asStateFlow()

    private val _preprocessedFrame = MutableStateFlow<Bitmap?>(null)
    val preprocessedFrame: StateFlow<Bitmap?> = _preprocessedFrame.asStateFlow()

    private val _objectCharacterRecognizerAnalyzer = ObjectCharacterRecognizerAnalyzer(
        onFrameProcessed = { objects, bitmap, timestamp ->
            viewModelScope.launch {
                val translatedResults = translateResults(objects)
                _detectedBoxes.value = translatedResults
                _preprocessedFrame.value = bitmap
            }
        }
    )

    private val _trackingExecutor = Executors.newCachedThreadPool()
    private val _trackingImageAnalyzerUseCase = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        .setResolutionSelector(
            ResolutionSelector.Builder().apply {
                setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(960, 960),
                        FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
            }.build()
        )
        .build()
        .also {
            it.setAnalyzer(_trackingExecutor, _objectCharacterRecognizerAnalyzer)
        }

    /**
     * Translates the detected text in OCR results using the current language pair.
     */
    private suspend fun translateResults(
        results: List<ObjectCharacterRecognitionResult>
    ): List<ObjectCharacterRecognitionResult> {
        val languages = languageViewModel.languagePair.first() ?: return results

        results.forEach { result ->
            val translated = translationViewModel.translate(result.text, languages)
            result.translated = translated
        }

        return results
    }

    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(_cameraPreviewUseCase)
            .addUseCase(_trackingImageAnalyzerUseCase)
            .build()

        processCameraProvider.bindToLifecycle(
            lifecycleOwner = lifecycleOwner,
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            useCaseGroup = useCaseGroup
        )

        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }

    fun focusOnPoint(surfaceBounds: android.util.Size, x: Float, y: Float) {
        // TODO: Implement focus on point using CameraX's CameraControl.startFocusAndMetering()
    }

    override fun onCleared() {
        super.onCleared()
        _objectCharacterRecognizerAnalyzer.close()
        _trackingExecutor.shutdown()
    }

    val cameraPreviewUseCase: Preview
        get() = _cameraPreviewUseCase
}
