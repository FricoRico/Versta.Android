package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.whisper.WhisperRecognizerHandle
import app.versta.translate.bridge.whisper.WhisperSegmentCallback
import app.versta.translate.core.entity.SpeechRecognitionInferenceFiles
import app.versta.translate.core.entity.SpeechRecognitionMetrics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Path

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WhisperSpeechRecognitionTest {

    private class FakeModel : AutoCloseable {
        var closeCalls = 0
        override fun close() { closeCalls++ }
    }

    private class FakeRecognizer : WhisperRecognizerHandle {
        var feedCalls = 0
        var processResult: Long = 0L
        var processCalls = 0
        var flushCalls = 0
        var resetCalls = 0
        var closeCalls = 0
        var lastCarriedContext: IntArray? = null
        var metricsResult: SpeechRecognitionMetrics? = null
        var callback: WhisperSegmentCallback? = null

        override fun feed(pcm: FloatArray) { feedCalls++ }
        override fun process(): Long { processCalls++; return processResult }
        override fun flush(): Long { flushCalls++; return 0L }
        override fun reset() { resetCalls++ }
        override fun setCarriedContext(tokens: IntArray) { lastCarriedContext = tokens }
        override fun metrics() = metricsResult
        override fun close() { closeCalls++ }

        fun emit(text: String, startMs: Long, endMs: Long, tokens: IntArray = intArrayOf(1, 2)) {
            callback?.onSegment(text, startMs, endMs, tokens)
        }
    }

    private class FakeCapture(val feed: WhisperRecognizerHandle) : CaptureHandle {
        var startCalls = 0
        var stopCalls = 0
        var startError: IllegalStateException? = null
        override fun start(scope: CoroutineScope) {
            startCalls++
            startError?.let { throw it }
        }
        override fun stop() { stopCalls++ }
        override suspend fun join() {}
    }

    private fun buildInference(
        scheduler: TestCoroutineScheduler,
        modelHook: (FakeModel) -> Unit = {},
        recognizerHook: (FakeRecognizer) -> Unit = {},
        captureHook: (FakeCapture) -> Unit = {},
    ): WhisperSpeechRecognition {
        return WhisperSpeechRecognition(
            modelFactory = { _, _, _ -> FakeModel().also(modelHook) },
            recognizerFactory = { _, callback, _ ->
                FakeRecognizer().also { it.callback = callback }.also(recognizerHook)
            },
            captureFactory = { FakeCapture(it).also(captureHook) },
            processDispatcher = StandardTestDispatcher(scheduler),
        )
    }

    private fun files(model: String = "model.bin"): SpeechRecognitionInferenceFiles {
        return SpeechRecognitionInferenceFiles(Path.of(model), Path.of("vad.bin"))
    }

    private fun metrics(rtf: Double): SpeechRecognitionMetrics {
        return SpeechRecognitionMetrics(
            passCount = 1,
            abortCount = 0,
            vadSkipCount = 0,
            processedAudioSec = 3.0,
            commitComputeMs = 2000.0,
            rtf = rtf,
            lastPassElapsedMs = 100,
            lastPassWindowMs = 100,
            lastPassNSamples = 16000,
            lastPassAudioCtx = 0,
            lastPassMaxTokens = 512,
            lastPassBudgetMs = 1000,
            lastPassResult = 0,
            lastPassWasFlush = false,
            lastPassEncodeMs = 10f,
            lastPassDecodeMs = 20f,
            lastPassBatchdMs = 5f,
            lastPassNEncode = 1,
            lastPassNDecode = 1,
            lastPassNBatchd = 1,
        )
    }

    private suspend fun waitForCondition(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        withContext(Dispatchers.Default) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!condition()) {
                if (System.currentTimeMillis() > deadline) {
                    fail("Condition not met within ${timeoutMs}ms")
                }
                delay(10)
            }
        }
    }

    @Test
    fun isDegenerate_emptyText() {
        assertTrue(WhisperSpeechRecognition.isDegenerate(""))
    }

    @Test
    fun isDegenerate_whitespaceOnly() {
        assertTrue(WhisperSpeechRecognition.isDegenerate("   "))
        assertTrue(WhisperSpeechRecognition.isDegenerate("\t\n "))
    }

    @Test
    fun isDegenerate_punctuationOnly() {
        assertTrue(WhisperSpeechRecognition.isDegenerate("."))
        assertTrue(WhisperSpeechRecognition.isDegenerate("..."))
        assertTrue(WhisperSpeechRecognition.isDegenerate("!?"))
        assertTrue(WhisperSpeechRecognition.isDegenerate(";:"))
    }

    @Test
    fun isDegenerate_cjkPunctuationOnly() {
        assertTrue(WhisperSpeechRecognition.isDegenerate("。"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("？！"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("。！，．：；？"))
    }

    @Test
    fun isDegenerate_orphanContractions() {
        assertTrue(WhisperSpeechRecognition.isDegenerate("'s"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("'d"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("'m"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("'re"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("'ve"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("'ll"))
    }

    @Test
    fun isDegenerate_curlyApostropheContractions() {
        assertTrue(WhisperSpeechRecognition.isDegenerate("’s"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("’ll"))
        assertTrue(WhisperSpeechRecognition.isDegenerate("’ve"))
    }

    @Test
    fun isDegenerate_normalText() {
        assertFalse(WhisperSpeechRecognition.isDegenerate("hello"))
        assertFalse(WhisperSpeechRecognition.isDegenerate("hello world"))
        assertFalse(WhisperSpeechRecognition.isDegenerate("don't"))
        assertFalse(WhisperSpeechRecognition.isDegenerate("won't stop"))
        assertFalse(WhisperSpeechRecognition.isDegenerate("hello."))
    }

    @Test
    fun isDegenerate_cjkText() {
        assertFalse(WhisperSpeechRecognition.isDegenerate("北京欢迎你"))
        assertFalse(WhisperSpeechRecognition.isDegenerate("的"))
        assertFalse(WhisperSpeechRecognition.isDegenerate("'abc"))
    }

    @Test
    fun load_createsModelRecognizerAndCapture() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val captures = mutableListOf<FakeCapture>()
        val inference = buildInference(
            testScheduler,
            { models.add(it) },
            { recognizers.add(it) },
            { captures.add(it) },
        )
        inference.setSourceLanguage("en")

        inference.load(files())

        assertEquals(1, models.size)
        assertEquals(1, recognizers.size)
        assertEquals(1, captures.size)
        assertSame(recognizers[0], captures[0].feed)
        inference.close()
    }

    @Test
    fun load_sameModelAndLanguage_reusesEverything() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")

        inference.load(files())
        inference.load(files())

        assertEquals(1, models.size)
        assertEquals(1, recognizers.size)
        inference.close()
    }

    @Test
    fun load_languageChange_recreatesRecognizerKeepsModel() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files())

        inference.setSourceLanguage("nl")
        inference.load(files())

        assertEquals(1, models.size)
        assertEquals(2, recognizers.size)
        waitForCondition { recognizers[0].closeCalls == 1 }
        inference.close()
    }

    @Test
    fun load_modelChange_tearsDownAndReloads() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files("model.bin"))

        inference.load(files("model-2.bin"))

        assertEquals(2, models.size)
        assertEquals(2, recognizers.size)
        waitForCondition { models[0].closeCalls == 1 }
        assertEquals(0, models[1].closeCalls)
        inference.close()
    }

    @Test
    fun start_notLoaded_throws() = runTest {
        val inference = buildInference(testScheduler)

        try {
            inference.start(this)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Speech recognition is not loaded", e.message)
        }
    }

    @Test
    fun start_whileListening_isIgnored() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files())
        inference.start(this)
        recognizers[0].emit("hello", 0, 100)

        inference.start(this)

        assertEquals(listOf("hello"), inference.segments.value.map { it.text })
        inference.close()
    }

    @Test
    fun start_whileDraining_isIgnored() = runTest {
        val inference = buildInference(testScheduler)
        inference.setSourceLanguage("en")
        inference.load(files())
        inference.start(this)
        inference.stop()

        inference.start(this)

        assertFalse(inference.listening.value)
        assertTrue(inference.finalizing.value)
        inference.close()
    }

    @Test
    fun start_whenMicUnavailable_throwsCaptureExceptionAndStaysIdle() = runTest {
        val captures = mutableListOf<FakeCapture>()
        val inference = buildInference(testScheduler, captureHook = {
            it.startError = IllegalStateException("Microphone not available")
            captures.add(it)
        })
        inference.setSourceLanguage("en")
        inference.load(files())

        try {
            inference.start(this)
            fail("Expected MicrophoneCaptureException")
        } catch (_: MicrophoneCaptureException) {
        }

        assertFalse(inference.listening.value)
        assertTrue(inference.segments.value.isEmpty())
        inference.close()
    }

    @Test
    fun stop_drainsBufferedAudio() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files())
        recognizers[0].processResult = 0L
        inference.start(this)

        inference.stop()

        assertFalse(inference.listening.value)
        assertTrue(inference.finalizing.value)

        advanceUntilIdle()

        assertEquals(1, recognizers[0].flushCalls)
        assertEquals(1, recognizers[0].resetCalls)
        assertFalse(inference.listening.value)
        assertFalse(inference.finalizing.value)
        inference.close()
    }

    @Test
    fun stop_whenNotListening_isNoOp() = runTest {
        val captures = mutableListOf<FakeCapture>()
        val inference = buildInference(testScheduler, captureHook = { captures.add(it) })

        inference.stop()

        assertEquals(0, captures.size)
    }

    @Test
    fun segmentCallback_filtersDegenerateAndTrims() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files())
        inference.start(this)
        val recognizer = recognizers[0]

        recognizer.emit("  Hello world  ", 0, 100)
        recognizer.emit("...", 200, 300)
        recognizer.emit("'s", 400, 500)
        recognizer.emit("   ", 600, 700)
        recognizer.emit("北京欢迎你", 800, 900)

        assertEquals(
            listOf("Hello world", "北京欢迎你"),
            inference.segments.value.map { it.text },
        )
        inference.close()
    }

    @Test
    fun segmentCallback_carriesContextAcrossSessions() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files())
        recognizers[0].processResult = 0L
        inference.start(this)
        recognizers[0].emit("hello", 0, 100, intArrayOf(7, 8, 9))

        inference.stop()
        advanceUntilIdle()
        inference.start(this)

        assertArrayEquals(intArrayOf(7, 8, 9), recognizers[0].lastCarriedContext)
        inference.close()
    }

    @Test
    fun rtf_reflectsRecognizerMetrics() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val inference = buildInference(testScheduler, { models.add(it) }, { recognizers.add(it) })
        inference.setSourceLanguage("en")
        inference.load(files())
        recognizers[0].metricsResult = metrics(rtf = 1.5)
        inference.start(this)

        advanceTimeBy(1)
        Thread.sleep(1_100)
        advanceTimeBy(200)

        assertEquals(1.5f, inference.rtf.value)
        inference.close()
    }

    @Test
    fun close_clearsContextAndClosesResources() = runTest {
        val models = mutableListOf<FakeModel>()
        val recognizers = mutableListOf<FakeRecognizer>()
        val captures = mutableListOf<FakeCapture>()
        val inference = buildInference(
            testScheduler,
            { models.add(it) },
            { recognizers.add(it) },
            { captures.add(it) },
        )
        inference.setSourceLanguage("en")
        inference.load(files())
        inference.start(this)
        recognizers[0].emit("hello", 0, 100, intArrayOf(7, 8, 9))

        inference.close()

        assertEquals(1, captures[0].stopCalls)
        waitForCondition { models[0].closeCalls == 1 }
        waitForCondition { recognizers[0].closeCalls == 1 }

        // Carried context is gone: a fresh session starts cold.
        inference.load(files())
        inference.start(this)
        assertTrue(recognizers[1].lastCarriedContext!!.isEmpty())
        inference.close()
    }
}
