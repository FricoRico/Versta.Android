package app.versta.translate.core.entity

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Unit-style tests for the leanmt_binary_version install gate (NEU-29). No
// drivers: a Json parser configured like DownloadWorker._serializer and pure
// entity logic only. LeanmtVersionTest in the bridge package pins the
// supported value against the compiled leanmt library on-device.
class LanguageBundleMetadataTest {
    private val serializer =
        Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }

    private fun bundleMetadata(leanmtBinaryVersion: Long?): LanguageBundleMetadata =
        LanguageBundleMetadata(
            version = "v2.1.0",
            metadata =
                listOf(
                    LanguageMetadata(
                        directory = "en-es",
                        sourceLanguage = "en",
                        targetLanguage = "es",
                    ),
                ),
            bidirectional = false,
            languages = listOf("en", "es"),
            leanmtBinaryVersion = leanmtBinaryVersion,
        )

    @Test
    fun parse_withBinaryVersion_declaredVersionIsRead() {
        val json =
            """
            {
              "version": "v2.1.0",
              "leanmt_binary_version": 1,
              "bidirectional": false,
              "languages": ["en", "es"],
              "metadata": [
                { "directory": "en-es", "source_language": "en", "target_language": "es" }
              ]
            }
            """.trimIndent()

        val metadata = serializer.decodeFromString<LanguageBundleMetadata>(json)

        assertEquals(1L, metadata.leanmtBinaryVersion)
        assertTrue(metadata.isBinaryVersionSupported(1L))
    }

    @Test
    fun parse_withoutBinaryVersion_fieldIsNull() {
        // Pre-v2.1.0 bundle manifest: no leanmt_binary_version, plus fields the
        // current parser does not know, to prove both stay parseable.
        val json =
            """
            {
              "version": "v2.0.3",
              "some_future_field": { "nested": true },
              "bidirectional": false,
              "languages": ["en", "es"],
              "metadata": [
                { "directory": "en-es", "source_language": "en", "target_language": "es" }
              ]
            }
            """.trimIndent()

        val metadata = serializer.decodeFromString<LanguageBundleMetadata>(json)

        assertNull(metadata.leanmtBinaryVersion)
        assertTrue(metadata.isValid())
    }

    @Test
    fun isBinaryVersionSupported_matchingVersion_isSupported() {
        assertTrue(bundleMetadata(1L).isBinaryVersionSupported(1L))
    }

    @Test
    fun isBinaryVersionSupported_missingVersion_isRejected() {
        assertFalse(bundleMetadata(null).isBinaryVersionSupported(1L))
    }

    @Test
    fun isBinaryVersionSupported_staleVersion_isRejected() {
        assertFalse(bundleMetadata(0L).isBinaryVersionSupported(1L))
    }

    @Test
    fun isBinaryVersionSupported_newerVersion_isRejected() {
        assertFalse(bundleMetadata(2L).isBinaryVersionSupported(1L))
    }

    @Test
    fun incompatibleException_reasonReflectsMissingVersion() {
        val exception = IncompatibleModelBundleException(declared = null, supported = 1L)

        assertEquals(IncompatibleModelBundleException.Reason.BundleUndeclared, exception.reason)
    }

    @Test
    fun incompatibleException_reasonReflectsStaleBundle() {
        val exception = IncompatibleModelBundleException(declared = 0L, supported = 1L)

        assertEquals(IncompatibleModelBundleException.Reason.BundleOutdated, exception.reason)
    }

    @Test
    fun incompatibleException_reasonReflectsNewerBundle() {
        val exception = IncompatibleModelBundleException(declared = 2L, supported = 1L)

        assertEquals(IncompatibleModelBundleException.Reason.AppOutdated, exception.reason)
    }
}
