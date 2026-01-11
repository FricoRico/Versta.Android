package app.versta.translate.adapter.outbound

/**
 * Post-processor interface for OCR results.
 * Implementations can transform, filter, or group OCR results after inference.
 *
 * Post-processors receive a context object containing the image reference and results,
 * allowing them to access the original image data for analysis.
 */
interface OcrPostProcessor {
    /**
     * Process OCR results within the given context.
     *
     * @param context The OCR context containing image and results
     * @return Updated context with processed results
     */
    fun process(context: OcrPostProcessorContext): OcrPostProcessorContext
}

