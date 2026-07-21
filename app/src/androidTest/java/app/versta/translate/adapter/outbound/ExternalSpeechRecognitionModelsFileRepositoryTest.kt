package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ExternalSpeechRecognitionModelDefinition
import app.versta.translate.core.entity.SpeechRecognitionArchitecture
import app.versta.translate.core.entity.SpeechRecognitionInferenceFiles
import app.versta.translate.core.entity.SpeechRecognitionWithFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class ExternalSpeechRecognitionModelsFileRepositoryTest {

    private fun definitionsJson(): String {
        return """
            [
                {
                    "id": "whisper-base-en",
                    "name": "Whisper Base (English)",
                    "base_model": "openai/whisper-base.en",
                    "architectures": ["Whisper"],
                    "languages": ["en"],
                    "size": 147322514,
                    "version": "v1.0.0",
                    "bundle": "https://mock.versta.app/whisper-base-en-bundle.tar.gz",
                    "checksum": "https://mock.versta.app/whisper-base-en-bundle.tar.gz.sha256"
                },
                {
                    "id": "whisper-large",
                    "name": "Whisper Large",
                    "base_model": "openai/whisper-large-v3",
                    "architectures": ["Whisper"],
                    "languages": ["en", "nl", "de"],
                    "size": 811329223,
                    "version": "v2.0.0",
                    "bundle": "https://mock.versta.app/whisper-large-bundle.tar.gz",
                    "checksum": "https://mock.versta.app/whisper-large-bundle.tar.gz.sha256"
                },
                {
                    "id": "whisper-tiny",
                    "name": "Whisper Tiny",
                    "base_model": "openai/whisper-tiny",
                    "architectures": ["Whisper"],
                    "languages": ["en"],
                    "size": 75497594,
                    "version": "v1.0.0",
                    "bundle": "https://mock.versta.app/whisper-tiny-bundle.tar.gz",
                    "checksum": "https://mock.versta.app/whisper-tiny-bundle.tar.gz.sha256"
                }
            ]
        """.trimIndent()
    }

    private fun repository(json: String = definitionsJson()): ExternalSpeechRecognitionModelsFileRepository {
        return ExternalSpeechRecognitionModelsFileRepository(ByteArrayInputStream(json.toByteArray()))
    }

    private fun importedModel(id: String, version: String, size: Long): SpeechRecognitionWithFiles {
        return SpeechRecognitionWithFiles(
            id = id,
            path = Path.of(""),
            baseModel = "openai/whisper-base.en",
            architectures = listOf(SpeechRecognitionArchitecture.Whisper),
            languages = listOf("en"),
            version = version,
            size = size,
            inference = SpeechRecognitionInferenceFiles(
                model = Path.of("model.bin"),
                vad = Path.of("vad.bin"),
            )
        )
    }

    private fun awaitDefinitions(repo: ExternalSpeechRecognitionModelsFileRepository): List<ExternalSpeechRecognitionModelDefinition> {
        return runBlocking { repo.getDefinitions().first { it.isNotEmpty() } }
    }

    @Test
    fun getDefinitions_parsesAllModels() {
        val repo = repository()

        val definitions = awaitDefinitions(repo)

        assertEquals(3, definitions.size)
        assertEquals(
            listOf("whisper-base-en", "whisper-large", "whisper-tiny"),
            definitions.map { it.id },
        )
        assertTrue(definitions.all { it.isValid() })
    }

    @Test
    fun getDefinition_returnsMatchingModel() {
        val repo = repository()
        awaitDefinitions(repo)

        val definition = runBlocking { repo.getDefinition("whisper-large").first() }

        assertEquals("whisper-large", definition.id)
        assertEquals("Whisper Large", definition.name)
        assertEquals("openai/whisper-large-v3", definition.baseModel)
    }

    @Test
    fun getDefinitionsByState_sameVersion_marksInstalled() {
        val repo = repository()
        awaitDefinitions(repo)
        val imported = listOf(importedModel("whisper-base-en", "v1.0.0", size = 42L))

        val result = runBlocking {
            repo.getDefinitionsByState(flowOf(imported)).first { it.installed.isNotEmpty() }
        }

        assertEquals(listOf("whisper-base-en"), result.installed.map { it.definition.id })
        assertEquals(42L, result.installed.first().extracted)
    }

    @Test
    fun getDefinitionsByState_olderImportedVersion_marksUpdate() {
        val repo = repository()
        awaitDefinitions(repo)
        val imported = listOf(importedModel("whisper-large", "v1.0.0", size = 99L))

        val result = runBlocking {
            repo.getDefinitionsByState(flowOf(imported)).first { it.updates.isNotEmpty() }
        }

        assertEquals(listOf("whisper-large"), result.updates.map { it.definition.id })
        assertEquals(99L, result.updates.first().extracted)
    }

    @Test
    fun getDefinitionsByState_notImported_marksAvailable() {
        val repo = repository()
        awaitDefinitions(repo)

        val result = runBlocking {
            repo.getDefinitionsByState(flowOf(emptyList())).first { it.available.isNotEmpty() }
        }

        assertEquals(
            listOf("whisper-base-en", "whisper-large", "whisper-tiny"),
            result.available.map { it.definition.id },
        )
        assertTrue(result.installed.isEmpty())
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun getDefinitionsByState_classifiesAllStatesAtOnce() {
        val repo = repository()
        awaitDefinitions(repo)
        val imported = listOf(
            importedModel("whisper-base-en", "v1.0.0", size = 42L),
            importedModel("whisper-large", "v1.0.0", size = 99L),
        )

        val result = runBlocking {
            repo.getDefinitionsByState(flowOf(imported))
                .first { it.installed.isNotEmpty() && it.updates.isNotEmpty() && it.available.isNotEmpty() }
        }

        assertEquals(listOf("whisper-base-en"), result.installed.map { it.definition.id })
        assertEquals(listOf("whisper-large"), result.updates.map { it.definition.id })
        assertEquals(listOf("whisper-tiny"), result.available.map { it.definition.id })
    }
}
