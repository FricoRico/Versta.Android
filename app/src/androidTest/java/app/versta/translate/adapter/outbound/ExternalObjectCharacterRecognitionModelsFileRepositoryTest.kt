package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.ObjectCharacterRecognitionArchitecture
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorInferenceFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionDetectorWithFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerInferenceFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerTokenizerFiles
import app.versta.translate.core.entity.ObjectCharacterRecognitionRecognizerWithFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class ExternalObjectCharacterRecognitionModelsFileRepositoryTest {
    private fun definitionsJson(version: String = "v1.10.0"): String =
        """
        [
            {
                "id": "paddle-ocr",
                "name": "Paddle OCR",
                "base_model": "PaddlePaddle/PP-OCRv6",
                "architectures": ["PaddleOCR"],
                "languages": ["en"],
                "size": 28391476,
                "version": "$version",
                "bundle": "https://mock.versta.app/paddle-ocr-bundle.tar.gz",
                "checksum": "https://mock.versta.app/paddle-ocr-bundle.tar.gz.sha256"
            }
        ]
        """.trimIndent()

    private fun repository(json: String = definitionsJson()): ExternalObjectCharacterRecognitionModelsFileRepository = ExternalObjectCharacterRecognitionModelsFileRepository(ByteArrayInputStream(json.toByteArray()))

    private fun importedDetector(
        id: String,
        version: String,
    ): ObjectCharacterRecognitionDetectorWithFiles =
        ObjectCharacterRecognitionDetectorWithFiles(
            id = id,
            path = Path.of(""),
            baseModel = "PaddlePaddle/PP-OCRv6",
            architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
            languages = listOf("en"),
            version = version,
            size = 0,
            inference = ObjectCharacterRecognitionDetectorInferenceFiles(model = Path.of("detector.bin")),
        )

    private fun importedRecognizer(
        id: String,
        version: String,
    ): ObjectCharacterRecognitionRecognizerWithFiles =
        ObjectCharacterRecognitionRecognizerWithFiles(
            id = id,
            path = Path.of(""),
            baseModel = "PaddlePaddle/PP-OCRv6",
            architectures = listOf(ObjectCharacterRecognitionArchitecture.PaddleOCR),
            languages = listOf("en"),
            version = version,
            size = 0,
            inference = ObjectCharacterRecognitionRecognizerInferenceFiles(model = Path.of("recognizer.bin")),
            tokenizer = ObjectCharacterRecognitionRecognizerTokenizerFiles(vocabulary = Path.of("vocabulary.bin")),
        )

    private fun awaitDefinitions(repo: ExternalObjectCharacterRecognitionModelsFileRepository) = runBlocking { repo.getDefinitions().first { it.isNotEmpty() } }

    @Test
    fun getDefinitions_parsesAllModels() {
        val repo = repository()

        val definitions = awaitDefinitions(repo)

        assertEquals(listOf("paddle-ocr"), definitions.map { it.id })
        assertTrue(definitions.all { it.isValid() })
    }

    @Test
    fun getDefinitionsByState_sameVersion_marksInstalled() {
        val repo = repository()
        awaitDefinitions(repo)
        val detectors = listOf(importedDetector("paddle-ocr", "v1.10.0"))
        val recognizers = listOf(importedRecognizer("paddle-ocr", "v1.10.0"))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(detectors), flowOf(recognizers)).first()
            }

        assertEquals(listOf("paddle-ocr"), result.installed.map { it.definition.id })
        assertTrue(result.updates.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_olderImportedVersions_marksUpdate() {
        val repo = repository()
        awaitDefinitions(repo)
        // Lexicographic order treats v1.2.0 as newer than v1.10.0; numeric order
        // must still surface the update.
        val detectors = listOf(importedDetector("paddle-ocr", "v1.2.0"))
        val recognizers = listOf(importedRecognizer("paddle-ocr", "v1.2.0"))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(detectors), flowOf(recognizers)).first()
            }

        assertEquals(listOf("paddle-ocr"), result.updates.map { it.definition.id })
        assertTrue(result.installed.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_singleOutdatedComponent_marksUpdate() {
        val repo = repository()
        awaitDefinitions(repo)
        val detectors = listOf(importedDetector("paddle-ocr", "v1.2.0"))
        val recognizers = listOf(importedRecognizer("paddle-ocr", "v1.10.0"))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(detectors), flowOf(recognizers)).first()
            }

        assertEquals(listOf("paddle-ocr"), result.updates.map { it.definition.id })
        assertTrue(result.installed.isEmpty())
        assertTrue(result.available.isEmpty())
    }

    @Test
    fun getDefinitionsByState_newerImportedVersions_notMarkedAsUpdate() {
        val repo = repository(definitionsJson(version = "v2.0.0"))
        awaitDefinitions(repo)
        // Lexicographic order treats v10.0.0 as older than v2.0.0; numeric order
        // must not downgrade a newer install.
        val detectors = listOf(importedDetector("paddle-ocr", "v10.0.0"))
        val recognizers = listOf(importedRecognizer("paddle-ocr", "v10.0.0"))

        val result =
            runBlocking {
                repo.getDefinitionsByState(flowOf(detectors), flowOf(recognizers)).first()
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
                repo.getDefinitionsByState(flowOf(emptyList()), flowOf(emptyList())).first()
            }

        assertEquals(listOf("paddle-ocr"), result.available.map { it.definition.id })
        assertTrue(result.installed.isEmpty())
        assertTrue(result.updates.isEmpty())
    }
}
