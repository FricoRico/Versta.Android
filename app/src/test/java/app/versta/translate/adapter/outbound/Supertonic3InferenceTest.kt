package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.speech.Supertonic3Engine
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

class Supertonic3InferenceTest {
    /**
     * Mirrors the native engine's style contract: every load replaces the
     * engine and drops its style, and synthesis needs a bound style.
     */
    private class FakeEngine : Supertonic3Engine {
        var live = false
        var bound: Path? = null
        var loads = 0
        var closes = 0
        var lastSpeed: Float? = null
        var rate = 0

        override val sampleRate: Int
            get() = rate

        override fun load(
            models: Map<String, Path>,
            config: Path,
            threads: Int,
        ) {
            loads++
            live = true
            bound = null
        }

        override fun setStyle(path: Path?) {
            check(live) { "Not initialized" }
            bound = path
        }

        override fun synthesize(
            tokens: LongArray,
            speed: Float,
        ): FloatArray {
            check(live) { "Not initialized" }
            checkNotNull(bound) { "no style bound" }
            lastSpeed = speed
            return FloatArray(tokens.size) { 0.5f }
        }

        override fun close() {
            closes++
            live = false
            bound = null
        }
    }

    private val files =
        VoiceModelInferenceFiles(
            models = Supertonic3.MODEL_STAGES.associateWith { Paths.get("$it.mnn") },
            config = Paths.get("tts.json"),
        )

    private val voice = Paths.get("voice_styles/F1.json")

    @Test
    fun `voice selected before load is bound once the engine loads`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        inference.setVoice(voice)
        inference.load(files, threads = 2)

        assertEquals(voice, engine.bound)
        assertArrayEquals(floatArrayOf(0.5f, 0.5f), inference.synthesize(longArrayOf(1, 2), 1f), 0f)
    }

    @Test
    fun `reloading keeps the selected voice bound`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        inference.load(files, threads = 2)
        inference.setVoice(voice)
        inference.load(files, threads = 4)

        assertEquals(2, engine.loads)
        assertEquals(voice, engine.bound)
    }

    @Test
    fun `cleared voice stays cleared across a reload`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        inference.load(files, threads = 2)
        inference.setVoice(voice)
        inference.clearVoice()
        inference.load(files, threads = 2)

        assertNull(engine.bound)
    }

    @Test
    fun `synthesis before load fails without reaching the engine`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        assertThrows(IllegalStateException::class.java) {
            inference.synthesize(longArrayOf(1), 1f)
        }
        assertNull(engine.lastSpeed)
    }

    @Test
    fun `synthesis after close fails without reaching the engine`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)
        inference.setVoice(voice)
        inference.load(files, threads = 2)

        inference.close()

        assertThrows(IllegalStateException::class.java) {
            inference.synthesize(longArrayOf(1), 1f)
        }
        assertNull(engine.lastSpeed)
    }

    @Test
    fun `close releases a loaded engine once`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)
        inference.load(files, threads = 2)

        inference.close()
        inference.close()

        assertEquals(1, engine.closes)
    }

    @Test
    fun `close without a loaded engine does not touch it`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        inference.close()

        assertEquals(0, engine.closes)
    }

    @Test
    fun `speed is clamped to the supported range`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)
        inference.setVoice(voice)
        inference.load(files, threads = 2)

        inference.synthesize(longArrayOf(1), 9f)
        assertEquals(MAX_SUPERTONIC_SPEED, engine.lastSpeed)

        inference.synthesize(longArrayOf(1), 0.1f)
        assertEquals(MIN_SUPERTONIC_SPEED, engine.lastSpeed)
    }

    @Test
    fun `load rejects a bundle without the four model stages`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)
        val partial = files.copy(models = files.models - "vocoder")

        assertThrows(IllegalArgumentException::class.java) {
            inference.load(partial, threads = 2)
        }
        assertEquals(0, engine.loads)
    }

    @Test
    fun `load rejects a bundle without tts json`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        assertThrows(IllegalArgumentException::class.java) {
            inference.load(files.copy(config = null), threads = 2)
        }
        assertEquals(0, engine.loads)
    }

    @Test
    fun `sample rate falls back to the published rate until the engine reports one`() {
        val engine = FakeEngine()
        val inference = Supertonic3Inference(engine)

        assertEquals(Supertonic3.SAMPLE_RATE, inference.sampleRate)

        engine.rate = 24_000
        assertEquals(24_000, inference.sampleRate)
    }
}
