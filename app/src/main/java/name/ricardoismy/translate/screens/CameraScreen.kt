package name.ricardoismy.translate.screens

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import name.ricardoismy.translate.utils.TextRecognitionProcessor
import java.util.UUID.randomUUID
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.max
import kotlin.math.min

fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
    if (lhs == rhs) {
        return 0
    }
    if (lhs.isEmpty()) {
        return rhs.length
    }
    if (rhs.isEmpty()) {
        return lhs.length
    }

    val lhsLength = lhs.length + 1
    val rhsLength = rhs.length + 1

    var cost = Array(lhsLength) { it }
    var newCost = Array(lhsLength) { 0 }

    for (i in 1..rhsLength - 1) {
        newCost[0] = i

        for (j in 1..lhsLength - 1) {
            val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1

            val costReplace = cost[j - 1] + match
            val costInsert = cost[j] + 1
            val costDelete = newCost[j - 1] + 1

            newCost[j] = min(min(costInsert, costDelete), costReplace)
        }

        val swap = cost
        cost = newCost
        newCost = swap
    }

    return cost[lhsLength - 1]
}


class TextBlock(
    private var lines: List<TextLines>,
    private var language: String,
    var boundingBox: Rect?,
    var cornerPoints: Array<out Point>?,
    var lastSeen: Long
) {
    val id = randomUUID()
    val confidence: Float = lines.sumOf { it.confidence.toDouble() }.toFloat() / lines.size

    private var visible = true

    fun text(): String {
        return lines.joinToString("\n") { it.text }
    }

    fun matches(other: TextBlock): Boolean {
        val a = text()
        val b = other.text()

        val distance = levenshtein(a.lowercase(), b.lowercase())
        val maxLength = max(a.length, b.length)
        return (distance.toDouble() / maxLength) < 0.1 // Allow 10% difference
    }

    fun worse(other: TextBlock): Boolean {
        return confidence > other.confidence
    }

    fun update(newLines: List<TextLines>, newLanguage: String) {
        lines = newLines
        language = newLanguage
    }

    fun seen(currentTime: Long) {
        lastSeen = currentTime
        visible = true
    }

    fun position(newBoundingBox: Rect?, newCornerPoints: Array<out Point>?) {
        boundingBox = newBoundingBox
        cornerPoints = newCornerPoints
    }

    fun hide() {
        visible = false
    }
}

data class TextLines(
    val text: String,
    val confidence: Float,
)

class CameraScreenViewModel : ViewModel() {
    private val executorService = Executors.newFixedThreadPool(2)
    val blocks = MutableStateFlow<List<TextBlock>>(emptyList())

    var imageWidth = 1f
    var imageHeight = 1f

    fun processFrameAsync(recognizedTextBlocks: List<Text.TextBlock>) {
        executorService.execute {
            processFrame(recognizedTextBlocks)
        }
    }

    fun processFrame(recognizedTextBlocks: List<Text.TextBlock>) {
        val currentTime = System.currentTimeMillis()

        val newBlocks = mutableListOf<TextBlock>()

        for (block in recognizedTextBlocks) {
            val textLines = mutableListOf<TextLines>()
            val textLinesLanguages = mutableListOf<String>()
            for (lines in block.lines) {
                textLines.add(TextLines(lines.text.trim(), lines.confidence))
                textLinesLanguages.add(lines.recognizedLanguage)
            }

            val language = textLinesLanguages.groupingBy { it }.eachCount().maxBy { it.value }.key

            val textBlock = TextBlock(
                textLines,
                language,
                block.boundingBox,
                block.cornerPoints,
                currentTime
            )

            if (textBlock.confidence < 0.8f) {
                continue
            }

            val existingBlock = blocks.value.find { it.matches(textBlock) }

            if (existingBlock != null) {
                if (existingBlock.worse(textBlock)) {
                    existingBlock.update(textLines, language)
                }

                existingBlock.position(block.boundingBox, block.cornerPoints)
                existingBlock.seen(currentTime)

                continue
            }

            newBlocks.add(textBlock)
        }

        blocks.value += newBlocks

        for (block in blocks.value) {
            if (!newBlocks.contains(block)) {
                block.hide()
            }
        }

        removeStaleBlocks(currentTime)
    }

    fun exists(textBlock: TextBlock): Boolean {
        return blocks.value.any { it.matches(textBlock) }
    }

    fun text(): String {
        return blocks.value.joinToString("\n") { it.text() }
    }

    private fun removeStaleBlocks(currentTime: Long) {
        blocks.value = blocks.value.filterNot { currentTime - it.lastSeen > 5000 }
    }
}

@Composable
fun CameraScreen(modifier: Modifier = Modifier, viewModel: CameraScreenViewModel = viewModel()) {
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
    ) { image, textBlocks ->
        viewModel.imageWidth = image.width.toFloat()
        viewModel.imageHeight = image.height.toFloat()

        viewModel.processFrameAsync(textBlocks)
    }

    val imageAnalyzer = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .also {
            it.setAnalyzer(ContextCompat.getMainExecutor(context), textRecognitionProcessor)
        }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraxSelector, preview, imageAnalyzer)
        preview.setSurfaceProvider(previewView.surfaceProvider)

    }

    Box(modifier = Modifier.fillMaxSize().then(modifier)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

//        Canvas(modifier = Modifier.fillMaxSize()) {
//            val canvasWidth = size.width
//            val canvasHeight = size.height
//
//            // Calculate scaling factors to map image coordinates to Canvas coordinates
//            val scaleX = canvasWidth / 480
//            val scaleY = canvasHeight / 640
//
//            for (textBlock in blocks) {
//                val rect = textBlock.boundingBox ?: continue
//
//                // Map the bounding box coordinates to Canvas coordinates
//                val left = rect.left
//                val top = rect.top
//                val right = rect.right
//                val bottom = rect.bottom
//
//                // Draw the rectangle overlay
//                drawRect(
//                    color = Color.Red,
//                    topLeft = Offset(left.toFloat(), top.toFloat()),
//                    size = Size(right.toFloat() - left.toFloat(), bottom.toFloat() - top.toFloat()),
//                    style = Stroke(width = 2.dp.toPx())
//                )
//            }
//        }
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