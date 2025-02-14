package app.versta.translate.bridge.inference

import java.nio.Buffer

object TensorUtils {
    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    external fun closeBuffer(buffer: Buffer)
}