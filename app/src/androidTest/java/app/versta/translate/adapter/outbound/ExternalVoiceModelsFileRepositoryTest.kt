package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import app.versta.translate.core.entity.VoiceModelTokenizerFiles
import app.versta.translate.core.entity.VoiceModelVoiceFiles
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class ExternalVoiceModelsFileRepositoryTest {
    private fun definitionsJson(version: String = "v1.10.0"): String =
        """
        [
            {
                "id": "supertonic3",
                "name": "Supertonic 3",
                "base_model": "Supertone/supertonic-3",
                "size": 114532352,
                "version": "$version",
                "bundle": "https://mock.versta.app/supertonic3-bundle.tar.gz",
                "checksum": "https://mock.versta.app/supertonic3-bundle.tar.gz.sha256"
            }
        ]
        """.trimIndent()

    private fun repository(json: String = definitionsJson()): ExternalVoiceModelsFileRepository = ExternalVoiceModelsFileRepository(ByteArrayInputStream(json.toByteArray()))

    private fun importedModel(
        id: String,
        version: String,
        size: Long,
    ): VoiceWithModelFiles =
        VoiceWithModelFiles(
            id = id,
            path = Path.of(""),
            baseModel = "Supertone/supertonic-3",
            architectures = listOf(VoiceModelArchitecture.Supertonic3),
            version = version,
            size = size,
            inference = VoiceModelInferenceFiles(config = Path.of("tts.json")),
            tokenizer = VoiceModelTokenizerFiles(unicodeIndexer = Path.of("unicode_indexer.json")),
            voices = VoiceModelVoiceFiles(),
        )

    private fun awaitDefinitions(repo: ExternalVoiceModelsFileRepository) = runBlocking { repo.getDefinitions().first { it.isNotEmpty() } }

    @Test
    fun getDefinitions_parsesAllModels() {
        val repo = repository()

        val definitions = awaitDefinitions(repo)

        assertEquals(listOf("supertonic3"), definitions.map { it.id })
        assertTrue(definitions.all { it.isValid() })
    }

    @Test
    fun getDefinitionsByState_sameVersion_marksInstalled() {
        val repo = repository()
        awaitDefinitions(repo)
        val imported = listOf(importedModel("supertonic3", "v1.10.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("supertonic3"), result.installed.map { it.id })
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
        val imported = listOf(importedModel("supertonic3", "v1.2.0", size = 99L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("supertonic3"), result.updates.map { it.id })
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
        val imported = listOf(importedModel("supertonic3", "v10.0.0", size = 99L))

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

        assertEquals(listOf("supertonic3"), result.available.map { it.id })
        assertTrue(result.installed.isEmpty())
        assertTrue(result.updates.isEmpty())
    }
}
