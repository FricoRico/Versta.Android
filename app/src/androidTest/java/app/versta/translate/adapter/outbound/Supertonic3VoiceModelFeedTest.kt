package app.versta.translate.adapter.outbound

import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceGender
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

/**
 * Covers the app side of the Supertonic 3 voice-model feed entry NEU-68 ships
 * to `versta_text_to_speech_models.json`: the parser must accept the new
 * architecture alongside the sixty-two voice mappings, and the bundle shows up
 * next to an unrelated install as available rather than as an update.
 */
class Supertonic3VoiceModelFeedTest {
    private fun feedJson(): String =
        """
        [
            {
                "id": "${Supertonic3.ID}",
                "name": "Supertonic 3",
                "base_model": "Supertone/supertonic-3",
                "size": 119000000,
                "version": "v2.0.0",
                "voices": [
                    ${Supertonic3.LANGUAGES.sorted().joinToString(", ") { language ->
            VoiceGender.entries.joinToString(", ") { gender ->
                "{\"gender\": \"${gender.value.lowercase()}\", \"language\": \"$language\"}"
            }
        }}
                ],
                "architectures": ["Supertonic3"],
                "bundle": "https://models.versta.app/text-to-speech/v2.0.0/supertonic3-bundle.tar.gz",
                "checksum": "https://models.versta.app/text-to-speech/v2.0.0/supertonic3-bundle.tar.sha256"
            }
        ]
        """.trimIndent()

    @Test
    fun parsesSupertonic3FeedEntry() =
        runBlocking {
            val repository =
                ExternalVoiceModelsFileRepository(ByteArrayInputStream(feedJson().toByteArray()))

            val definitions = repository.getDefinitions().first { it.isNotEmpty() }

            assertEquals(listOf(Supertonic3.ID), definitions.map { it.id })
            assertEquals(listOf(VoiceModelArchitecture.Supertonic3), definitions.first().architectures)
            assertEquals(62, definitions.first().voices.size)
            assertTrue(definitions.all { it.isValid() })
        }

    @Test
    fun unrelatedInstallDoesNotShadowTheSupertonic3Bundle() =
        runBlocking {
            val repository =
                ExternalVoiceModelsFileRepository(ByteArrayInputStream(feedJson().toByteArray()))
            repository.getDefinitions().first { it.isNotEmpty() }

            val installed =
                VoiceWithModelFiles(
                    id = "other-tts",
                    path = Path.of(""),
                    baseModel = "other/tts",
                    architectures = listOf(VoiceModelArchitecture.Supertonic3),
                    version = "v1.2.0",
                    inference = VoiceModelInferenceFiles(config = Path.of("tts.json")),
                    tokenizer = VoiceModelTokenizerFiles(unicodeIndexer = Path.of("unicode_indexer.json")),
                    voices = VoiceModelVoiceFiles(),
                )

            val state = repository.getDefinitionsByState(flowOf(listOf(installed))).first()

            assertEquals(listOf(Supertonic3.ID), state.available.map { it.id })
            assertTrue(state.installed.isEmpty())
            assertTrue(state.updates.isEmpty())
        }
}
