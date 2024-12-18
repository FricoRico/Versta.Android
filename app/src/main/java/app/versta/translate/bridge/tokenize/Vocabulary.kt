package app.versta.translate.bridge.tokenize

object Vocabulary {
    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    external fun load(filePath: String): List<String>
}