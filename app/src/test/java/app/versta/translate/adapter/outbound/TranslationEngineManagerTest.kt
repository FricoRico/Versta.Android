package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.leanmt.LeanmtModelConfig
import app.versta.translate.bridge.leanmt.LeanmtPackage
import app.versta.translate.bridge.leanmt.TranslationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class TranslationEngineManagerTest {
    private class FakeEngine : TranslationEngine {
        var closed = false

        override fun loadModel(
            pkg: LeanmtPackage,
            config: LeanmtModelConfig,
        ) = Unit

        override fun translate(
            texts: Array<String>,
            maxBeamWidth: Long,
            maxSequenceLength: Long,
        ): Array<String> = texts

        override fun close() {
            closed = true
        }
    }

    private class RecordingManager(
        defaultCacheSize: Long = DEFAULT_CACHE_SIZE.toLong(),
    ) {
        val requestedCacheSizes = mutableListOf<Long>()
        val engines = mutableListOf<FakeEngine>()

        val manager =
            TranslationEngineManager(defaultCacheSize) { cacheSize ->
                requestedCacheSizes += cacheSize
                FakeEngine().also { engines += it }
            }
    }

    @Test
    fun `initial inference pair shares one service without constructing native engines eagerly`() {
        val recording = RecordingManager()

        assertEquals(emptyList<Long>(), recording.requestedCacheSizes)

        val intermediate = recording.manager.intermediateModel
        val output = recording.manager.outputModel

        assertEquals(listOf(DEFAULT_CACHE_SIZE.toLong()), recording.requestedCacheSizes)
        assertNotSame(intermediate, output)
    }

    @Test
    fun `createInference builds a fresh service per call with the requested cache size`() {
        val recording = RecordingManager()

        val first = recording.manager.createInference(2048)
        val second = recording.manager.createInference(512)

        assertEquals(listOf(2048L, 512L), recording.requestedCacheSizes)
        assertNotSame(first, second)
        assertNotSame(recording.engines[0], recording.engines[1])
    }

    @Test
    fun `recreated inferences do not touch the initial shared service`() {
        val recording = RecordingManager()

        recording.manager.createInference(0)
        assertEquals(1, recording.engines.size)

        recording.manager.intermediateModel
        recording.manager.outputModel

        assertEquals(listOf(0L, DEFAULT_CACHE_SIZE.toLong()), recording.requestedCacheSizes)
    }
}
