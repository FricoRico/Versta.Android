package app.versta.translate.ui.screen

import android.Manifest
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.text.TextPaint
import androidx.camera.core.SurfaceRequest
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.versta.translate.ui.component.VerstaGlSurfaceView
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
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
import app.versta.translate.R
import app.versta.translate.core.entity.CameraTranslationBlockLine
import app.versta.translate.core.entity.FontWeight
import app.versta.translate.core.model.CameraTranslationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.entity.OcrBlockLayout
import app.versta.translate.core.entity.OcrBlockLayoutCache
import app.versta.translate.core.entity.OcrBlockRender
import app.versta.translate.core.entity.OCR_TEXT_HORIZONTAL_INSET
import app.versta.translate.core.entity.OcrLineQuad
import app.versta.translate.core.entity.lineQuadOf
import app.versta.translate.utils.mapPoints
import app.versta.translate.utils.mapToArray
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



@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraTranslation(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    cameraTranslationViewModel: CameraTranslationViewModel,
) {
    val cameraPermissionState = rememberPermissionState(
        Manifest.permission.CAMERA
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val torchEnabled by cameraTranslationViewModel.torchEnabled.collectAsStateWithLifecycle()
    val translating by cameraTranslationViewModel.translating.collectAsStateWithLifecycle()
    val stillBusy by cameraTranslationViewModel.stillBusy.collectAsStateWithLifecycle()
    val viewfinderMode by cameraTranslationViewModel.viewfinderMode.collectAsStateWithLifecycle()

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
        if (translating) {
            return
        }

        if (viewfinderMode) {
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
                                    contentDescription = stringResource(R.string.vision_torch),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = MaterialTheme.spacing.large),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            enabled = !translating && !stillBusy,
                            modifier = Modifier
                                .size(MaterialTheme.spacing.extraExtraLarge),
                            shape = MaterialTheme.shapes.extraExtraLarge,
                            border = BorderStroke(
                                width = MaterialTheme.spacing.extraSmall,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            onClick = {
                                scope.launch {
                                    cameraTranslationViewModel.takeStill(context.applicationContext)
                                }
                            }
                        ) {
                            Box {
                                this@Button.AnimatedVisibility(
                                    visible = stillBusy,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.requiredSize(MaterialTheme.spacing.extraLarge)
                                    )
                                }

                                this@Button.AnimatedVisibility(
                                    visible = !stillBusy,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.rounded_photo_camera_24),
                                        contentDescription = stringResource(R.string.vision_capture),
                                        modifier = Modifier.requiredSize(MaterialTheme.spacing.extraLarge)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(MaterialTheme.spacing.medium))

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
                                    visible = !translating && viewfinderMode,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.rounded_translate_24),
                                        contentDescription = stringResource(R.string.vision_translate),
                                        modifier = Modifier.requiredSize(MaterialTheme.spacing.extraLarge)
                                    )
                                }

                                this@Button.AnimatedVisibility(
                                    visible = !translating && !viewfinderMode,
                                    enter = fadeIn(),
                                    exit = fadeOut()
                                ) {
                                    Icon(
                                        imageVector = ImageVector.vectorResource(R.drawable.round_replay_24),
                                        contentDescription = stringResource(R.string.vision_restart),
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
            Text(text = stringResource(R.string.vision_permission_rationale))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(text = stringResource(R.string.vision_permission_denied))
        Button(onClick = { state.launchPermissionRequest() }) {
            Text(text = stringResource(R.string.vision_permission_request))
        }
    }
}

@Composable
fun CameraViewFinder(
    modifier: Modifier = Modifier,
    cameraTranslationViewModel: CameraTranslationViewModel,
    lifecycleOwner: LifecycleOwner,
) {
    val currentSurfaceRequest: SurfaceRequest? by cameraTranslationViewModel.surfaceRequests.collectAsStateWithLifecycle()
    val sensorRotationQuadrant by cameraTranslationViewModel.sensorRotationQuadrant.collectAsStateWithLifecycle()
    val liveTrackingActive by cameraTranslationViewModel.liveTrackingActive.collectAsStateWithLifecycle()
    val layoutCache = remember { OcrBlockLayoutCache() }

    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        cameraTranslationViewModel.viewfinder(context.applicationContext, lifecycleOwner)
    }

    Surface(
        modifier = Modifier.then(modifier),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        currentSurfaceRequest?.let { surfaceRequest ->
            Box {
                // GL viewfinder: the camera frame is consumed and presented
                // by our own GL thread — the pass the tracker (and later the
                // overlay composite) renders in.
                var viewFinder by remember { mutableStateOf<VerstaGlSurfaceView?>(null) }
                AndroidView(
                    factory = { ctx ->
                        VerstaGlSurfaceView(ctx.applicationContext)
                    },
                    update = { view ->
                        viewFinder = view
                        view.sensorRotationQuadrant = sensorRotationQuadrant
                        view.liveFrameSink = if (liveTrackingActive) {
                            cameraTranslationViewModel::onLiveGlFrame
                        } else {
                            null
                        }
                        view.setSurfaceRequest(surfaceRequest, ContextCompat.getMainExecutor(context))
                    },
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
                            detectTapGestures { tap ->
                                viewFinder?.let { view ->
                                    cameraTranslationViewModel.focusOnPoint(view, tap.x, tap.y)
                                }
                            }
                        },
                )

                Canvas(
                    modifier = Modifier.fillMaxSize(),
                    onDraw = {
                        // Live tracking composites the overlay in the GL pass
                        // (warped by the tracker homography each frame); this
                        // Canvas only presents the stills capture's result.
                        if (liveTrackingActive) return@Canvas
                        // Snapshot reads inside onDraw: pose publishes from the
                        // GL viewfinder thread invalidate this draw directly,
                        // with no recomposition round trip per camera frame.
                        val results = cameraTranslationViewModel.detectedBoxes
                        val frameWidth = cameraTranslationViewModel.frameWidth
                        val frameHeight = cameraTranslationViewModel.frameHeight
                        val blockTranslations = cameraTranslationViewModel.blockTranslations
                        if (frameWidth <= 0 || frameHeight <= 0) return@Canvas
                        val inferenceRect = RectF(0f, 0f, frameWidth.toFloat(), frameHeight.toFloat())
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

                        // Per-block screen quads with reading geometry. Column
                        // snapping happens once natively in canonical space
                        // (snapBlockTightRects), so these quads keep the
                        // homography's projective skew — that's what puts the
                        // page's perspective on both text and strips.
                        val blockQuads = results.mapNotNull { block ->
                            val quads = block.lines.mapNotNull { line ->
                                lineQuadOf(outputToScreenMatrix.mapPoints(line.points.asList()))
                                    ?.let { line to it }
                            }
                            if (quads.isEmpty()) null else block to quads
                        }

                        // One layout per block, wrap sticky across tracker ticks.
                        data class Draw(
                            val quads: List<Pair<CameraTranslationBlockLine, OcrLineQuad>>,
                            val paint: TextPaint,
                            val layout: OcrBlockLayout.Result?,
                        )
                        val draws = blockQuads.map { (block, quads) ->
                            val paint = OcrBlockRender.blockTextPaint(
                                bold = quads.any { it.first.fontWeight == FontWeight.BOLD }
                            )
                            // Translations land one publish late on a miss
                            // (geometry first, text follows asynchronously).
                            val trimmed = (blockTranslations[block.source] ?: "").trim()
                            // Lazily shape-once: a layout-cache hit never
                            // measures at all.
                            val measurer = lazy { OcrBlockRender.measureAtReference(paint, trimmed) }
                            val quadGeometries = quads.map { it.second }
                            val layout = if (trimmed.isEmpty()) null else layoutCache.layout(
                                text = trimmed,
                                lineWidths = OcrBlockRender.blockLineWidths(quadGeometries),
                                startSize = OcrBlockRender.blockStartSize(quadGeometries),
                                measure = { from, until, size -> measurer.value.measure(from, until, size) },
                            )
                            Draw(quads, paint, layout)
                        }

                        // Per-frame warp scratch: strip patches AND text draw
                        // through quad-corner matrices, re-posed every frame.
                        val stripPaint = OcrBlockRender.stripPaint()

                        // Two passes across ALL blocks: every erased
                        // background on the bottom layer, every glyph on
                        // top — strip pads overlap neighbouring blocks, so
                        // interleaving them per block would clip the text.
                        draws.forEach { draw ->
                            draw.quads.forEach { (line, _) ->
                                val strip = line.strip ?: return@forEach
                                val dst = outputToScreenMatrix.mapToArray(strip.points)
                                drawContext.canvas.nativeCanvas.drawBitmap(
                                    strip.bitmap, OcrBlockRender.stripMatrix(strip.bitmap, dst), stripPaint
                                )
                            }
                        }

                        draws.forEach { draw ->
                            val quads = draw.quads
                            val layout = draw.layout ?: return@forEach
                            // Per-block fitted size only — no cross-block
                            // unification (the reference shares one size per
                            // block and lets neighbouring blocks differ).
                            draw.paint.textSize = layout.textSize
                            // Projective text: the local band rect maps onto
                            // the tracked quad corners, so glyphs carry the
                            // page's perspective exactly like the strips do.
                            quads.forEachIndexed { index, (line, quad) ->
                                val segment = layout.segments.getOrElse(index) { "" }
                                if (segment.isEmpty()) return@forEachIndexed

                                draw.paint.color = line.colors.foreground.toArgb()
                                drawContext.canvas.nativeCanvas.save()
                                drawContext.canvas.nativeCanvas.concat(OcrBlockRender.textMatrix(quad))
                                drawContext.canvas.nativeCanvas.drawText(
                                    segment, OCR_TEXT_HORIZONTAL_INSET,
                                    OcrBlockRender.centeredBaselineY(draw.paint, quad.bandHeight),
                                    draw.paint
                                )
                                drawContext.canvas.nativeCanvas.restore()
                            }
                        }
                    }
                )
            }
        }
    }
}

