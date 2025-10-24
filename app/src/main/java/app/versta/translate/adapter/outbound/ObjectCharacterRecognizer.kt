package app.versta.translate.adapter.outbound

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.MainApplication
import app.versta.translate.bridge.inference.PaddleOCR
import app.versta.translate.core.entity.ObjectCharacterRecognitionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * @deprecated This class has been replaced by ObjectCharacterRecognizerAnalyzer.
 * The translation logic has been moved to VisionViewModel to better separate concerns.
 * Use ObjectCharacterRecognizerAnalyzer for OCR analysis and VisionViewModel for managing
 * the analysis pipeline and translation.
 */
@Deprecated(
    message = "Use ObjectCharacterRecognizerAnalyzer instead. Translation logic moved to VisionViewModel.",
    replaceWith = ReplaceWith("ObjectCharacterRecognizerAnalyzer", "app.versta.translate.adapter.outbound.ObjectCharacterRecognizerAnalyzer")
)
class ObjectCharacterRecognizer(
    private val onFrameProcessed: (List<ObjectCharacterRecognitionResult>, Bitmap?, Long) -> Unit
) : ImageAnalysis.Analyzer {
    // TODO: Make this dependency injection through interface
    private val _tokenizer = PaddleTokenizer()
    private val _paddleOCR = PaddleOCR(
        ortEnvironment = MainApplication.module.ortEnvironment,
        tokenizer = _tokenizer
    )
    private val _languageViewModel = MainApplication.module.languageViewModel
    private val _translationViewModel = MainApplication.module.translationViewModel

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        _paddleOCR.load()
    }

    override fun analyze(imageProxy: ImageProxy) {
        @Suppress("DEPRECATION")
        val (results, bitmap) = _paddleOCR.processCameraFrame(imageProxy)

        scope.launch {
            handleResults(
                results = results.map { oldResult ->
                    ObjectCharacterRecognitionResult(
                        points = oldResult.points,
                        score = oldResult.score,
                        tokens = oldResult.tokens,
                        text = oldResult.text,
                        translated = oldResult.translated,
                        colors = app.versta.translate.core.entity.ObjectCharacterRecognitionColors(
                            background = oldResult.colors.background,
                            foreground = oldResult.colors.foreground
                        )
                    )
                },
                bitmap = bitmap,
                imageProxy = imageProxy
            )
        }
    }

    suspend fun handleResults(
        results: List<ObjectCharacterRecognitionResult>,
        bitmap: Bitmap?,
        imageProxy: ImageProxy
    ) {
        val languages = _languageViewModel.languagePair.first()
        if (languages == null) {
            imageProxy.close()
            return
        }

        results.map { result ->
            val translated = _translationViewModel.translate(result.text, languages)
            result.translated = translated
        }

        onFrameProcessed(
            results,
            bitmap,
            imageProxy.imageInfo.timestamp
        )

        imageProxy.close()
    }

    companion object {
        private val TAG: String = ObjectCharacterRecognizer::class.java.simpleName
    }
}