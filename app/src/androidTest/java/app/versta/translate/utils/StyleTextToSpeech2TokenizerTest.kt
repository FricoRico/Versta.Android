package app.versta.translate.utils

import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.adapter.outbound.StyleTextToSpeech2Tokenizer
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceModelInferenceFiles
import app.versta.translate.core.entity.VoiceModelVoiceFiles
import app.versta.translate.core.entity.VoiceWithModelFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.nio.file.Files

class StyleTextToSpeech2TokenizerTest {

    @Test
    fun testTokenizerWithoutVocabularyFile() {
        val tokenizer = StyleTextToSpeech2Tokenizer()
        
        // Create a mock VoiceWithModelFiles without vocabulary
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tempDir = Files.createTempDirectory("test_voice_model")
        val modelFile = tempDir.resolve("model.onnx")
        Files.createFile(modelFile)
        
        val voiceFiles = VoiceModelVoiceFiles()
        val files = VoiceWithModelFiles(
            id = "test-model",
            path = tempDir,
            baseModel = "test",
            architectures = listOf(VoiceModelArchitecture.StyleTTS2),
            version = "v1.1.0", // Pre-vocabulary version
            inference = VoiceModelInferenceFiles(model = modelFile),
            voices = voiceFiles,
            vocabulary = null
        )
        
        // Load should work without throwing exception
        tokenizer.load(files)
        
        // Test tokenization works with default vocabulary
        val result = tokenizer.tokenize("Hello", Language.fromIsoCode("en"))
        assertNotNull(result)
        
        // Cleanup
        Files.deleteIfExists(modelFile)
        Files.deleteIfExists(tempDir)
    }

    @Test
    fun testTokenizerVersionCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tempDir = Files.createTempDirectory("test_voice_model")
        val modelFile = tempDir.resolve("model.onnx")
        Files.createFile(modelFile)
        
        val voiceFiles = VoiceModelVoiceFiles()
        
        // Test with version that supports vocabulary files
        val filesV12 = VoiceWithModelFiles(
            id = "test-model",
            path = tempDir,
            baseModel = "test",
            architectures = listOf(VoiceModelArchitecture.Kokoro),
            version = "v1.2.0",
            inference = VoiceModelInferenceFiles(model = modelFile),
            voices = voiceFiles,
            vocabulary = null
        )
        
        assertEquals(true, filesV12.supportsVocabularyFile())
        
        // Test with version that doesn't support vocabulary files
        val filesV11 = VoiceWithModelFiles(
            id = "test-model",
            path = tempDir,
            baseModel = "test",
            architectures = listOf(VoiceModelArchitecture.StyleTTS2),
            version = "v1.1.0",
            inference = VoiceModelInferenceFiles(model = modelFile),
            voices = voiceFiles,
            vocabulary = null
        )
        
        assertEquals(false, filesV11.supportsVocabularyFile())
        
        // Cleanup
        Files.deleteIfExists(modelFile)
        Files.deleteIfExists(tempDir)
    }

    companion object {
        private const val TAG: String = "StyleTextToSpeech2TokenizerTest"
    }
}