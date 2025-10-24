package app.versta.translate.adapter.outbound

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.MainApplication
import app.versta.translate.bridge.inference.PaddleOCR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ObjectCharacterRecognizer(
    private val onFrameProcessed: (List<PaddleOCR.OcrResults>, Bitmap?, Long) -> Unit
) : ImageAnalysis.Analyzer {
    // TODO: Make this dependancy injection through interface
    private val _paddleOCR = PaddleOCR(MainApplication.module.ortEnvironment)
    private val _languageViewModel = MainApplication.module.languageViewModel
    private val _translationViewModel = MainApplication.module.translationViewModel

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        _paddleOCR.load()
    }

    override fun analyze(imageProxy: ImageProxy) {
        val startTime = System.currentTimeMillis()
        val (results, bitmap) = _paddleOCR.processCameraFrame(imageProxy)
        Log.d(
            TAG,
            "Processed frame in ${System.currentTimeMillis() - startTime} ms with ${results.size} results"
        )

        scope.launch {
            handleResults(
                results = results,
                bitmap = bitmap,
                imageProxy = imageProxy
            )
        }
    }

    suspend fun handleResults(
        results: List<PaddleOCR.OcrResults>,
        bitmap: Bitmap?,
        imageProxy: ImageProxy
    ) {
        val languages = _languageViewModel.languagePair.first()
        if (languages == null) {
            imageProxy.close()
            return
        }

        val startTime = System.currentTimeMillis()
        results.map { result ->
            val translated = _translationViewModel.translate(result.text, languages)
            result.translated = translated
        }
        Log.d(
            TAG,
            "Translated frame in ${System.currentTimeMillis() - startTime} ms"
        )

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