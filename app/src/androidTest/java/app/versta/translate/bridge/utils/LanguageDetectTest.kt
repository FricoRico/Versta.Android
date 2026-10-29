package app.versta.translate.bridge.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Regression suite for the LanguageDetect native registry (NEU-253).
 *
 * The pre-fix registry was an unguarded `std::unordered_map` mutated by
 * construct/close on the main thread while detect traversed it from the
 * Default pool — concurrent insert vs traversal is UB and SIGSEGVed under
 * this exact thread shape on the host harness, and left orphaned entries on
 * the runs that survived. These tests run the same shape against the real
 * native library and audit the debug hook at the end.
 */
class LanguageDetectTest {
    private val englishSample =
        "This is a plain English sentence used to sanity-check language detection."

    @Test
    fun detectsEnglishText() {
        val detector = LanguageDetect()
        try {
            val result = detector.detectLanguage(englishSample)
            assertNotNull("detect on a live handle must return a result", result)
            assertEquals("en", result!!.language)
            assertTrue("sample text should be a confident detection", result.isReliable)
        } finally {
            detector.close()
        }
        assertEquals("registry must be empty after close", 0, LanguageDetect.instanceCount())
    }

    @Test
    fun detectAfterCloseReturnsNull() {
        val detector = LanguageDetect()
        detector.close()
        assertNull(
            "detect on a closed handle must miss the registry, not crash",
            detector.detectLanguage(englishSample),
        )
        assertEquals("registry must stay empty", 0, LanguageDetect.instanceCount())
    }

    @Test
    fun closeIsIdempotent() {
        val detector = LanguageDetect()
        detector.close()
        detector.close()
        assertEquals("registry must be empty after repeated close", 0, LanguageDetect.instanceCount())
    }

    @Test
    fun concurrentConstructCloseAndDetectLeavesRegistryEmpty() {
        val iterations = 1000
        val pool = Executors.newFixedThreadPool(3)

        val churn =
            Callable {
                repeat(iterations) {
                    val detector = LanguageDetect()
                    detector.close()
                }
            }
        val detect =
            Callable {
                repeat(iterations) {
                    val detector = LanguageDetect()
                    try {
                        detector.detectLanguage(englishSample)
                    } finally {
                        detector.close()
                    }
                }
            }

        val futures = listOf(pool.submit(churn), pool.submit(churn), pool.submit(detect))
        try {
            futures.forEach { it.get(120, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertEquals("registry must be empty after the stress run", 0, LanguageDetect.instanceCount())
    }

    @Test
    fun closeDuringInFlightDetectIsSafe() {
        val detector = LanguageDetect()
        val started = CountDownLatch(1)
        val detectorThread =
            thread {
                started.await()
                repeat(50_000) {
                    // Once close lands this is a null-returning registry miss;
                    // before close it runs on a live instance. Either way the
                    // native deferred-destruction contract must not crash.
                    detector.detectLanguage(englishSample)
                }
            }

        started.countDown()
        Thread.sleep(5)
        detector.close()
        detectorThread.join(TimeUnit.SECONDS.toMillis(60))

        assertFalse("detect loop must finish after mid-flight close", detectorThread.isAlive)
        assertEquals("registry must be empty after close-during-detect", 0, LanguageDetect.instanceCount())
    }
}
