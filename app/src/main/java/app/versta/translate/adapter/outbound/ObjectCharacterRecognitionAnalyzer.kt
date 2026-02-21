package app.versta.translate.adapter.outbound

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.versta.translate.core.entity.FontWeight
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ObjectCharacterRecognitionAnalyzer(
    private val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference,
    private val tokenizer: PaddleObjectCharacterRecognitionTokenizer,
    private val postProcessor: OcrPostProcessor? = null,
    private val beforeFrameProcessing: () -> Unit = {},
    private val onFrameProcessed: suspend (List<ObjectCharacterRecogniserResult>, Long) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun analyze(imageProxy: ImageProxy) {
        beforeFrameProcessing()

        val rawResults = objectCharacterRecognitionInference.run(imageProxy)

        val decodedResults = rawResults.regions.map { region ->
            ObjectCharacterRecogniserResult(
                points = region.points,
                score = region.score,
                tokens = region.tokens,
                text = tokenizer.decode(region.tokens),
                colors = region.colors,
                fontSize = region.fontSize,
                lineHeight = region.lineHeight,
                fontWeight = FontWeight.fromInt(region.fontWeight)
            )
        }

        val results = if (postProcessor != null && decodedResults.isNotEmpty()) {
            val context = OcrPostProcessorContext(
                imageProxy = imageProxy,
                results = decodedResults
            )

            val processedContext = postProcessor.process(context)
            processedContext.results
        } else {
            decodedResults
        }

        scope.launch {
            onFrameProcessed(
                results,
                imageProxy.imageInfo.timestamp
            )

            imageProxy.close()
        }
    }

    companion object {
        private val TAG: String = ObjectCharacterRecognitionAnalyzer::class.java.simpleName
    }
}
