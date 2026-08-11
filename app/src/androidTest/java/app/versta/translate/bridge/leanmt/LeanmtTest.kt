package app.versta.translate.bridge.leanmt

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

// Real-driver tests: these load the native library on-device and exercise the
// JNI lifecycle guards. No model files are involved — a usable leanmt Model
// bundle cannot be fabricated without downloaded artifacts.
class LeanmtTest {

    @Test
    fun create_withCacheSizeZero_succeeds() {
        Leanmt(0L).close()
    }

    @Test
    fun create_withCacheSize_succeeds() {
        Leanmt(1024L).close()
    }

    @Test
    fun translate_withoutModel_throwsNotLoaded() {
        val leanmt = Leanmt(0L)

        try {
            leanmt.translate(arrayOf("hello"), 3, 128)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Translation model is not loaded", e.message)
        } finally {
            leanmt.close()
        }
    }

    @Test
    fun loadModel_withMissingFiles_throwsLoadError() {
        val leanmt = Leanmt(0L)

        try {
            leanmt.loadModel(
                LeanmtPackage(
                    "/nonexistent/model.bin",
                    "/nonexistent/vocab.spm",
                    "",
                    "/nonexistent/shortlist.bin",
                ),
                LeanmtModelConfig(6, 2, 2, 8),
            )
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Failed to load leanmt model from /nonexistent/model.bin", e.message)
        } finally {
            leanmt.close()
        }
    }

    @Test
    fun translate_afterClose_throwsNotInitialized() {
        val leanmt = Leanmt(0L)
        leanmt.close()

        try {
            leanmt.translate(arrayOf("hello"), 3, 128)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Service is not initialized", e.message)
        }
    }

    @Test
    fun close_twice_isIdempotent() {
        val leanmt = Leanmt(0L)

        leanmt.close()
        leanmt.close()
    }
}
