package app.versta.translate.adapter.outbound

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ObjectCharacterRecognitionAnalyzer(
    private val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference,
    private val beforeFrameProcessing: () -> Unit = {},
    private val onFrameProcessed: suspend (List<ObjectCharacterRecogniserResult>, Long) -> Unit,
) : ImageAnalysis.Analyzer {
    // TODO: Make this dependancy injection through interface
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun analyze(imageProxy: ImageProxy) {
        beforeFrameProcessing()

        val startTime = System.currentTimeMillis()
        val results = objectCharacterRecognitionInference.process(imageProxy)
        Log.d(
            TAG,
            "Processed frame in ${System.currentTimeMillis() - startTime} ms with ${results.size} results"
        )

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