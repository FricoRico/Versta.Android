package app.versta.translate.bridge.leanmt

/**
 * The translation operations
 * [app.versta.translate.adapter.outbound.BergamotTinyInference] drives:
 * loading a language-pair model, translating batches of text, and releasing
 * the service. Implemented by [Leanmt]; defined as an interface so the
 * inference adapter can be tested against a fake that never invokes the
 * native library.
 */
interface TranslationEngine : AutoCloseable {
    /**
     * Loads the model described by [pkg] with architecture [config], replacing
     * any previously loaded model. The translation cache survives the swap.
     */
    fun loadModel(pkg: LeanmtPackage, config: LeanmtModelConfig)

    /**
     * Translates each element of [texts] as one unit and returns the
     * translations in the same order.
     *
     * @throws IllegalStateException if no model has been loaded.
     */
    fun translate(
        texts: Array<String>,
        maxBeamWidth: Long,
        maxSequenceLength: Long,
    ): Array<String>

    override fun close()
}
