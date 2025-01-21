package app.versta.translate.bridge.inference

object BeamSearch {
    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    external fun softmax(logits: FloatArray): FloatArray

    external fun topKIndices(probabilities: FloatArray, k: Int): IntArray

    external fun minPIndices(probabilities: FloatArray, p: Float): IntArray
}