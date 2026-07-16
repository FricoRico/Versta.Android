package app.versta.translate.bridge.leanmt

/**
 * Native model configuration. Mirrors leanmt's `leanmt::Model::Config`
 * (encoder/decoder layers, feed-forward depth and attention heads). The
 * original `split_mode` option is not part of the pristine leanmt API and
 * is intentionally omitted.
 */
data class LeanmtModelConfig(
    val encoderLayers: Long,
    val decoderLayers: Long,
    val feedForwardDepth: Long,
    val numHeads: Long,
)
