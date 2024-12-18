package app.versta.translate.ui.screen

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.core.model.TextRecognitionViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.utils.TextRecognitionProcessor
import app.versta.translate.utils.koinActivityViewModel
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun Camera(
    modifier: Modifier = Modifier,
    textRecognitionViewModel: TextRecognitionViewModel = koinActivityViewModel(),
    translationViewModel: TranslationViewModel = koinActivityViewModel(),
) {
    val stableBlocks by textRecognitionViewModel.stableBlocks.collectAsStateWithLifecycle()
    val transformMatrix by textRecognitionViewModel.transformMatrix
    val rotationCompensation by textRecognitionViewModel.rotationCompensation
    val safeArea by textRecognitionViewModel.safeArea

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

    val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val textRecognitionProcessor = TextRecognitionProcessor(
        TextRecognizerOptions.Builder().build()
    ) { imageProxy, text ->
        textRecognitionViewModel.transformMatrix(imageProxy, previewView)
        textRecognitionViewModel.processFrame(text)

        val startTime = System.currentTimeMillis()
        processingScope.launch {
            val texts = stableBlocks.map { it.text }
            if (texts.isEmpty()) return@launch

//            val output = translationViewModel.translate(texts)
            val elapsedTime = System.currentTimeMillis() - startTime

//            Log.i("CameraTranslation", "Translated '$texts' in ${elapsedTime}ms: $output")
        }
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

    val aspectRatio = (preview.resolutionInfo?.resolution?.height?.toFloat()
        ?: 3f) / (preview.resolutionInfo?.resolution?.width?.toFloat() ?: 4f)

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
                    textRecognitionViewModel.needsUpdateTransformation(true)
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