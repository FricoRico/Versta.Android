package app.versta.translate.core.entity

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.writeText

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class ObjectCharacterRecognitionBundleWithFilesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var bundleRoot: Path

    @Before
    fun setUp() {
        bundleRoot = context.cacheDir.toPath()
            .resolve("ocr-bundle-test")
            .resolve(java.util.UUID.randomUUID().toString())
        bundleRoot.createDirectories()
    }

    @After
    fun tearDown() {
        bundleRoot.deleteRecursively()
    }

    private fun writeBundleMetadata() {
        bundleRoot.resolve("metadata.json").writeText(
            """
            {
                "id": "paddle-ocr",
                "version": "v2.0.0",
                "architecture": "PaddleOCR",
                "base_model": "PaddlePaddle/PP-OCRv6",
                "modules": ["detector", "recognizer"],
                "languages": ["en", "zh"],
                "metadata": []
            }
            """.trimIndent()
        )
    }

    private fun writeModule(directory: String, module: String, languages: List<String>, inference: String, vocab: String? = null) {
        val dir = bundleRoot.resolve(directory)
        dir.createDirectories()
        dir.resolve(inference).writeText("fake-weights")
        vocab?.let { dir.resolve(it).writeText("line1\nline2\n") }

        val filesEntry = if (vocab != null) {
            """[{"inference": "$inference", "priority": 1, "vocab": "$vocab"}]"""
        } else {
            """[{"inference": "$inference", "priority": 1}]"""
        }
        val langs = languages.joinToString(",") { "\"$it\"" }
        dir.resolve("metadata.json").writeText(
            """{"module": "$module", "languages": [$langs], "files": $filesEntry}"""
        )
    }

    @Test
    fun recognizerForLanguagePrefersV6OverV5() {
        writeBundleMetadata()
        writeModule("PP-OCRv6_tiny_det", "detector", listOf("*"), "det.mnn")
        writeModule("latin_PP-OCRv5_mobile_rec", "recognizer", listOf("en"), "rec.mnn", "vocab.txt")
        writeModule("PP-OCRv6_tiny_rec", "recognizer", listOf("en", "fr"), "rec6.mnn", "vocab6.txt")

        val bundle = ObjectCharacterRecognitionBundleWithFiles.load(
            id = "paddle-ocr",
            path = bundleRoot,
            modules = listOf(
                ObjectCharacterRecognitionModuleWithFiles.load(
                    "paddle-ocr:PP-OCRv6_tiny_det", "paddle-ocr", "v2.0.0", bundleRoot.resolve("PP-OCRv6_tiny_det")
                ),
                ObjectCharacterRecognitionModuleWithFiles.load(
                    "paddle-ocr:latin_PP-OCRv5_mobile_rec", "paddle-ocr", "v2.0.0", bundleRoot.resolve("latin_PP-OCRv5_mobile_rec")
                ),
                ObjectCharacterRecognitionModuleWithFiles.load(
                    "paddle-ocr:PP-OCRv6_tiny_rec", "paddle-ocr", "v2.0.0", bundleRoot.resolve("PP-OCRv6_tiny_rec")
                ),
            )
        )

        assertNotNull(bundle)
        assertTrue(bundle!!.isComplete)
        assertEquals("PP-OCRv6_tiny_rec", bundle.recognizerForLanguage("en")?.path?.fileName.toString().let {
            bundle.recognizerForLanguage("en")!!.path.fileName.toString()
        })
    }

    @Test
    fun incompleteWithoutDetector() {
        writeModule("PP-OCRv6_tiny_rec", "recognizer", listOf("en"), "rec6.mnn", "vocab6.txt")

        val modules = listOf(
            ObjectCharacterRecognitionModuleWithFiles.load(
                "paddle-ocr:PP-OCRv6_tiny_rec", "paddle-ocr", "v2.0.0", bundleRoot.resolve("PP-OCRv6_tiny_rec")
            )
        )

        val bundle = ObjectCharacterRecognitionBundleWithFiles(
            id = "paddle-ocr",
            path = bundleRoot,
            version = "v2.0.0",
            languages = listOf("en"),
            modules = modules
        )
        assertFalse(bundle.isComplete)
        assertNull(bundle.module(ObjectCharacterRecognitionModule.Detector))
    }

    @Test
    fun moduleMetadataRequiresExistingFiles() {
        val dir = bundleRoot.resolve("empty")
        dir.createDirectories()
        dir.resolve("metadata.json").writeText(
            """{"module": "detector", "languages": ["*"], "files": [{"inference": "missing.mnn", "priority": 1}]}"""
        )

        try {
            ObjectCharacterRecognitionModuleWithFiles.load("x:empty", "x", "v2.0.0", dir)
            org.junit.Assert.fail("Expected failure for missing inference file")
        } catch (_: Exception) {
            // expected
        }
    }
}
