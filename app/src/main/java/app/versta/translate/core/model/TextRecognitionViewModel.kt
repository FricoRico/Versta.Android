package app.versta.translate.core.model

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraEffect.PREVIEW
import androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.core.graphics.minus
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import app.versta.translate.core.entity.TrackedFrame
import app.versta.translate.core.entity.TrackedText
import app.versta.translate.utils.PyramidKLTProcessor
import app.versta.translate.utils.TextRecognitionProcessor
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import georegression.metric.UtilAngle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors
import kotlin.math.atan2


//data class TrackedTextBlock(
//    val text: String,
//    val sanitizedText: String = text.trimIndent().replace("\\s+".toRegex(), " ").lowercase(),
//    val lines: List<Text.Line>,
//    val boundingBox: Rect,
//    val blockAngle: Float,
//    val confidence: Float,
//    val stability: Int = 0,
//)

class TextRecognitionViewModel : ViewModel() {
    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest

    private val _cameraPreview = Preview.Builder().apply {
    }.build().apply {
        setSurfaceProvider { newSurfaceRequest ->
            _surfaceRequest.update { newSurfaceRequest }
        }
    }

    private val _trackedText = TrackedText()
    private var _offsetMatrix = Matrix()


    private val _textRecognitionProcessor = TextRecognitionProcessor(
        textRecognizerOptions = TextRecognizerOptions.Builder().build(),
        preProcessing = {
            _offsetMatrix = _trackedFrame.computeTranslationMatrix(_trackedText.boundingRect())
            _pyramidKLTProcessor.clear()
            _trackedFrame.clear()
        },
    ) { text, timestamp ->
        _trackedText.update(
            timestamp = timestamp,
            text = text,
        )
    }

    // TODO: Calculate scale based on original image size vs processing size
    private val _pyramidKLTProcessor = PyramidKLTProcessor(
        scale = 5f,
        significantChangeThreshold = 150f,
        onSignificantChange = {
            onSignificantChange()
        }
    ) { pairs, timestamp ->
        _trackedFrame.update(
            timestamp = timestamp,
            pairs = pairs,
        )

        _textRecognitionOverlay.drawFrameAsync(timestamp)
    }

    private fun onSignificantChange() {
        Log.d("PyramidKLT", "Significant change detected")
        _textRecognitionProcessor.shouldUpdate()
    }

    private val _textRecognitionOverlay = OverlayEffect(
        PREVIEW,
        3,
        Handler(Looper.getMainLooper())
    ) {}

    private var _trackedFrame = TrackedFrame()

    var hsv = FloatArray(3)

    @SuppressLint("RestrictedApi")
    suspend fun bindToCamera(appContext: Context, lifecycleOwner: LifecycleOwner) {
        val processCameraProvider = ProcessCameraProvider.awaitInstance(appContext)

        _textRecognitionOverlay.setOnDrawListener { frame ->
            if (frame.timestampNanos != _trackedFrame.timestamp) {
                return@setOnDrawListener true
            }

            val canvas = frame.overlayCanvas
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

            val paint = Paint()
            paint.color = Color.GREEN
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 5.0f

            val matrix = _trackedFrame.computeTranslationMatrix(_trackedText.boundingRect(80f))

            for (block in _trackedText.blocks) {
                val rect = block.cornerPoints?.map { PointF(it.x.toFloat(), it.y.toFloat()) } ?: continue

                val offsetRect = mapRectHomography(rect, _offsetMatrix)
                val transformedRect = mapRectHomography(offsetRect, matrix)

                val path = Path()
                if (transformedRect.size >= 2) {
                    // Move to the first point
                    path.moveTo(transformedRect[0].x, transformedRect[0].y)
                    // Draw lines to each subsequent point
                    var i = 1
                    while (i < transformedRect.size) {
                        path.lineTo(transformedRect[i].x, transformedRect[i].y)
                        i += 1
                    }
                    // Close the path to form the rectangle
                    path.close()
                }

                canvas.drawPath(
                    path,
                    paint
                )
            }

            val paintLine = Paint()
            paintLine.color = Color.RED
            paintLine.style = Paint.Style.STROKE
            paintLine.strokeWidth = 3.0f

            val maxRange = canvas.width / 8.0f
            hsv[1] = 1.0f
            hsv[2] = 0.8f

            val points = _trackedFrame.pointsInsideBoundingBox(_trackedText.boundingRect(80f))

            for (point in points) {
                val s = point.p1
                val p = point.p2

                val b = PointF(s.x.toFloat(), s.y.toFloat())
                val c = PointF(p.x.toFloat(), p.y.toFloat())

                hsv[0] = UtilAngle.degree(atan2(p.x.toFloat() - s.x.toFloat(), p.y.toFloat() - s.y.toFloat())) + 180.0f
                hsv[2] = (0.40f + 1.0f.coerceAtMost(b.minus(c).length() / maxRange) * 0.60f)

                paintLine.setColor(Color.HSVToColor(hsv))
                canvas.drawLine(s.x.toFloat(), s.y.toFloat(), p.x.toFloat(), p.y.toFloat(), paintLine)
            }

            true
        }

        val trackingExecutor = Executors.newCachedThreadPool()
        val trackingImageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder().apply {
                    setResolutionStrategy(
                        ResolutionStrategy(
                            Size(320, 240),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                }.build()
            )
            .build()
            .also {
                it.setAnalyzer(trackingExecutor, _pyramidKLTProcessor)
            }

        val textRecognitionExecutor = Executors.newCachedThreadPool()
        val textRecognitionImageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder().apply {
                    setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1600, 1200),
                            FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                }.build()
            )
            .build()
            .also {
                it.setAnalyzer(textRecognitionExecutor, _textRecognitionProcessor)
            }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(_cameraPreview)
            .addUseCase(trackingImageAnalyzer)
            .addUseCase(textRecognitionImageAnalyzer)
            .addEffect(_textRecognitionOverlay)
            .build()

        processCameraProvider.bindToLifecycle(
            lifecycleOwner, DEFAULT_BACK_CAMERA, useCaseGroup
        )

        try {
            awaitCancellation()
        } finally {
            processCameraProvider.unbindAll()
        }
    }

    fun mapRectToPolygon(rect: RectF): List<PointF> {
        return listOf(
            PointF(rect.left, rect.top),
            PointF(rect.right, rect.top),
            PointF(rect.right, rect.bottom),
            PointF(rect.left, rect.bottom)
        )
    }

    /**
     * Projects the 4 corners of a RectF with a homography.
     */
    fun mapRectHomography(rect: List<PointF>, matrix: Matrix): List<PointF> {
        val points = floatArrayOf(
            rect[0].x, rect[0].y,
            rect[1].x, rect[1].y,
            rect[2].x, rect[2].y,
            rect[3].x, rect[3].y
        )

        matrix.mapPoints(points)

        // The result is no longer necessarily a rectangle, so we return them as a list
        // in the same corner order: [TL, TR, BR, BL].

        return listOf(
            PointF(points[0], points[1]),
            PointF(points[2], points[3]),
            PointF(points[4], points[5]),
            PointF(points[6], points[7])
        )
    }

//    private val _stableBlocks = MutableStateFlow<List<TrackedTextBlock>>(emptyList())
//    val stableBlocks: StateFlow<List<TrackedTextBlock>> = _stableBlocks.asStateFlow()
//
//    private val _transformMatrix = mutableStateOf(Matrix())
//    val transformMatrix = _transformMatrix
//
//    private val _safeArea = mutableStateOf(Rect())
//    val safeArea = _safeArea
//
//    private val _needUpdateTransformation = mutableStateOf(true)
//
//    private val _rotationCompensation = mutableFloatStateOf(0f)
//    val rotationCompensation = _rotationCompensation
//
//    private val bufferSize = 32
//    private val temporalBuffer = ArrayDeque<List<TrackedTextBlock>>(bufferSize)
//    private val stableFrameCountThreshold = 4
//    private val confidenceThreshold = 0.3f
//
//    private val safeAreaFactor = 0.02f
//    private var scaleFactor = 1.0f
//
//    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
//    fun processFrame(text: Text) {
//        processingScope.launch {
//            val filteredTextBlocks = text.textBlocks.map {
//                TrackedTextBlock(
//                    text = it.text,
//                    lines = it.lines,
//                    boundingBox = it.boundingBox!!,
//                    confidence = calculateConfidence(it),
//                    blockAngle = blockAngle(it),
//                )
//            }.filter { it.confidence > confidenceThreshold }
//
//            if (filteredTextBlocks.isEmpty()) {
//                clearBuffer()
//                return@launch
//            }
//
//            updateBuffer(filteredTextBlocks)
//
//            val filteredStableBlocks = filteredTextBlocks.filter { isStable(it) }
//            if (filteredStableBlocks.isNotEmpty()) {
//                _stableBlocks.value = filteredStableBlocks
//            }
//        }
//    }
//
//    private fun calculateConfidence(block: TextBlock): Float {
//        return (block.lines.sumOf { line -> line.confidence.toDouble() } / max(
//            block.lines.size,
//            1
//        )).toFloat()
//    }
//
//    private fun blockAngle(block: TextBlock): Float {
//        return (block.lines.sumOf { line -> line.angle.toDouble() } / max(
//            block.lines.size,
//            1
//        )).toFloat()
//    }
//
//    private fun isStable(block: TrackedTextBlock): Boolean {
//        return temporalBuffer.count { it.any { frame -> frame.sanitizedText.hashCode() == block.sanitizedText.hashCode() } } >= stableFrameCountThreshold
//    }
//
//    private fun clearBuffer() {
//        temporalBuffer.clear()
//        _stableBlocks.value = emptyList()
//    }
//
//    private fun updateBuffer(blocks: List<TrackedTextBlock>) {
//        if (this.temporalBuffer.size == bufferSize) {
//            this.temporalBuffer.removeFirst()
//        }
//        this.temporalBuffer.addLast(blocks)
//    }
}
