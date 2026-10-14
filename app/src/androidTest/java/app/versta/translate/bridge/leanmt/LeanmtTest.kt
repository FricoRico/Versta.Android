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
    fun modelConfig_threads_defaultsToLibraryDefault() {
        // 0 hands the thread-budget choice to leanmt (process default /
        // LEANMT_THREADS); the bridge must not impose its own policy.
        assertEquals(0L, LeanmtModelConfig(6, 2, 2, 8).threads)
    }

    @Test
    fun loadModel_withThreads_reachesNativeLoad() {
        // Missing files fail inside the native loader after the threads
        // argument crosses JNI; an UnsatisfiedLinkError here would mean the
        // Kotlin/native create signatures have drifted.
        val leanmt = Leanmt(0L)

        try {
            leanmt.loadModel(
                LeanmtPackage(
                    "/nonexistent/model.bin",
                    "/nonexistent/vocab.spm",
                    "",
                    "/nonexistent/shortlist.bin",
                ),
                LeanmtModelConfig(6, 2, 2, 8, threads = 2),
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
