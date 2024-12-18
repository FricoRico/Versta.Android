package app.versta.translate.core.model

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.Text.TextBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

class TextRecognitionViewModel : ViewModel() {
    private val _stableBlocks = MutableStateFlow<List<TrackedTextBlock>>(emptyList())
    val stableBlocks: StateFlow<List<TrackedTextBlock>> = _stableBlocks.asStateFlow()

    private val _transformMatrix = mutableStateOf(Matrix())
    val transformMatrix = _transformMatrix

    private val _safeArea = mutableStateOf(Rect())
    val safeArea = _safeArea

    private val _needUpdateTransformation = mutableStateOf(true)

    private val _rotationCompensation = mutableFloatStateOf(0f)
    val rotationCompensation = _rotationCompensation

    private val bufferSize = 32
    private val temporalBuffer = ArrayDeque<List<TrackedTextBlock>>(bufferSize)
    private val stableFrameCountThreshold = 4
    private val confidenceThreshold = 0.3f

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
                    confidence = calculateConfidence(it),
                    blockAngle = blockAngle(it),
                )
            }.filter { it.confidence > confidenceThreshold }

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
