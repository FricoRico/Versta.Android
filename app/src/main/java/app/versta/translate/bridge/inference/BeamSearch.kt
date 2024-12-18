package app.versta.translate.bridge.inference

object BeamSearch {
    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    external fun topKIndices(array: FloatArray, k: Int): IntArray
}