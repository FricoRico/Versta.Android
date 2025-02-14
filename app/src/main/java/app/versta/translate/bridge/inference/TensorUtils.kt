package app.versta.translate.bridge.inference

import app.versta.translate.utils.TensorUtils
import com.google.mlkit.common.sdkinternal.Cleaner
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TensorUtils {
    init {
        System.loadLibrary("app_versta_translate_bridge")
    }

    external fun closeBuffer(buffer: Buffer)
}