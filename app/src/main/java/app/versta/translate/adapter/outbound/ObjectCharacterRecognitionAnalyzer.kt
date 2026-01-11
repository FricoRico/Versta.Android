package app.versta.translate.adapter.outbound

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ObjectCharacterRecognitionAnalyzer(
    private val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference,
    private val postProcessor: OcrPostProcessor? = null,
    private val beforeFrameProcessing: () -> Unit = {},
    private val onFrameProcessed: suspend (List<ObjectCharacterRecogniserResult>, Long) -> Unit,
) : ImageAnalysis.Analyzer {
    // TODO: Make this dependency injection through interface
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun analyze(imageProxy: ImageProxy) {
        beforeFrameProcessing()

        val rawResults = objectCharacterRecognitionInference.process(imageProxy)

        // Apply post-processor pipeline if available
        val results = if (postProcessor != null && rawResults.isNotEmpty()) {
            // Create context with ImageProxy and actual detect buffer for pipeline
            val context = OcrPostProcessorContext(
                imageProxy = imageProxy,
                results = rawResults,
                detectResultBuffer = objectCharacterRecognitionInference.getCachedDetectResultBuffer()
            )

            val processedContext = postProcessor.process(context)
            processedContext.results
        } else {
            rawResults
        }

        scope.launch {
            onFrameProcessed(
                results,
                imageProxy.imageInfo.timestamp
            )

            imageProxy.close()
        }
    }

//    suspend fun handleResults(
//        results: List<ObjectCharacterRecogniserResult>,
//        imageProxy: ImageProxy
//    ) {
//        val languages = languageViewModel.languagePair.first()
//        if (languages == null) {
//            imageProxy.close()
//            return
//        }
//
//        val startTime = System.currentTimeMillis()
//        results.map { result ->
//            val translated = translationViewModel.translate(result.text, languages)
//            result.translated = translated
//        }
//        Log.d(
//            TAG,
//            "Translated frame in ${System.currentTimeMillis() - startTime} ms"
//        )
//    }

    companion object {
        private val TAG: String = ObjectCharacterRecognitionAnalyzer::class.java.simpleName
    }
}