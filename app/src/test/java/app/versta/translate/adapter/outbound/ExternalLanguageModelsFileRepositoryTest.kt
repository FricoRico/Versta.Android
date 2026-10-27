package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.LanguageModel
import app.versta.translate.core.entity.LanguageModelConfiguration
import app.versta.translate.core.entity.LanguageModelFiles
import app.versta.translate.core.entity.LanguageModelPair
import app.versta.translate.core.entity.localeForLanguageCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.Locale

class ExternalLanguageModelsFileRepositoryTest {
    private fun definitionsJson(version: String = "v1.10.0"): String =
        """
        [
            [
                {
                    "base_model": "BergamotTiny/en-nl",
                    "source_language": "en",
                    "target_language": "nl",
                    "bidirectional": false,
                    "architectures": ["BergamotTinyModel"],
                    "score": 1.0,
                    "size": 50713114,
                    "version": "$version",
                    "bundle": "https://mock.versta.app/en-nl-bundle.tar.gz",
                    "checksum": "https://mock.versta.app/en-nl-bundle.tar.gz.sha256"
                }
            ]
        ]
        """.trimIndent()

    private fun zhDefinitionEntry(
        targetLanguage: String,
        bundleSlug: String,
        version: String,
    ): String =
        """
        [
            {
                "base_model": "BergamotTiny/en-$bundleSlug",
                "source_language": "en",
                "target_language": "$targetLanguage",
                "bidirectional": false,
                "architectures": ["BergamotTinyModel"],
                "score": 1.0,
                "size": 50713114,
                "version": "$version",
                "bundle": "https://mock.versta.app/$bundleSlug-bundle.tar.gz",
                "checksum": "https://mock.versta.app/$bundleSlug-bundle.tar.gz.sha256"
            }
        ]
        """.trimIndent()

    private fun zhDefinitionsJson(
        includeTraditional: Boolean = false,
        version: String = "v1.10.0",
    ): String {
        val groups =
            listOfNotNull(
                zhDefinitionEntry(targetLanguage = "zh-Hans", bundleSlug = "en-zh", version = version),
                if (includeTraditional) {
                    zhDefinitionEntry(targetLanguage = "zh-Hant", bundleSlug = "en-zh_hant", version = version)
                } else {
                    null
                },
            )

        return groups.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    private fun repository(json: String = definitionsJson()): ExternalLanguageModelsFileRepository = ExternalLanguageModelsFileRepository(ByteArrayInputStream(json.toByteArray()))

    private fun importedPair(
        source: String,
        target: String,
        version: String,
        size: Long,
    ): LanguageModelPair =
        LanguageModelPair(
            sourceLocale = Locale(source),
            targetLocale = Locale(target),
            files = modelFiles(source, target, version, size),
        )

    private fun registryPair(
        source: String,
        target: String,
        version: String,
        size: Long,
    ): LanguageModelPair =
        LanguageModelPair(
            sourceLocale = localeForLanguageCode(source),
            targetLocale = localeForLanguageCode(target),
            files = modelFiles(source, target, version, size),
        )

    private fun modelFiles(
        source: String,
        target: String,
        version: String,
        size: Long,
    ): LanguageModel =
        LanguageModel(
            path = Path.of(""),
            baseModel = "BergamotTiny/$source-$target",
            version = version,
            size = size,
            files =
                LanguageModelFiles(
                    model = Path.of("model.bin"),
                    vocabulary = Path.of("vocabulary.bin"),
                    shortlist = Path.of("shortlist.bin"),
                ),
            config =
                LanguageModelConfiguration(
                    encoderLayers = 1,
                    decoderLayers = 1,
                    numHeads = 8,
                ),
        )

    private fun awaitDefinitions(repo: ExternalLanguageModelsFileRepository) = runBlocking { repo.getDefinitions().first { it.isNotEmpty() } }

    @Test
    fun getDefinitions_parsesAllPairs() {
        val repo = repository()

        val definitions = awaitDefinitions(repo)

        assertEquals(listOf("en-nl"), definitions.map { it.pair.uniqueId() })
    }

    @Test
    fun getDefinitionsByState_sameVersion_marksInstalled() {
        val repo = repository()
        awaitDefinitions(repo)
        val imported = listOf(importedPair("en", "nl", "v1.10.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("en-nl"), result.installed.map { it.pair.uniqueId() })
        assertEquals(42L, result.installed.first().extracted)
        assertTrue(result.updates.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_olderImportedVersion_marksUpdate() {
        val repo = repository()
        awaitDefinitions(repo)
        // Lexicographic order treats v1.2.0 as newer than v1.10.0; numeric order
        // must still surface the update.
        val imported = listOf(importedPair("en", "nl", "v1.2.0", size = 99L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("en-nl"), result.updates.map { it.pair.uniqueId() })
        assertEquals(99L, result.updates.first().extracted)
        assertTrue(result.installed.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_newerImportedVersion_notMarkedAsUpdate() {
        val repo = repository(definitionsJson(version = "v2.0.0"))
        awaitDefinitions(repo)
        // Lexicographic order treats v10.0.0 as older than v2.0.0; numeric order
        // must not downgrade a newer install.
        val imported = listOf(importedPair("en", "nl", "v10.0.0", size = 99L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertTrue(result.updates.isEmpty())
        assertTrue(result.installed.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_notImported_marksAvailable() {
        val repo = repository()
        awaitDefinitions(repo)

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(emptyList())).first()
            }

        assertEquals(listOf("en-nl"), result.available.map { it.pair.uniqueId() })
        assertTrue(result.installed.isEmpty())
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun getDefinitionsByState_registryCodedInstallJoinsCatalogBcp47Entry() {
        val repo = repository(zhDefinitionsJson())
        awaitDefinitions(repo)
        // The zh pack installs from the en-zh bundle whose manifests declare
        // registry codes ("en", "zh"), while the catalog lists "zh-Hans".
        val imported = listOf(registryPair("en", "zh", "v1.10.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("en-zh"), result.installed.map { it.pair.uniqueId() })
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_traditionalChineseNotMatchedBySimplifiedInstall() {
        val repo = repository(zhDefinitionsJson(includeTraditional = true))
        awaitDefinitions(repo)
        val imported = listOf(registryPair("en", "zh", "v1.10.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("en-zh"), result.installed.map { it.pair.uniqueId() })
        assertEquals(listOf("en-zh_hant"), result.available.map { it.pair.uniqueId() })
    }
}
