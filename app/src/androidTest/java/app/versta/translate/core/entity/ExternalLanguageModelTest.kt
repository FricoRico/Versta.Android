package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class ExternalLanguageModelTest {

    private fun definition(source: String, target: String): ExternalLanguagePairDefinition {
        return ExternalLanguagePairDefinition(
            pair = LanguagePair.fromIsoCodes(source, target),
            bidirectional = false,
            metadata = emptyList(),
            size = 1,
            version = "1",
            bundleUri = URI("https://example.com/bundle"),
            checksumUri = URI("https://example.com/checksum"),
        )
    }

    @Test
    fun emptyCatalog_returnsEmptySet() {
        val result = emptyList<ExternalLanguagePairDefinition>().supportedLanguageIsoCodes()

        assertTrue(result.isEmpty())
    }

    @Test
    fun catalog_unionsSourceAndTargetLanguages() {
        val catalog = listOf(
            definition("en", "fr"),
            definition("de", "es"),
        )

        val result = catalog.supportedLanguageIsoCodes()

        assertEquals(setOf("en", "fr", "de", "es"), result)
    }

    @Test
    fun catalog_deduplicatesLanguagesAcrossPairs() {
        val catalog = listOf(
            definition("en", "fr"),
            definition("fr", "en"),
        )

        val result = catalog.supportedLanguageIsoCodes()

        assertEquals(setOf("en", "fr"), result)
    }
}
