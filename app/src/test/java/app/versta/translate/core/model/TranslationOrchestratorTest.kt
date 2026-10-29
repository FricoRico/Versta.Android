package app.versta.translate.core.model

import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.PivotPairModelFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class TranslationOrchestratorTest {
    private class RecordingInference(
        private val transform: (String) -> String = { it },
    ) : TranslationInference {
        data class TranslateCall(
            val text: String,
            val maxBeamWidth: Int,
            val maxSequenceLength: Int,
        )

        data class LoadCall(
            val files: LanguageModelFiles,
            val config: LanguageModelConfiguration,
        )

        val translateCalls = mutableListOf<TranslateCall>()
        val loadCalls = mutableListOf<LoadCall>()
        var closeCount = 0

        override fun translate(
            text: String,
            maxBeamWidth: Int,
            maxSequenceLength: Int,
        ): String {
            translateCalls += TranslateCall(text, maxBeamWidth, maxSequenceLength)
            return transform(text)
        }

        override fun load(
            files: LanguageModelFiles,
            config: LanguageModelConfiguration,
        ) {
            loadCalls += LoadCall(files, config)
        }

        override fun close() {
            closeCount++
        }
    }

    private class InferenceFactory {
        val sizes = mutableListOf<Long>()
        val created = mutableListOf<RecordingInference>()

        val create: (Long) -> TranslationInference = { size ->
            sizes += size
            RecordingInference().also { created += it }
        }
    }

    private val preferences = TranslationPreferenceMemoryRepository()
    private val intermediate = RecordingInference(transform = { "<pivot>$it" })
    private val output = RecordingInference()
    private val factory = InferenceFactory()

    private val orchestrator =
        TranslationOrchestrator(
            intermediateModel = intermediate,
            outputModel = output,
            translationPreferenceRepository = preferences,
            createInference = factory.create,
        )

    private fun model(name: String) =
        LanguageModel(
            path = Path.of(name),
            baseModel = "base",
            version = "1",
            files =
                LanguageModelFiles(
                    model = Path.of("$name/model.bin"),
                    vocabulary = Path.of("$name/vocab.spm"),
                    shortlist = Path.of("$name/shortlist.bin"),
                ),
            config =
                LanguageModelConfiguration(
                    encoderLayers = 2L,
                    decoderLayers = 2L,
                    numHeads = 4L,
                ),
        )

    @Test
    fun translate_withIntermediaryChainsPivotThenOutput() =
        runBlocking {
            preferences.setNumberOfBeams(6)
            preferences.setMaxSequenceLength(128)

            val result =
                orchestrator.translate(
                    "hello",
                    PivotPairModelFiles(intermediary = model("int"), output = model("out")),
                )

            assertEquals(listOf(RecordingInference.TranslateCall("hello", 6, 128)), intermediate.translateCalls)
            assertEquals(listOf(RecordingInference.TranslateCall("<pivot>hello", 6, 128)), output.translateCalls)
            assertEquals("<pivot>hello", result)
        }

    @Test
    fun translate_withoutIntermediaryGoesStraightToOutput() =
        runBlocking {
            val result =
                orchestrator.translate(
                    "hello",
                    PivotPairModelFiles(intermediary = null, output = model("out")),
                )

            assertTrue(intermediate.translateCalls.isEmpty())
            assertEquals(
                listOf(
                    RecordingInference.TranslateCall(
                        "hello",
                        DEFAULT_BEAMS,
                        DEFAULT_MAX_SEQUENCE,
                    ),
                ),
                output.translateCalls,
            )
            assertEquals("hello", result)
        }

    @Test
    fun translate_stripsUndefinedCharactersBeforeReachingEngines() =
        runBlocking {
            orchestrator.translate(
                "ab\u0378c",
                PivotPairModelFiles(intermediary = model("int"), output = model("out")),
            )

            assertEquals("abc", intermediate.translateCalls.single().text)
            assertEquals("<pivot>abc", output.translateCalls.single().text)
        }

    @Test
    fun load_withIntermediaryLoadsBothEngines() =
        runBlocking {
            val files = PivotPairModelFiles(intermediary = model("int"), output = model("out"))

            orchestrator.load(files)

            assertEquals(
                listOf(RecordingInference.LoadCall(files.intermediary!!.files, files.intermediary!!.config)),
                intermediate.loadCalls,
            )
            assertEquals(
                listOf(RecordingInference.LoadCall(files.output!!.files, files.output!!.config)),
                output.loadCalls,
            )
        }

    @Test
    fun load_withoutIntermediaryClosesIntermediateEngine() =
        runBlocking {
            orchestrator.load(PivotPairModelFiles(intermediary = null, output = model("out")))

            assertEquals(1, intermediate.closeCount)
            assertTrue(intermediate.loadCalls.isEmpty())
            assertEquals(1, output.loadCalls.size)
        }

    @Test
    fun load_nullFilesClosesIntermediateWithoutTouchingOutput() =
        runBlocking {
            orchestrator.load(null)

            assertEquals(1, intermediate.closeCount)
            assertEquals(0, output.closeCount)
            assertTrue(output.loadCalls.isEmpty())
        }

    @Test
    fun unload_closesBothEngines() =
        runBlocking {
            orchestrator.unload()

            assertEquals(1, intermediate.closeCount)
            assertEquals(1, output.closeCount)
        }

    @Test
    fun recreateEngines_rebuildsEnginesWithConfiguredCacheSize() =
        runBlocking {
            preferences.setCacheSize(2048)

            orchestrator.recreateEngines()
            orchestrator.translate(
                "hello",
                PivotPairModelFiles(intermediary = null, output = model("out")),
            )

            assertEquals(listOf(2048L, 2048L), factory.sizes)
            assertEquals(2, factory.created.size)
            assertEquals(1, intermediate.closeCount)
            assertEquals(1, output.closeCount)
            assertTrue(intermediate.translateCalls.isEmpty())
            assertTrue(output.translateCalls.isEmpty())
            val recreated = factory.created.last()
            assertEquals(1, recreated.translateCalls.size)
        }

    @Test
    fun recreateEngines_disabledCacheUsesSentinelAndPreservesConfiguredSize() =
        runBlocking {
            preferences.setCacheSize(512)
            preferences.setCacheEnabled(false)

            orchestrator.recreateEngines()

            preferences.setCacheEnabled(true)
            orchestrator.recreateEngines()

            assertEquals(listOf(CACHE_SIZE_DISABLED, CACHE_SIZE_DISABLED, 512L, 512L), factory.sizes)
        }

    private companion object {
        const val DEFAULT_BEAMS = 4
        const val DEFAULT_MAX_SEQUENCE = 256
        const val CACHE_SIZE_DISABLED = -2L
    }
}
