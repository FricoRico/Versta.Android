package app.versta.translate.core.entity

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Bundle round-trip tests for the Supertonic 3 metadata schema: a bundle is
 * only usable when every file in `files` resolves, and voices resolve through
 * the fixed per-language style mapping.
 */
class VoiceWithModelFilesTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .cacheDir
                .resolve("voice-model-files-test")
        root.deleteRecursively()
        root.mkdirs()
    }

    private fun writeBundle(withVocoder: Boolean = true): File {
        val models =
            mutableMapOf(
                "duration_predictor" to "duration_predictor.mnn",
                "text_encoder" to "text_encoder.mnn",
                "vector_estimator" to "vector_estimator.mnn",
                "vocoder" to "vocoder.mnn",
            )
        if (!withVocoder) {
            models.remove("vocoder")
        }

        models.values.forEach { root.resolve(it).writeBytes(byteArrayOf(0)) }
        root.resolve("tts.json").writeText("{}")
        root.resolve("unicode_indexer.json").writeText("[]")
        root.resolve("voice_styles").mkdirs()
        ((1..5).map { "F$it" } + (1..5).map { "M$it" }).forEach {
            root.resolve(Supertonic3.styleFileName(it)).writeText("{}")
        }

        val modelsJson = models.entries.joinToString(", ") { "\"${it.key}\": \"${it.value}\"" }
        root.resolve("metadata.json").writeText(
            """
            {
                "version": "v2.0.0",
                "base_model": "Supertone/supertonic-3",
                "architectures": ["Supertonic3"],
                "files": {
                    "inference": {
                        "models": { $modelsJson },
                        "config": "tts.json"
                    },
                    "tokenizer": { "unicode_indexer": "unicode_indexer.json" },
                    "voices": [
                        ${((1..5).map { "F$it" } + (1..5).map { "M$it" }).joinToString(", ") { "\"${Supertonic3.styleFileName(it)}\"" }}
                    ]
                }
            }
            """.trimIndent(),
        )

        return root
    }

    @Test
    fun loadParsesSupertonic3Bundle() {
        val files = VoiceWithModelFiles.load(Supertonic3.ID, writeBundle().toPath())

        assertEquals(Supertonic3.ID, files.id)
        assertTrue(files.isSupertonic3)
        assertTrue(files.isValid())
        assertEquals(4, files.inference.models.size)
        assertEquals(root.toPath().resolve("unicode_indexer.json"), files.tokenizer.unicodeIndexer)
        assertEquals(10, files.voices.size)
    }

    @Test
    fun loadRejectsIncompleteBundles() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            VoiceWithModelFiles.load(Supertonic3.ID, writeBundle(withVocoder = false).toPath())
        }
    }

    @Test
    fun getVoiceResolvesTheDefaultStylePerLanguageAndGender() {
        val files = VoiceWithModelFiles.load(Supertonic3.ID, writeBundle().toPath())
        val language = Language.fromIsoCode("nl")

        val female = files.getVoice(language, VoiceGender.Female)
        val male = files.getVoice(language, VoiceGender.Male)

        assertEquals(
            root.toPath().resolve(Supertonic3.styleFileName(Supertonic3.defaultStyle(language, VoiceGender.Female))),
            female,
        )
        assertEquals(
            root.toPath().resolve(Supertonic3.styleFileName(Supertonic3.defaultStyle(language, VoiceGender.Male))),
            male,
        )
    }

    @Test
    fun getVoiceReturnsNothingForLanguagesWithoutCoverage() {
        val files = VoiceWithModelFiles.load(Supertonic3.ID, writeBundle().toPath())

        assertNull(files.getVoice(Language.fromIsoCode("zh")))
        assertNull(files.getVoice(Language.fromIsoCode("ca")))
    }

    @Test
    fun legacyBundlesStayParsable() {
        root.resolve("model.onnx").writeBytes(byteArrayOf(0))
        root.resolve("vocab.txt").writeText("")
        root.resolve("af_alpha.bin").writeBytes(byteArrayOf(0))
        root.resolve("metadata.json").writeText(
            """
            {
                "version": "v1.2.0",
                "base_model": "hexgrad/Kokoro-82M",
                "architectures": ["StyleTTS2"],
                "files": {
                    "inference": { "model": "model.onnx" },
                    "tokenizer": { "vocabulary": "vocab.txt" },
                    "voices": ["af_alpha.bin"]
                }
            }
            """.trimIndent(),
        )

        val files = VoiceWithModelFiles.load("kokoro", root.toPath())

        assertFalse(files.isSupertonic3)
        assertTrue(files.isValid())
        assertEquals(root.toPath().resolve("af_alpha.bin"), files.getVoice(Language.fromIsoCode("en")))
    }
}
