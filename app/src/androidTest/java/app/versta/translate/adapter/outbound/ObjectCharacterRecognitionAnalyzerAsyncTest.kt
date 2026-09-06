package app.versta.translate.adapter.outbound

import android.graphics.PointF
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionInference.LiveTick
import app.versta.translate.core.entity.OcrAnalysisResult
import app.versta.translate.core.entity.OcrLineResult
import app.versta.translate.core.entity.OcrTextBox
import app.versta.translate.core.entity.ObjectCharacterRecognitionBundleWithFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/**
 * The live pump's acquire scheduling + version-gated presentation: the full
 * OCR pipeline must NEVER run synchronously on the GL thread, and line
 * content must cross JNI only when its native cursor moves (both were
 * measured GL-frame budget breakers on-device).
 */
class ObjectCharacterRecognitionAnalyzerAsyncTest {

    private val quad = arrayOf(
        PointF(0f, 0f), PointF(50f, 0f), PointF(50f, 20f), PointF(0f, 20f)
    )
    private val pose = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    private fun line(text: String) = OcrLineResult(
        box = OcrTextBox(points = quad, score = 0.9f),
        text = text,
        score = 0.9f,
        blockId = 1,
    )

    private fun result(lines: List<OcrLineResult>) =
        OcrAnalysisResult(lines = lines, width = 2, height = 2)

    private fun tick(version: Int = 1, epoch: Int = 1) =
        LiveTick(homography = pose.copyOf(), anchorEpoch = epoch, contentVersion = version)

    private class FakeOcr : ObjectCharacterRecognitionInference {
        var analyzeCalls = 0
        var tickCalls = 0
        var probeCalls = 0
        var pullCalls = 0
        var analyzeResult: OcrAnalysisResult = OcrAnalysisResult(emptyList(), 2, 2)
        var tickResult: LiveTick? = null
        var pullResult: OcrAnalysisResult? = null

        override fun load(bundle: ObjectCharacterRecognitionBundleWithFiles, threads: Int) {}
        override fun analyzeLive(input: ByteBuffer, width: Int, height: Int, forcedRecognizer: String?): OcrAnalysisResult {
            analyzeCalls++
            return analyzeResult
        }
        override fun tickLive(input: ByteBuffer, width: Int, height: Int): LiveTick? {
            tickCalls++
            return tickResult
        }
        override fun pullLiveContent(width: Int, height: Int): OcrAnalysisResult? {
            pullCalls++
            return pullResult ?: OcrAnalysisResult(emptyList(), width, height)
        }
        override fun probeLive(input: ByteBuffer, width: Int, height: Int) {
            probeCalls++
        }
        override fun analyzeStill(input: ByteBuffer, width: Int, height: Int, rotationDegrees: Int, forcedRecognizer: String?): OcrAnalysisResult =
            OcrAnalysisResult(emptyList(), width, height)
        override fun cancel() {}
        override fun close() {}
    }

    private fun analyzerFor(
        fake: FakeOcr,
        presented: MutableList<List<String>>,
        minAcquireIntervalMs: Long = 0,
        maxAcquireIntervalMs: Long = 2_000,
    ) = ObjectCharacterRecognitionAnalyzer(
        objectCharacterRecognitionInference = fake,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        acquireDispatcher = Dispatchers.Unconfined,
        minAcquireIntervalMs = minAcquireIntervalMs,
        maxAcquireIntervalMs = maxAcquireIntervalMs,
        onFrameProcessed = { objects, _, _ -> presented += objects.map { it.text } },
    )

    private fun frame() = ByteBuffer.allocateDirect(2 * 2 * 4)

    @Test
    fun anchorlessFrameNeverAnalyzesInline() {
        val fake = FakeOcr()
        fake.analyzeResult = result(listOf(line("hi")))
        val presented = mutableListOf<List<String>>()
        val analyzer = analyzerFor(fake, presented)

        val out = analyzer.process(frame(), 2, 2)

        // Acquire dispatched to the worker (ran on Unconfined → already done),
        // but the GL frame itself never saw a blocking analyze. The stillness
        // probe still saw the frame (the quiet gate needs frame-rate samples).
        assertEquals(1, fake.analyzeCalls)
        assertEquals(0, fake.tickCalls)
        assertEquals(1, fake.probeCalls)
        assertNull(out)
        assertTrue(presented.last().isEmpty())
    }

    @Test
    fun completedAcquireAppliesThenTicksSameFrame() {
        val fake = FakeOcr()
        fake.analyzeResult = result(listOf(line("hi")))
        val presented = mutableListOf<List<String>>()
        val analyzer = analyzerFor(fake, presented)
        analyzer.process(frame(), 2, 2) // acquires

        fake.tickResult = tick(version = 1, epoch = 1)
        fake.pullResult = result(listOf(line("hi")))
        val out = analyzer.process(frame(), 2, 2)

        // The applied acquire presents its lines (strips land, content bump),
        // then this frame still ticks for the fresh pose; the version move
        // pulls line content exactly once.
        assertTrue(presented.any { it == listOf("hi") })
        assertEquals(1, fake.tickCalls)
        assertEquals(1, fake.pullCalls)
        assertNotNull(out)
    }

    @Test
    fun stableCursorSuppressesPublishing() {
        val fake = FakeOcr()
        fake.analyzeResult = result(listOf(line("hi")))
        val presented = mutableListOf<List<String>>()
        val analyzer = analyzerFor(fake, presented)
        analyzer.process(frame(), 2, 2) // acquires

        fake.tickResult = tick(version = 1, epoch = 1)
        fake.pullResult = result(listOf(line("hi")))
        analyzer.process(frame(), 2, 2) // apply + first tick pulls once

        val afterPull = presented.size
        repeat(3) { analyzer.process(frame(), 2, 2) }

        assertEquals(1, fake.pullCalls)
        assertEquals(4, fake.tickCalls)
        assertEquals("tracked frames with a stable cursor present nothing", afterPull, presented.size)
    }

    @Test
    fun cursorMoveRepublishes() {
        val fake = FakeOcr()
        fake.analyzeResult = result(listOf(line("hi")))
        val presented = mutableListOf<List<String>>()
        val analyzer = analyzerFor(fake, presented)
        analyzer.process(frame(), 2, 2)

        fake.tickResult = tick(version = 1, epoch = 1)
        fake.pullResult = result(listOf(line("hi")))
        analyzer.process(frame(), 2, 2)

        fake.tickResult = tick(version = 2, epoch = 1) // content refresh
        fake.pullResult = result(listOf(line("hi"), line("world")))
        analyzer.process(frame(), 2, 2)

        assertEquals(2, fake.pullCalls)
        assertEquals(listOf("hi", "world"), presented.last())
    }

    @Test
    fun acquireBackoffDoublesWhileNothingSticks() {
        val fake = FakeOcr() // analyze returns empty: nothing ever anchors
        val presented = mutableListOf<List<String>>()
        val analyzer = analyzerFor(fake, presented, minAcquireIntervalMs = 50, maxAcquireIntervalMs = 400)

        analyzer.process(frame(), 2, 2) // dispatch #1 (interval now 100 ms)
        analyzer.process(frame(), 2, 2) // throttled: within 100 ms
        assertEquals(1, fake.analyzeCalls)

        Thread.sleep(120)
        analyzer.process(frame(), 2, 2) // dispatch #2 (interval now 200 ms)
        assertEquals(2, fake.analyzeCalls)
        analyzer.process(frame(), 2, 2) // throttled: within 200 ms
        assertEquals(2, fake.analyzeCalls)
    }

    @Test
    fun lostAnchorReacquiresInsteadOfThrowingAwayFrames() {
        val fake = FakeOcr()
        fake.analyzeResult = result(listOf(line("hi")))
        val presented = mutableListOf<List<String>>()
        val analyzer = analyzerFor(fake, presented)
        analyzer.process(frame(), 2, 2) // acquire #1

        fake.tickResult = tick()
        analyzer.process(frame(), 2, 2) // apply + tick, anchored

        fake.tickResult = null // anchor lost on this tick
        val out = analyzer.process(frame(), 2, 2)

        assertNull(out)
        assertEquals(2, fake.analyzeCalls) // re-acquire dispatched, not inline
    }
}
