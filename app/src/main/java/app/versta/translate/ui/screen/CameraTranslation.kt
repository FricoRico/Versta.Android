package app.versta.translate.ui.screen

import android.Manifest
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.compose.MutableCoordinateTransformer
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.CameraTranslationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.theme.spacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

private val SNAP_THRESHOLD = 0.2f

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraTranslation(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    cameraTranslationViewModel: CameraTranslationViewModel,
    navigationViewModel: NavigationViewModel, // Unused, but kept for future use
) {
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val torchEnabled by cameraTranslationViewModel.torchEnabled.collectAsStateWithLifecycle()
    val translating by cameraTranslationViewModel.translating.collectAsStateWithLifecycle()
    val viewFinderMode by cameraTranslationViewModel.viewFinderReady.collectAsStateWithLifecycle()

    var ready by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(500)
        ready = true
    }

    fun toggleTorch() {
        cameraTranslationViewModel.torch(!torchEnabled)
    }

    fun toggleCameraAnalysis() {
        Timber.i("Pressed translating button!")
        if (translating) {
            return
        }

        if (viewFinderMode) {
            scope.launch {
                cameraTranslationViewModel.capture(
                    context.applicationContext,
                    lifecycleOwner
                )
            }
            return
        }

        scope.launch {
            cameraTranslationViewModel.viewfinder(
                context.applicationContext,
                lifecycleOwner
            )
        }
    }

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel
    ) {
        Box(
            modifier = Modifier
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = ready && cameraPermissionState.status is PermissionStatus.Granted,
                enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                exit = ExitTransition.None
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = MaterialTheme.spacing.medium,
                            start = MaterialTheme.spacing.medium,
                            end = MaterialTheme.spacing.medium,
                            bottom = MaterialTheme.spacing.extraLarge
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        CameraViewFinder(
                            cameraTranslationViewModel = cameraTranslationViewModel,
                            lifecycleOwner = lifecycleOwner,
                            modifier = Modifier
                                .fillMaxSize()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(MaterialTheme.spacing.small),
                            horizontalArrangement = Arrangement.End
                        ) {
//                            ZoomSelector(previewViewModel = previewViewModel)
                            FilledIconButton(
                                onClick = {
                                    toggleTorch()
                                }
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled)
                                        ImageVector.vectorResource(R.drawable.baseline_lightbulb_24)
                                    else
                                        ImageVector.vectorResource(R.drawable.baseline_lightbulb_outline_24),
                                    contentDescription = "Torch",
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.spacing.large),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            enabled = !translating,
                            modifier = Modifier
                                .size(MaterialTheme.spacing.extraExtraLarge),
                            shape = MaterialTheme.shapes.extraExtraLarge,
                            border = BorderStroke(
                                width = MaterialTheme.spacing.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            onClick = {
                                toggleCameraAnalysis()
                            }
                        ) {
                            Box {
                                this@Button.AnimatedVisibility(
                                    visible = translating,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.requiredSize(MaterialTheme.spacing.extraLarge)
                                    )
                                }

                                this@Button.AnimatedVisibility(
                                    visible = !translating && viewFinderMode,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.rounded_translate_24),
                                        contentDescription = "Translate",
                                        modifier = Modifier.requiredSize(MaterialTheme.spacing.extraLarge)
                                    )
                                }

                                this@Button.AnimatedVisibility(
                                    visible = !translating && !viewFinderMode,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.round_replay_24),
                                        contentDescription = "Restart",
                                        modifier = Modifier.requiredSize(MaterialTheme.spacing.extraLarge)
                                    )
                                }
                            }
                        }
                    }
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoomSelector(
    modifier: Modifier = Modifier,
    cameraTranslationViewModel: CameraTranslationViewModel,
) {
    val min by cameraTranslationViewModel.minZoomRatio.collectAsState()
    val max by cameraTranslationViewModel.maxZoomRatio.collectAsState()
    val current by cameraTranslationViewModel.currentZoomRatio.collectAsState()
    val presets by cameraTranslationViewModel.zoomPresets.collectAsState()
    var sliderPos by remember { mutableStateOf(cameraTranslationViewModel.ratioToSliderPosition(current)) }
    // presetPositions previously unused; removed to avoid warnings
    val layoutDirection = LocalLayoutDirection.current

    // Sync slider position with external changes
    LaunchedEffect(current, min, max) {
        sliderPos = cameraTranslationViewModel.ratioToSliderPosition(current)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = String.format(Locale.US, "%.2fx", current))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            // Moving track and stripes
            val horizontalPadding = 24.dp
            var trackWidthPx by remember { mutableStateOf(0f) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
                    .onSizeChanged { trackWidthPx = it.width.toFloat() }
            ) {
                val trackWidth = size.width
                val trackHeight = size.height
                val centerX = size.width / 2
                // Reverse the sliding direction
                val trackOffset = (0.5f - sliderPos) * trackWidth

                // Draw vertical stripes for presets and intermediate stripes
                presets.forEachIndexed { index, preset ->
                    val presetPos = cameraTranslationViewModel.ratioToSliderPosition(preset)
                    val xPos = centerX + (presetPos - 0.5f) * trackWidth + trackOffset

                    // Draw red stripe for the preset
                    drawLine(
                        color = Color.Red,
                        start = Offset(xPos, 0f),
                        end = Offset(xPos, trackHeight),
                        strokeWidth = 2f
                    )

                    // Draw 4 blue stripes between this preset and the next
                    if (index < presets.size - 1) {
                        val nextPreset = presets[index + 1]
                        val lnCurrent = ln(preset)
                        val lnNext = ln(nextPreset)
                        val delta = (lnNext - lnCurrent) / 5f // 5 gaps for 4 stripes

                        repeat(4) { i ->
                            val lnStripe = lnCurrent + (i + 1) * delta
                            val stripeRatio = exp(lnStripe)
                            val stripePos = cameraTranslationViewModel.ratioToSliderPosition(stripeRatio)
                            val stripeX = centerX + (stripePos - 0.5f) * trackWidth + trackOffset
                            drawLine(
                                color = Color.Blue,
                                start = Offset(stripeX, 0f),
                                end = Offset(stripeX, trackHeight),
                                strokeWidth = 1f
                            )
                        }
                    }
                }
            }

            // Fixed thumb in the center
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .align(Alignment.Center)
            )

            // Invisible touch overlay that handles taps and horizontal drags
            // Matches the same horizontal padding as the Canvas so positions line up
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding)
                    .pointerInput(presets, trackWidthPx, layoutDirection) {
                        if (trackWidthPx <= 0f) return@pointerInput

                        detectTapGestures { offset ->
                            if (trackWidthPx <= 0f) return@detectTapGestures
                            val raw = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                            // For LTR: left->right should map 0->1 (raw). For RTL: mirror it.
                            val pos = if (layoutDirection == LayoutDirection.Ltr) raw else 1f - raw
                            sliderPos = pos
                            val ratio = cameraTranslationViewModel.sliderPositionToRatio(pos)
                            cameraTranslationViewModel.setZoomRatio(ratio)

                            // Snap if close to a preset
                            val snap = cameraTranslationViewModel.nearestPresetForPosition(sliderPos)
                            if (snap != null) {
                                val (presetRatio, presetPos) = snap
                                val distance = abs(presetPos - sliderPos)
                                if (distance <= SNAP_THRESHOLD) {
                                    sliderPos = presetPos
                                    cameraTranslationViewModel.setZoomRatio(presetRatio)
                                }
                            }
                        }
                    }
                    .pointerInput(presets, trackWidthPx, layoutDirection) {
                        if (trackWidthPx <= 0f) return@pointerInput

                        detectHorizontalDragGestures(
                            onDragStart = { /* start */ },
                            onDragEnd = {
                                // Snap to nearest preset on release
                                val snap = cameraTranslationViewModel.nearestPresetForPosition(sliderPos)
                                if (snap != null) {
                                    val (presetRatio, presetPos) = snap
                                    val distance = abs(presetPos - sliderPos)
                                    if (distance <= SNAP_THRESHOLD) {
                                        sliderPos = presetPos
                                        cameraTranslationViewModel.setZoomRatio(presetRatio)
                                    }
                                }
                            },
                            onDragCancel = { /* cancelled */ },
                            onHorizontalDrag = { change, dragAmount ->
                                if (trackWidthPx <= 0f) return@detectHorizontalDragGestures
                                // consume the change so other gestures don't also handle it
                                try {
                                    change.consume()
                                } catch (_: Exception) {
                                    // consume may not be needed; ignore safely
                                }

                                val deltaPosRaw = dragAmount / trackWidthPx
                                // For LTR dragging right should increase sliderPos, so use deltaPosRaw directly.
                                // For RTL, flip sign.
                                val deltaPos =
                                    if (layoutDirection == LayoutDirection.Ltr) deltaPosRaw else -deltaPosRaw
                                sliderPos = (sliderPos + deltaPos).coerceIn(0f, 1f)
                                val ratio = cameraTranslationViewModel.sliderPositionToRatio(sliderPos)
                                cameraTranslationViewModel.setZoomRatio(ratio)
                            }
                        )
                    }
            )
        }
    }
}

@Composable
fun CameraViewFinder(
    modifier: Modifier = Modifier,
    cameraTranslationViewModel: CameraTranslationViewModel,
    lifecycleOwner: LifecycleOwner,
) {
    val currentSurfaceRequest: SurfaceRequest? by cameraTranslationViewModel.surfaceRequests.collectAsState()
    val results by cameraTranslationViewModel.detectedBoxes.collectAsState()

    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        cameraTranslationViewModel.viewfinder(context.applicationContext, lifecycleOwner)
    }

    Surface(
        modifier = Modifier.then(modifier),
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
                            detectTransformGestures { _, _, zoom, _ ->
                                // zoom is a scale factor relative to previous gesture
                                // apply directly via ViewModel
                                if (!zoom.isFinite()) return@detectTransformGestures
                                cameraTranslationViewModel.applyZoomDelta(zoom)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                with(coordinateTransformer) {
                                    val surfaceCoords = it.transform()
                                    cameraTranslationViewModel.focusOnPoint(
                                        surfaceRequest.resolution,
                                        surfaceCoords.x,
                                        surfaceCoords.y,
                                    )
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
                            val textPaint = Paint().apply {
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
