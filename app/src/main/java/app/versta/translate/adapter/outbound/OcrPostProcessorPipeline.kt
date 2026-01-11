package app.versta.translate.adapter.outbound

import timber.log.Timber

/**
 * Pipeline that chains multiple OCR post-processors
 * Processors are executed sequentially in the order provided
 *
 * Example usage:
 * ```
 * val pipeline = OcrPostProcessorPipeline(listOf(
 *     TextStyleAnalysisPostProcessor(...),
 *     ParagraphGroupingPostProcessor(...)
 * ))
 * ```
 */
class OcrPostProcessorPipeline(
    private val processors: List<OcrPostProcessor>
) : OcrPostProcessor {

    override fun process(context: OcrPostProcessorContext): OcrPostProcessorContext {
        var currentContext = context

        processors.forEachIndexed { index, processor ->
            val startTime = System.currentTimeMillis()

            currentContext = processor.process(currentContext)

            val duration = System.currentTimeMillis() - startTime
            if (duration > 10) {
                Timber.tag(TAG).d(
                    "Processor ${processor::class.simpleName} took ${duration}ms"
                )
            }
        }

        return currentContext
    }

    companion object {
        private val TAG: String = OcrPostProcessorPipeline::class.java.simpleName
    }
}

