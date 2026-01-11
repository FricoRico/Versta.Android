package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.inference.OcrTextAnalyzer
import app.versta.translate.core.entity.ObjectCharacterRecogniserResult
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Post-processor that extracts text style metrics (colors, font size, font weight)
 * from the original image using OcrTextAnalyzer
 */
class TextStyleAnalysisPostProcessor(
    private val ocrTextAnalyzer: OcrTextAnalyzer,
    private val recognizeSize: Int = 960
) : OcrPostProcessor {

    override fun process(context: OcrPostProcessorContext): OcrPostProcessorContext {
        val results = context.results

        if (results.isEmpty()) {
            return context
        }

        return try {
            // Create bounding box buffer from actual result points
            // Each result has 4 points (corners), each point is 2 ints (x, y)
            // Format: [count, box0_p0_x, box0_p0_y, box0_p1_x, box0_p1_y, ..., box0_p3_x, box0_p3_y, box1_...]
            val boxesBuffer = ByteBuffer.allocateDirect(4 + results.size * 4 * 2 * 4) // 4 bytes for count, then 8 ints per box
                .order(ByteOrder.nativeOrder())

            // Write count
            boxesBuffer.putInt(results.size)

            // Write each bounding box
            results.forEach { result ->
                // Points are expected in order: top-left, top-right, bottom-right, bottom-left
                result.points.forEach { point ->
                    boxesBuffer.putInt(point.x.toInt())
                    boxesBuffer.putInt(point.y.toInt())
                }
            }

            boxesBuffer.rewind()

            // Analyze all text regions in one JNI call
            val metrics = ocrTextAnalyzer.analyzeTextRegions(
                imageProxy = context.imageProxy,
                boxesBuffer = boxesBuffer,
                recognizeSize = recognizeSize
            )

            // Map metrics back to results
            val updatedResults = results.mapIndexed { index, result ->
                val metric = metrics.getOrNull(index)
                if (metric != null) {
                    ObjectCharacterRecogniserResult(
                        points = result.points,
                        score = result.score,
                        tokens = result.tokens,
                        text = result.text,
                        colors = metric.colors,
                        lines = result.lines,
                        fontSize = metric.fontSize,
                        lineHeight = metric.lineHeight,  // NEW: Pass line height
                        fontWeight = metric.fontWeight
                    )
                } else {
                    // Keep original result if no metrics available
                    result
                }
            }

            context.copy(results = updatedResults)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error analyzing text styles, keeping original results")
            context
        }
    }

    companion object {
        private val TAG: String = TextStyleAnalysisPostProcessor::class.java.simpleName
    }
}

