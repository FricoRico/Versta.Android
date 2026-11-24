package app.versta.translate.core.entity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/**
 * Validates the Supertonic 3 golden-audio corpus in
 * `app/src/test/resources/tts/golden/sentences.json` (NEU-71). The corpus is
 * blessed into reference wavs by `tools/tts-golden/check.sh --bless`; this test
 * is the only CI gate that keeps the fixture itself consistent with the
 * languages the app actually ships.
 */
class Supertonic3GoldenCorpusTest {
    private val corpus =
        Json
            .parseToJsonElement(
                requireNotNull(javaClass.getResource("/tts/golden/sentences.json")) {
                    "golden corpus resource missing: /tts/golden/sentences.json"
                }.readText(),
            ).jsonObject

    private val cases = corpus.getValue("cases").jsonArray

    @Test
    fun corpusCoversExactlyTheSupportedLanguages() {
        val languages =
            cases
                .map {
                    it.jsonObject
                        .getValue("language")
                        .jsonPrimitive.content
                }.toSet()
        assertEquals(Supertonic3.LANGUAGES, languages)
    }

    @Test
    fun corpusHasOneUniqueCasePerLanguage() {
        val ids =
            cases.map {
                it.jsonObject
                    .getValue("id")
                    .jsonPrimitive.content
            }
        assertEquals(cases.size, ids.toSet().size)
        cases.forEach { kase ->
            val entry = kase.jsonObject
            assertEquals(
                "case id must equal the language code",
                entry.getValue("language").jsonPrimitive.content,
                entry.getValue("id").jsonPrimitive.content,
            )
        }
    }

    @Test
    fun corpusSentencesStayWithinOneTokenizerChunk() {
        cases.forEach { kase ->
            val text =
                kase.jsonObject
                    .getValue("text")
                    .jsonPrimitive.content
            assertTrue("sentence is blank", text.isNotBlank())
            assertTrue(
                "sentence must stay under the 120-char ko/ja segment cap: ${kase.jsonObject.getValue("id")}",
                text.length <= 120,
            )
        }
    }

    @Test
    fun corpusSentencesAreNfkcStable() {
        cases.forEach { kase ->
            val text =
                kase.jsonObject
                    .getValue("text")
                    .jsonPrimitive.content
            assertEquals(
                "corpus text must be stored in NFKC so the tokenizer's NFKD step is the only transform",
                text,
                Normalizer.normalize(text, Normalizer.Form.NFKC),
            )
        }
    }

    @Test
    fun corpusPinsSampleRateSeedAndGender() {
        assertEquals(Supertonic3.SAMPLE_RATE, corpus.getValue("sampleRate").jsonPrimitive.int)
        assertNotNull(corpus.getValue("seed").jsonPrimitive.int)
        assertEquals("female", corpus.getValue("gender").jsonPrimitive.content)
    }
}
