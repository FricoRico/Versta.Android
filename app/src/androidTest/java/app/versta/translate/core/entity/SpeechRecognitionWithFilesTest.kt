package app.versta.translate.core.entity

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class SpeechRecognitionWithFilesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testRoot: Path
    private lateinit var modelDir: Path

    @Before
    fun setUp() {
        testRoot = context.cacheDir.toPath()
            .resolve("speech-recognition-with-files-test")
            .resolve(java.util.UUID.randomUUID().toString())
        modelDir = testRoot.resolve("whisper-base-en")
        modelDir.createDirectories()
    }

    @After
    fun tearDown() {
        testRoot.deleteRecursively()
    }

    private fun writeMetadata(json: String) {
        modelDir.resolve("metadata.json").writeText(json)
    }

    private fun validMetadataJson(): String {
        return """
            {
                "id": "whisper-base-en",
                "version": "v1.0.0",
                "base_model": "openai/whisper-base.en",
                "languages": ["en"],
                "architectures": ["Whisper"],
                "files": {
                    "inference": {
                        "model": "model.bin",
                        "vad": "vad.bin"
                    }
                }
            }
        """.trimIndent()
    }

    private fun createModelFiles() {
        modelDir.resolve("model.bin").writeText("fake-model-bytes")
        modelDir.resolve("vad.bin").writeText("fake-vad-bytes")
    }

    private fun expectedSize(): Long {
        val files = listOf(
            modelDir.resolve("metadata.json"),
            modelDir.resolve("model.bin"),
            modelDir.resolve("vad.bin"),
        )
        return files.sumOf { Files.size(it) }
    }

    @Test
    fun load_missingMetadataFile_throws() {
        try {
            SpeechRecognitionWithFiles.load(id = "whisper-base-en", path = modelDir)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("metadata file not found"))
        }
    }

    @Test
    fun load_invalidMetadata_throws() {
        val json = validMetadataJson().replace("\"openai/whisper-base.en\"", "\"\"")
        writeMetadata(json)

        try {
            SpeechRecognitionWithFiles.load(id = "whisper-base-en", path = modelDir)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not complete and valid"))
        }
    }

    @Test
    fun load_missingModelFile_throws() {
        writeMetadata(validMetadataJson())

        try {
            SpeechRecognitionWithFiles.load(id = "whisper-base-en", path = modelDir)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not complete and valid"))
        }
    }

    @Test
    fun load_validBundle_loadsModel() {
        writeMetadata(validMetadataJson())
        createModelFiles()

        val model = SpeechRecognitionWithFiles.load(id = "whisper-base-en", path = modelDir)

        assertEquals("whisper-base-en", model.id)
        assertEquals("openai/whisper-base.en", model.baseModel)
        assertEquals(listOf("en"), model.languages)
        assertEquals("v1.0.0", model.version)
        assertEquals(listOf(SpeechRecognitionArchitecture.Whisper), model.architectures)
        assertEquals(modelDir.resolve("model.bin"), model.inference.model)
        assertEquals(modelDir.resolve("vad.bin"), model.inference.vad)
        assertEquals(expectedSize(), model.size)
        assertTrue(model.isValid())
    }

    @Test
    fun inferenceFilesIsValid_missingModel_returnsFalse() {
        val files = SpeechRecognitionInferenceFiles(
            model = modelDir.resolve("model.bin"),
            vad = modelDir.resolve("vad.bin"),
        )

        assertEquals(false, files.isValid())
    }

    @Test
    fun inferenceFilesIsValid_allPresent_returnsTrue() {
        createModelFiles()
        val files = SpeechRecognitionInferenceFiles(
            model = modelDir.resolve("model.bin"),
            vad = modelDir.resolve("vad.bin"),
        )

        assertTrue(files.isValid())
    }
}
