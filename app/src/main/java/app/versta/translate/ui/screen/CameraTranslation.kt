package app.versta.translate.ui.screen

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.MockInference
import app.versta.translate.adapter.outbound.MockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.TextRecognitionViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.spacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.compose.ui.tooling.preview.Preview as ComposablePreview

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraTranslation(
    navController: NavController,
    textRecognitionViewModel: TextRecognitionViewModel,
    translationViewModel: TranslationViewModel,
    modifier: Modifier = Modifier
) {
//    val translationViewModel = viewModel<TranslationViewModel>(
//        factory = viewModelFactory {
//            MainApplication.viewModel.translationViewModel
//        }
//    )

    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )

//    val stableBlocks by textRecognitionViewModel.stableBlocks.collectAsStateWithLifecycle()
//    val transformMatrix by textRecognitionViewModel.transformMatrix
//    val rotationCompensation by textRecognitionViewModel.rotationCompensation
//    val safeArea by textRecognitionViewModel.safeArea

//    val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

//    val textRecognitionProcessor = TextRecognitionProcessor(
//        TextRecognizerOptions.Builder().build()
//    ) { imageProxy, text ->
//        textRecognitionViewModel.transformMatrix(imageProxy, previewView)
//        textRecognitionViewModel.processFrame(text)
//
//        val startTime = System.currentTimeMillis()
//        processingScope.launch {
//            val texts = stableBlocks.map { it.text }
//            if (texts.isEmpty()) return@launch
//
//            val output = translationViewModel.translate(texts)
//            val elapsedTime = System.currentTimeMillis() - startTime
//        }
//    }

//    val imageAnalyzer = ImageAnalysis.Builder()
//        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
//        .build()
//        .also {
//            it.setAnalyzer(ContextCompat.getMainExecutor(context), textRecognitionProcessor)
//        }
//
//
//    LaunchedEffect(lensFacing) {
//        val cameraProvider = context.getCameraProvider()
//        cameraProvider.unbindAll()
//        cameraProvider.bindToLifecycle(lifecycleOwner, cameraxSelector, preview, imageAnalyzer)
//        preview.surfaceProvider = previewView.surfaceProvider
//    }
//
//    val backgroundPadding = 16f
//    val backgroundPaint = Paint().apply {
//        color = 0x7F000000
//    }
//
//    val textPaintNotTranslated = Paint().apply {
//        color = 0xFFFF0000.toInt()
//        textSize = 16f
//    }
//
//    val aspectRatio = (preview.resolutionInfo?.resolution?.height?.toFloat()
//        ?: 3f) / (preview.resolutionInfo?.resolution?.width?.toFloat() ?: 4f)

    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .clip(MaterialTheme.shapes.extraLarge)
            .fillMaxSize()
    ) {
        if (cameraPermissionState.status.isGranted) {
            CameraViewFinder(
                textRecognitionViewModel = textRecognitionViewModel,
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                TopAppBar(
                    title = { },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                stringResource(R.string.back)
                            )
                        }
                    },
                )
            },
            content = { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    if (!cameraPermissionState.status.isGranted) {
                        CameraPermissionRequest(
                            showRationale = cameraPermissionState.status.shouldShowRationale,
                            onPermissionRequested = {
                                cameraPermissionState.launchPermissionRequest()
                            }
                        )
                    }
                }
            }
        )
//

//        Canvas(modifier = Modifier.fillMaxSize()) {
//            drawContext.canvas.nativeCanvas.apply {
//                val path = Path().apply {
//                    fillType = PathFillType.EvenOdd
//                    // Outer rectangle (full canvas)
//                    addRect(Rect(0, 0, size.width.toInt(), size.height.toInt()).toComposeRect())
//                    // Inner rectangle (safeRect) that we want to cut out
//                    addRect(safeArea.toComposeRect())
//                }
//
//                // Draw the path with red color
//                drawPath(
//                    path = path,
//                    color = Color.Red.copy(alpha = 0.3f),
//                )
//                for (block in stableBlocks) {
//                    val boundingBox = block.boundingBox.toRectF()
//                    transformMatrix.mapRect(boundingBox)
//
//                    save()
//
//                    rotate(
//                        block.blockAngle + rotationCompensation,
//                        boundingBox.centerX(),
//                        boundingBox.centerY()
//                    )
//
//                    drawRect(
//                        boundingBox.left - backgroundPadding,
//                        boundingBox.top - backgroundPadding,
//                        boundingBox.right + backgroundPadding,
//                        boundingBox.bottom + backgroundPadding,
//                        backgroundPaint
//                    )
//
//                    restore()
//
//                    for (line in block.lines) {
//                        val lineBoundingBox = line.boundingBox?.toRectF() ?: continue
//                        transformMatrix.mapRect(lineBoundingBox)
//
//                        // Save the current canvas state before applying transformations
//                        save()
//
//                        // Rotate the canvas around the center of the bounding box
//                        rotate(
//                            line.angle + rotationCompensation,
//                            lineBoundingBox.centerX(),
//                            lineBoundingBox.centerY()
//                        )
//
//                        val bounds = Rect()
//                        textPaintNotTranslated.getTextBounds(line.text, 0, line.text.length, bounds)
//
//                        val scale = minOf(
//                            lineBoundingBox.width() / bounds.width(),
//                            lineBoundingBox.height() / bounds.height()
//                        )
//
//                        textPaintNotTranslated.textSize *= scale
//
//                        drawText(
//                            line.text,
//                            lineBoundingBox.left,
//                            lineBoundingBox.top + textPaintNotTranslated.textSize,
//                            textPaintNotTranslated
//                        )
//
//                        // Restore the canvas to the previous state before rotation
//                        restore()
//                    }
//                }
//            }
//        }
    }
}

@Composable
fun CameraPermissionRequest(
    showRationale: Boolean,
    onPermissionRequested: () -> Unit
) {
    // TODO: Add a better rationale
    val text = if (showRationale) {
        "Camera permission is required to use this feature"
    } else {
        "Camera permission is required to use this feature"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaterialTheme.spacing.large)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onPermissionRequested,
            modifier = Modifier.padding(top = MaterialTheme.spacing.large)
        ) {
            Text(text = "Request permission")
        }
    }
}

@Composable
fun CameraViewFinder(
    textRecognitionViewModel: TextRecognitionViewModel,
    modifier: Modifier = Modifier,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current
) {
    val surfaceRequest by textRecognitionViewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(lifecycleOwner) {
        textRecognitionViewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    surfaceRequest?.let {
        CameraXViewfinder(
            surfaceRequest = it,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
@ComposablePreview(showBackground = true)
fun CameraPreview() {
    CameraTranslation(
        navController = rememberNavController(),
        textRecognitionViewModel = TextRecognitionViewModel(),
        translationViewModel = TranslationViewModel(
            tokenizer = MockTokenizer(),
            model = MockInference(),
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            translationPreferenceRepository = TranslationPreferenceMemoryRepository()
        )
    )
}