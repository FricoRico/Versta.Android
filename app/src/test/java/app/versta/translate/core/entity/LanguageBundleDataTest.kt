package app.versta.translate.core.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageBundleDataTest {
    private fun module(
        source: String,
        target: String,
    ) = LanguageModelMetadata(
        baseModel = "$source-$target:base-memory",
        sourceLanguage = source,
        targetLanguage = target,
        files = LanguageModelFilesMetadata(model = "model.bin", vocabulary = "vocabulary.bin", shortlist = "shortlist.bin"),
        config = LanguageModelConfigurationMetadata(encoderLayers = 1, decoderLayers = 1, numHeads = 8),
    )

    private fun bundle(
        languages: List<String>,
        modules: List<LanguageModelMetadata>,
    ) = LanguageBundleData(
        bundle =
            LanguageBundleMetadata(
                version = "v1.0.0",
                metadata = emptyList(),
                bidirectional = false,
                languages = languages,
            ),
        languages = modules,
    )

    @Test
    fun isLanguageBoundaryConsistent_registryCodesCovered() {
        val data = bundle(languages = listOf("en", "zh"), modules = listOf(module("en", "zh")))

        assertTrue(data.isLanguageBoundaryConsistent())
    }

    @Test
    fun isLanguageBoundaryConsistent_normalizesBothSides() {
        val data = bundle(languages = listOf("zh", "en"), modules = listOf(module("en", "zh-Hans")))

        assertTrue(data.isLanguageBoundaryConsistent())
    }

    @Test
    fun isLanguageBoundaryConsistent_rejectsUndeclaredModuleCode() {
        val data = bundle(languages = listOf("en", "zh"), modules = listOf(module("en", "zh-Hant")))

        assertFalse(data.isLanguageBoundaryConsistent())
    }

    @Test
    fun isLanguageBoundaryConsistent_rejectsMissingLanguage() {
        val data = bundle(languages = listOf("en"), modules = listOf(module("en", "zh")))

        assertFalse(data.isLanguageBoundaryConsistent())
    }
}
