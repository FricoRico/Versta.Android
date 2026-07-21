package app.versta.translate.core.entity

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechRecognitionMetadataTest {

    private fun bundleMetadata(
        directories: List<String> = listOf("whisper"),
        modules: List<String> = listOf("recognition"),
    ): SpeechRecognitionBundleMetadata {
        return SpeechRecognitionBundleMetadata(
            id = "whisper-base-en",
            version = "v1.0.0",
            languages = listOf("en"),
            modules = modules,
            metadata = directories.map { directory ->
                SpeechRecognitionMetadataFile(
                    directory = directory,
                    languages = listOf("en"),
                    module = SpeechRecognitionModule.Recognition,
                )
            },
        )
    }

    @Test
    fun bundleMetadata_validFields_isValid() {
        assertTrue(bundleMetadata().isValid())
    }

    @Test
    fun bundleMetadata_emptyModules_isInvalid() {
        assertFalse(bundleMetadata(modules = emptyList()).isValid())
    }

    @Test
    fun bundleMetadata_emptyDirectory_isInvalid() {
        assertFalse(bundleMetadata(directories = listOf("")).isValid())
    }

    @Test
    fun bundleMetadata_noMetadataEntries_isValidWhenModulesPresent() {
        assertTrue(bundleMetadata(directories = emptyList()).isValid())
    }

    @Test
    fun bundleMetadata_serializesAndDeserializes() {
        val json = Json.encodeToString(SpeechRecognitionBundleMetadata.serializer(), bundleMetadata())

        val decoded = Json.decodeFromString(SpeechRecognitionBundleMetadata.serializer(), json)

        assertEquals(bundleMetadata().id, decoded.id)
        assertEquals(bundleMetadata().modules, decoded.modules)
        assertEquals(bundleMetadata().metadata, decoded.metadata)
    }

    @Test
    fun metadataFile_serializesModule() {
        val file = SpeechRecognitionMetadataFile(
            directory = "whisper",
            languages = listOf("en"),
            module = SpeechRecognitionModule.Recognition,
        )

        val json = Json.encodeToString(SpeechRecognitionMetadataFile.serializer(), file)

        assertTrue(json.contains("\"module\":\"recognition\""))
    }

    @Test
    fun architecture_whisper_value() {
        assertEquals("Whisper", SpeechRecognitionArchitecture.Whisper.value)
    }
}
