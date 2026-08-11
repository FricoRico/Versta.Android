package app.versta.translate.adapter.outbound

import app.versta.translate.bridge.leanmt.TranslationEngine
import app.versta.translate.bridge.leanmt.LeanmtModelConfig
import app.versta.translate.bridge.leanmt.LeanmtPackage
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Path

class BergamotTinyInferenceTest {

    private class MockTranslationEngine : TranslationEngine {
        var loaded = false
        var loadCalls = 0
        var lastPackage: LeanmtPackage? = null
        var lastConfig: LeanmtModelConfig? = null
        var lastTexts: Array<String>? = null
        var lastMaxBeamWidth: Long? = null
        var lastMaxSequenceLength: Long? = null
        var closeCalls = 0
        var translateResult: (Array<String>) -> Array<String> = { it }

        override fun loadModel(pkg: LeanmtPackage, config: LeanmtModelConfig) {
            loadCalls++
            lastPackage = pkg
            lastConfig = config
            loaded = true
        }

        override fun translate(
            texts: Array<String>,
            maxBeamWidth: Long,
            maxSequenceLength: Long,
        ): Array<String> {
            if (!loaded) {
                throw IllegalStateException("Translation model is not loaded")
            }
            lastTexts = texts
            lastMaxBeamWidth = maxBeamWidth
            lastMaxSequenceLength = maxSequenceLength
            return translateResult(texts)
        }

        override fun close() {
            closeCalls++
            loaded = false
        }
    }

    private fun files(model: String = "model.bin"): LanguageModelFiles {
        return LanguageModelFiles(
            model = Path.of(model),
            vocabulary = Path.of("vocab.spm"),
            shortlist = Path.of("shortlist.bin"),
        )
    }

    private val config = LanguageModelConfiguration(
        encoderLayers = 6,
        decoderLayers = 2,
        ffnDepth = 2,
        numHeads = 8,
    )

    @Test
    fun translate_blankInput_returnsEmptyWithoutTranslating() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        engine.loaded = true

        assertEquals("", inference.translate("  \n  ", 3, 128))

        assertNull(engine.lastTexts)
    }

    @Test
    fun translate_singleLine_passesThroughAsSingleUnit() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        inference.load(files(), config)

        assertEquals("hello world", inference.translate("hello world", 3, 128))
        assertArrayEquals(arrayOf("hello world"), engine.lastTexts)
    }

    @Test
    fun translate_multipleParagraphs_splitsAndJoins() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        inference.load(files(), config)
        engine.translateResult = { texts -> texts.map { it.uppercase() }.toTypedArray() }

        assertEquals("A\nB\nC", inference.translate("a\nb\nc", 3, 128))
        assertArrayEquals(arrayOf("a", "b", "c"), engine.lastTexts)
    }

    @Test
    fun translate_blankParagraphRuns_areSkipped() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        inference.load(files(), config)

        inference.translate("alpha\n\n\nbeta\n  \ngamma", 3, 128)

        assertArrayEquals(arrayOf("alpha", "beta", "gamma"), engine.lastTexts)
    }

    @Test
    fun translate_forwardsBeamWidthAndSequenceLength() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        inference.load(files(), config)

        inference.translate("hi", 7, 99)

        assertEquals(7L, engine.lastMaxBeamWidth)
        assertEquals(99L, engine.lastMaxSequenceLength)
    }

    @Test
    fun translate_withoutLoad_throwsNotLoaded() {
        val inference = BergamotTinyInference(MockTranslationEngine())

        try {
            inference.translate("hello", 3, 128)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Translation model is not loaded", e.message)
        }
    }

    @Test
    fun load_mapsFilesAndConfigurationIntoPackage() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)

        inference.load(files(), config)

        assertEquals(1, engine.loadCalls)
        assertEquals("model.bin", engine.lastPackage?.model)
        assertEquals("vocab.spm", engine.lastPackage?.vocabulary)
        assertEquals("", engine.lastPackage?.targetVocabulary)
        assertEquals("shortlist.bin", engine.lastPackage?.shortlist)
        assertEquals(6L, engine.lastConfig?.encoderLayers)
        assertEquals(2L, engine.lastConfig?.decoderLayers)
        assertEquals(2L, engine.lastConfig?.feedForwardDepth)
        assertEquals(8L, engine.lastConfig?.numHeads)
    }

    @Test
    fun load_withTargetVocabulary_forwardsIt() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        val twoVocab = files().copy(targetVocabulary = Path.of("trgvocab.spm"))

        inference.load(twoVocab, config)

        assertEquals("trgvocab.spm", engine.lastPackage?.targetVocabulary)
    }

    @Test
    fun load_sameModelTwice_loadsOnce() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)

        inference.load(files(), config)
        inference.load(files(), config)

        assertEquals(1, engine.loadCalls)
    }

    @Test
    fun load_modelChange_loadsAgain() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)

        inference.load(files(), config)
        inference.load(files("model-2.bin"), config)

        assertEquals(2, engine.loadCalls)
    }

    @Test
    fun close_closesEngine() {
        val engine = MockTranslationEngine()
        val inference = BergamotTinyInference(engine)
        inference.load(files(), config)

        inference.close()

        assertEquals(1, engine.closeCalls)
    }
}
