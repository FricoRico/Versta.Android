package app.versta.translate.adapter.outbound

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
    private fun definitionsJson(catalogVersion: String): String =
        """
        [
            {
                "id": "tts-en-us",
                "size": 117633540,
                "type": "TTS",
                "version": "$catalogVersion",
                "bundle": "https://mock.versta.app/tts-en-us-bundle.tar.gz",
                "checksum": "https://mock.versta.app/tts-en-us-bundle.tar.gz.sha256"
            }
        ]
        """.trimIndent()

    private fun repository(catalogVersion: String): ExternalDataFileRepository = ExternalDataFileRepository(ByteArrayInputStream(definitionsJson(catalogVersion).toByteArray()))

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
                    espeak = Path.of("espeak-data"),
                    openJTalk = Path.of("open-jtalk-data"),
                ),
        )

    private fun awaitDefinitions(repo: ExternalDataFileRepository) = runBlocking { repo.getDefinitions().first { it.isNotEmpty() } }

    @Test
    fun getDefinitionsByState_sameVersion_marksInstalled() {
        val repo = repository("v1.10.0")
        awaitDefinitions(repo)
        val imported = listOf(importedData("tts-en-us", "v1.10.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first { it.installed.isNotEmpty() }
            }

        assertEquals(listOf("tts-en-us"), result.installed.map { it.id })
        assertTrue(result.updates.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_doubleDigitCatalogVersion_marksUpdate() {
        // NEU-17: lexicographic ordering treats "v1.2.0" as newer than "v1.10.0"
        // and would skip this update.
        val repo = repository("v1.10.0")
        awaitDefinitions(repo)
        val imported = listOf(importedData("tts-en-us", "v1.2.0", size = 42L))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first { it.updates.isNotEmpty() }
            }

        assertEquals(1, result.updates.size)
        assertEquals(listOf("tts-en-us"), result.updates.map { it.id })
        assertTrue(result.installed.isEmpty())
    }

    @Test
    fun getDefinitionsByState_newerImportedDoubleDigitVersion_marksNoUpdate() {
        // NEU-17: lexicographic ordering treats the installed "v1.10.0" as older
        // than the catalog's "v1.2.0" and would offer a phantom update.
        val repo = repository("v1.2.0")
        awaitDefinitions(repo)
        val imported = listOf(importedData("tts-en-us", "v1.10.0", size = 42L))

        // Catalog is already loaded, so the first emission is the terminal state.
        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(imported)).first()
            }

        assertTrue(result.updates.isEmpty())
        assertTrue(result.installed.isEmpty())
        assertTrue(result.available.isEmpty())
    }
}
