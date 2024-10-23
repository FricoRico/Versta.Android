package app.versta.translate.ui.screen

import android.content.Context
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.Text.TextBlock
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import app.versta.translate.utils.TextRecognitionProcessor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max

data class TrackedTextBlock(
    val text: String,
    val sanitizedText: String = text.trimIndent().replace("\\s+".toRegex(), " ").lowercase(),
    val lines: List<Text.Line>,
    val boundingBox: Rect,
    val blockAngle: Float,
    val confidence: Float,
    val stability: Int = 0,
)

class TextRecognitionViewModel(context: Context) : ViewModel() {
    private val _stableBlocks = MutableStateFlow<List<TrackedTextBlock>>(emptyList())
    val stableBlocks: StateFlow<List<TrackedTextBlock>> = _stableBlocks.asStateFlow()

    private val _transformMatrix = mutableStateOf(Matrix())
    val transformMatrix = _transformMatrix

    private val _safeArea = mutableStateOf(Rect())
    val safeArea = _safeArea

    private val _needUpdateTransformation = mutableStateOf(true)

    private val _rotationCompensation = mutableStateOf(0f)
    val rotationCompensation = _rotationCompensation

    private val bufferSize = 32
    private val temporalBuffer = ArrayDeque<List<TrackedTextBlock>>(bufferSize)
    private val stableFrameCountThreshold = 4
    private val confidenceThreshold = 0.4f

    private val safeAreaFactor = 0.02f
    private var scaleFactor = 1.0f

    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun processFrame(text: Text) {
        processingScope.launch {
            val filteredTextBlocks = text.textBlocks.map {
                TrackedTextBlock(
                    text = it.text,
                    lines = it.lines,
                    boundingBox = it.boundingBox!!,
                    confidence = 1f,
                    blockAngle = blockAngle(it),
                )
            }

            if (filteredTextBlocks.isEmpty()) {
                clearBuffer()
                return@launch
            }

            updateBuffer(filteredTextBlocks)

            val filteredStableBlocks = filteredTextBlocks.filter { isStable(it) }
            if (filteredStableBlocks.isNotEmpty()) {
                _stableBlocks.value = filteredStableBlocks
            }
        }
    }

    fun transformMatrix(imageProxy: ImageProxy, previewView: PreviewView) {
        if (!_needUpdateTransformation.value) {
            return
        }

        var imageWidth = imageProxy.width.toFloat()
        var imageHeight = imageProxy.height.toFloat()

        if (imageProxy.imageInfo.rotationDegrees % 180 == 90) {
            imageWidth = imageProxy.height.toFloat()
            imageHeight = imageProxy.width.toFloat()

            _rotationCompensation.value = imageProxy.imageInfo.rotationDegrees.toFloat()
        }

        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()

        val viewAspectRatio = viewWidth / viewHeight
        val imageAspectRatio = imageWidth / imageHeight

        var postScaleWidthOffset = 0f
        var postScaleHeightOffset = 0f
        if (viewAspectRatio > imageAspectRatio) {
            scaleFactor = viewWidth / imageWidth
            postScaleHeightOffset = (viewWidth / imageAspectRatio - viewHeight) / 2
        } else {
            scaleFactor = viewHeight / imageHeight
            postScaleWidthOffset = (viewHeight * imageAspectRatio - viewWidth) / 2
        }

        _transformMatrix.value.reset()
        _transformMatrix.value.setScale(scaleFactor, scaleFactor)
        _transformMatrix.value.postTranslate(-postScaleWidthOffset, -postScaleHeightOffset)

        val aspectRatioSafeAreaFactorTopLeft =
            if (viewAspectRatio > 1) viewWidth * safeAreaFactor else viewHeight * safeAreaFactor

        _safeArea.value = Rect(
            (-imageWidth / 2).toInt(),
            aspectRatioSafeAreaFactorTopLeft.toInt(),
            viewWidth.toInt() + (imageWidth / 2).toInt(),
            (viewHeight - aspectRatioSafeAreaFactorTopLeft).toInt()
        )

        _needUpdateTransformation.value = false
    }

    private fun calculateConfidence(block: TextBlock): Float {
        return (block.lines.sumOf { line -> line.confidence.toDouble() } / max(
            block.lines.size,
            1
        )).toFloat()
    }

    private fun blockAngle(block: TextBlock): Float {
        return (block.lines.sumOf { line -> line.angle.toDouble() } / max(
            block.lines.size,
            1
        )).toFloat()
    }

    private fun isStable(block: TrackedTextBlock): Boolean {
        return temporalBuffer.count { it.any { frame -> frame.sanitizedText.hashCode() == block.sanitizedText.hashCode() } } >= stableFrameCountThreshold
    }

    fun needsUpdateTransformation(update: Boolean) {
        _needUpdateTransformation.value = update
    }

    private fun clearBuffer() {
        temporalBuffer.clear()
        _stableBlocks.value = emptyList()
    }

    private fun updateBuffer(blocks: List<TrackedTextBlock>) {
        if (this.temporalBuffer.size == bufferSize) {
            this.temporalBuffer.removeFirst()
        }
        this.temporalBuffer.addLast(blocks)
    }
}

@Composable
fun Camera(
    modifier: Modifier = Modifier,
    viewModel: TextRecognitionViewModel = TextRecognitionViewModel(LocalContext.current)
) {
    val stableBlocks by viewModel.stableBlocks.collectAsStateWithLifecycle()
    val transformMatrix by viewModel.transformMatrix
    val rotationCompensation by viewModel.rotationCompensation
    val safeArea by viewModel.safeArea

    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val preview = Preview.Builder().build()
    val previewView = remember {
        PreviewView(context)
    }
    val cameraxSelector = CameraSelector
        .Builder()
        .requireLensFacing(lensFacing)
        .build()

    val textRecognitionProcessor = TextRecognitionProcessor(
        TextRecognizerOptions.Builder().build()
    ) { imageProxy, text ->
        viewModel.transformMatrix(imageProxy, previewView)
        viewModel.processFrame(text)
    }

    val imageAnalyzer = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//        .setResolutionSelector(
//            ResolutionSelector.Builder()
//                .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
//                .build()
//        )
        .build()
        .also {
            it.setAnalyzer(ContextCompat.getMainExecutor(context), textRecognitionProcessor)
        }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraxSelector, preview, imageAnalyzer)
        preview.surfaceProvider = previewView.surfaceProvider
    }

    val backgroundPadding = 16f
    val backgroundPaint = Paint().apply {
        color = 0x7F000000
    }

    val textPaintNotTranslated = Paint().apply {
        color = 0xFFFF0000.toInt()
        textSize = 16f
    }

   val aspectRatio =  (preview.resolutionInfo?.resolution?.height?.toFloat() ?: 3f) / (preview.resolutionInfo?.resolution?.width?.toFloat() ?: 4f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(aspectRatio)
            .then(modifier)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    transformMatrix.reset()
                    viewModel.needsUpdateTransformation(true)
                }
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawContext.canvas.nativeCanvas.apply {
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    // Outer rectangle (full canvas)
                    addRect(Rect(0, 0, size.width.toInt(), size.height.toInt()).toComposeRect())
                    // Inner rectangle (safeRect) that we want to cut out
                    addRect(safeArea.toComposeRect())
                }

                // Draw the path with red color
                drawPath(
                    path = path,
                    color = Color.Red.copy(alpha = 0.3f),
                )
                for (block in stableBlocks) {
                    val boundingBox = block.boundingBox.toRectF()
                    transformMatrix.mapRect(boundingBox)

                    save()

                    rotate(
                        block.blockAngle + rotationCompensation,
                        boundingBox.centerX(),
                        boundingBox.centerY()
                    )

                    drawRect(
                        boundingBox.left - backgroundPadding,
                        boundingBox.top - backgroundPadding,
                        boundingBox.right + backgroundPadding,
                        boundingBox.bottom + backgroundPadding,
                        backgroundPaint
                    )

                    restore()

                    for (line in block.lines) {
                        val lineBoundingBox = line.boundingBox?.toRectF() ?: continue
                        transformMatrix.mapRect(lineBoundingBox)

                        // Save the current canvas state before applying transformations
                        save()

                        // Rotate the canvas around the center of the bounding box
                        rotate(
                            line.angle + rotationCompensation,
                            lineBoundingBox.centerX(),
                            lineBoundingBox.centerY()
                        )

                        val bounds = Rect()
                        textPaintNotTranslated.getTextBounds(line.text, 0, line.text.length, bounds)

                        val scale = minOf(
                            lineBoundingBox.width() / bounds.width(),
                            lineBoundingBox.height() / bounds.height()
                        )

                        textPaintNotTranslated.textSize *= scale

                        drawText(
                            line.text,
                            lineBoundingBox.left,
                            lineBoundingBox.top + textPaintNotTranslated.textSize,
                            textPaintNotTranslated
                        )

                        // Restore the canvas to the previous state before rotation
                        restore()
                    }
                }
            }
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }