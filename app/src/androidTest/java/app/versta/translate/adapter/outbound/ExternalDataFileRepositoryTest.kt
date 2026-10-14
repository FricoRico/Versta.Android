package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.DataWithFiles
import app.versta.translate.core.entity.TextToSpeechDataFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class ExternalDataFileRepositoryTest {
    private fun definitionsJson(version: String = "v1.10.0"): String =
        """
        [
            {
                "id": "versta-tts-data",
                "size": 42120130,
                "type": "tts",
                "version": "$version",
                "bundle": "https://mock.versta.app/versta-tts-data-bundle.tar.gz",
                "checksum": "https://mock.versta.app/versta-tts-data-bundle.tar.gz.sha256"
            }
        ]
        """.trimIndent()

    private fun repository(json: String = definitionsJson()): ExternalDataFileRepository = ExternalDataFileRepository(ByteArrayInputStream(json.toByteArray()))

    private fun importedData(
        id: String,
        version: String,
        size: Long,
    ): DataWithFiles =
        DataWithFiles(
            id = id,
            path = Path.of(""),
            version = version,
            size = size,
            files =
                TextToSpeechDataFiles(
                    espeak = Path.of("espeak"),
                    openJTalk = Path.of("open-jtalk"),
                ),
        )

    private fun awaitDefinitions(repo: ExternalDataFileRepository) = runBlocking { repo.getDefinitions().first { it.isNotEmpty() } }

    @Test
    fun getDefinitions_parsesAllData() {
        val repo = repository()

        val definitions = awaitDefinitions(repo)

        assertEquals(listOf("versta-tts-data"), definitions.map { it.id })
        assertTrue(definitions.all { it.type == DataType.TTS })
    }

    @Test
    fun getDefinitionsByState_sameVersion_marksInstalled() {
        val repo = repository()
        awaitDefinitions(repo)
        val imported = listOf(importedData("versta-tts-data", "v1.10.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("versta-tts-data"), result.installed.map { it.id })
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
        val imported = listOf(importedData("versta-tts-data", "v1.2.0", size = 99L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertEquals(listOf("versta-tts-data"), result.updates.map { it.id })
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
        val imported = listOf(importedData("versta-tts-data", "v10.0.0", size = 99L))

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

        assertEquals(listOf("versta-tts-data"), result.available.map { it.id })
        assertTrue(result.installed.isEmpty())
        assertTrue(result.updates.isEmpty())
    }
}
