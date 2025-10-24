package app.versta.translate.ui.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.compose.CameraXViewfinder
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
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.luminance
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.versta.translate.adapter.outbound.ObjectCharacterRecognizerAnalyzer
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.VisionViewModel
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Vision(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel, // Unused, but kept for future use
) {
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

    val isCameraReady = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(400)
        isCameraReady.value = true
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel
    ) {
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(
                    top = MaterialTheme.spacing.medium,
                    start = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.medium,
                    bottom = MaterialTheme.spacing.extraLarge
                )
        ) {
            AnimatedVisibility(
                visible = isCameraReady.value && cameraPermissionState.status is PermissionStatus.Granted,
                enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                exit = ExitTransition.None
            ) {
                CameraViewFinder()
            }

            AnimatedVisibility(
                visible = cameraPermissionState.status is PermissionStatus.Denied,
                enter = EnterTransition.None,
                exit = fadeOut()
            ) {
                CameraPermissionDenied(cameraPermissionState)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPermissionDenied(state: PermissionState) {
    if (state.status.shouldShowRationale) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Text(text = "Without camera permission the vision feature is not available. Please grant the permission in the app settings.")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(text = "Without camera permission the vision feature is not available.")
        Button(onClick = { state.launchPermissionRequest() }) {
            Text(text = "Request permission")
        }
    }
}

// PreviewViewModel has been moved to VisionViewModel in core/model package
// to better handle OCR logic and translation

@Composable
fun CameraViewFinder(
    visionViewModel: VisionViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return VisionViewModel(
                    languageViewModel = app.versta.translate.MainApplication.module.languageViewModel,
                    translationViewModel = app.versta.translate.MainApplication.module.translationViewModel
                ) as T
            }
        }
    ),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val currentSurfaceRequest: SurfaceRequest? by visionViewModel.surfaceRequests.collectAsState()
    val results by visionViewModel.detectedBoxes.collectAsState()
    val preprocessedFrame by visionViewModel.preprocessedFrame.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        visionViewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        currentSurfaceRequest?.let { surfaceRequest ->
            val coordinateTransformer = remember { MutableCoordinateTransformer() }

            Box {
                CameraXViewfinder(
                    surfaceRequest = surfaceRequest,
                    implementationMode = ImplementationMode.EXTERNAL,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                with(coordinateTransformer) {
                                    val surfaceCoords = it.transform()
                                    // TODO: Implement focus on point
                                    // visionViewModel.focusOnPoint(...)
                                }
                            }
                        },
                    coordinateTransformer = coordinateTransformer,
                )

                Canvas(
                    modifier = Modifier.fillMaxSize(),
                    onDraw = {
                        val inferenceRect = RectF(0f, 0f, 960f, 960f)
                        val scaleX = size.width / inferenceRect.width()
                        val scaleY = size.height / inferenceRect.height()
                        val scale = maxOf(scaleX, scaleY)
                        val scaledWidth = inferenceRect.width() * scale
                        val scaledHeight = inferenceRect.height() * scale
                        val dx = (size.width - scaledWidth) / 2f
                        val dy = (size.height - scaledHeight) / 2f
                        val outputToScreenMatrix = Matrix().apply {
                            postScale(scale, scale)
                            postTranslate(dx, dy)
                        }

                        results.forEach { result ->
                            val points = result.points
                            val score = result.score
                            val text = result.translated
                            val colors = result.colors
                            if (score < 0.8f) return@forEach

                            // Map points to screen coordinates
                            val mappedPoints = points.map { point ->
                                val mapped = floatArrayOf(point.x, point.y)
                                outputToScreenMatrix.mapPoints(mapped)
                                Offset(mapped[0], mapped[1])
                            }

                            // Draw the polygon
                            val path = Path().apply {
                                moveTo(mappedPoints.first().x, mappedPoints.first().y)
                                mappedPoints.drop(1).forEach { p ->
                                    lineTo(p.x, p.y)
                                }
                                close()
                            }

                            val backgroundColor = colors.background
                            val foregroundColor = colors.foreground

                            drawPath(
                                path = path,
                                color = backgroundColor
                            )

                            // Calculate the bounding box of the polygon
                            val minX = mappedPoints.minOf { it.x }
                            val maxX = mappedPoints.maxOf { it.x }
                            val minY = mappedPoints.minOf { it.y }
                            val maxY = mappedPoints.maxOf { it.y }

                            // Define padding
                            val padding = 8f
                            val availableWidth = maxX - minX - 2 * padding
                            val availableHeight = maxY - minY - 2 * padding

                            // Create a Paint object for text
                            val textPaint = android.graphics.Paint().apply {
                                isAntiAlias = true
                                color = foregroundColor.toArgb()
                            }

                            // Start with a reasonable text size
                            var textSize = 24f
                            textPaint.textSize = textSize

                            // Measure the text width and height
                            val textBounds = Rect()
                            textPaint.getTextBounds(text, 0, text.length, textBounds)
                            val textWidth = textPaint.measureText(text)
                            val textHeight = textBounds.height().toFloat()

                            val widthRatio = availableWidth / textWidth
                            val heightRatio = availableHeight / textHeight
                            val ratio = minOf(widthRatio, heightRatio)
                            textSize *= ratio
                            textPaint.textSize = textSize

                            textPaint.getTextBounds(text, 0, text.length, textBounds)

                            drawContext.canvas.nativeCanvas.drawText(
                                text,
                                minX + padding,
                                minY + padding - textBounds.top.toFloat(),
                                textPaint
                            )
                        }
                    }
                )
            }
        }
    }
}
