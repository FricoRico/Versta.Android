package app.versta.translate.core.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalSpeechRecognitionModelTest {

    private fun definition(
        id: String = "whisper-base-en",
        name: String = "Whisper Base (English)",
        baseModel: String = "openai/whisper-base.en",
        architectures: List<SpeechRecognitionArchitecture> = listOf(SpeechRecognitionArchitecture.Whisper),
        languages: List<String> = listOf("en"),
        size: Long = 147322514,
        version: String = "v1.0.0",
        bundle: String = "https://mock.versta.app/bundle.tar.gz",
        checksum: String = "https://mock.versta.app/bundle.tar.gz.sha256",
    ): ExternalSpeechRecognitionModelDefinition {
        return ExternalSpeechRecognitionModelDefinition(
            id = id,
            name = name,
            baseModel = baseModel,
            architectures = architectures,
            languages = languages,
            size = size,
            version = version,
            bundle = bundle,
            checksum = checksum,
        )
    }

    @Test
    fun definition_validFields_isValid() {
        assertTrue(definition().isValid())
    }

    @Test
    fun definition_blankId_isInvalid() {
        assertFalse(definition(id = "  ").isValid())
    }

    @Test
    fun definition_blankName_isInvalid() {
        assertFalse(definition(name = "").isValid())
    }

    @Test
    fun definition_blankBaseModel_isInvalid() {
        assertFalse(definition(baseModel = "").isValid())
    }

    @Test
    fun definition_emptyLanguages_isInvalid() {
        assertFalse(definition(languages = emptyList()).isValid())
    }

    @Test
    fun definition_emptyArchitectures_isInvalid() {
        assertFalse(definition(architectures = emptyList()).isValid())
    }

    @Test
    fun definition_zeroSize_isInvalid() {
        assertFalse(definition(size = 0).isValid())
    }

    @Test
    fun definition_negativeSize_isInvalid() {
        assertFalse(definition(size = -1).isValid())
    }

    @Test
    fun definition_blankVersion_isInvalid() {
        assertFalse(definition(version = "").isValid())
    }

    @Test
    fun definition_blankBundle_isInvalid() {
        assertFalse(definition(bundle = "").isValid())
    }

    @Test
    fun definition_blankChecksum_isInvalid() {
        assertFalse(definition(checksum = "  ").isValid())
    }

    @Test
    fun definition_bundleUri_parses() {
        val model = definition(bundle = "https://mock.versta.app/whisper-base-en.tar.gz")

        assertEquals("https://mock.versta.app/whisper-base-en.tar.gz", model.bundleUri().toString())
    }

    @Test
    fun definition_checksumUri_parses() {
        val model = definition(checksum = "https://mock.versta.app/whisper-base-en.tar.gz.sha256")

        assertEquals(
            "https://mock.versta.app/whisper-base-en.tar.gz.sha256",
            model.checksumUri().toString()
        )
    }

    @Test
    fun downloadTask_getWorkData_containsTaskIdentity() {
        val model = definition()
        val task = ExternalSpeechRecognitionDownloadTask(
            model = model,
            status = DownloadStatus.Queued,
        )

        val data = task.getWorkData()

        assertEquals(task.id.toString(), data["taskId"])
        assertEquals(model.name, data["name"])
        assertEquals(model.bundleUri().toString(), data["uri"])
        assertEquals(model.checksumUri().toString(), data["checksum"])
    }

    @Test
    fun downloadTask_getName_returnsModelName() {
        val model = definition(name = "Whisper Base (English)")
        val task = ExternalSpeechRecognitionDownloadTask(model = model, status = DownloadStatus.Idle)

        assertEquals("Whisper Base (English)", task.getName())
    }

    @Test
    fun downloadTask_copyWithStatus_preservesModel() {
        val model = definition()
        val task = ExternalSpeechRecognitionDownloadTask(model = model, status = DownloadStatus.Queued)

        val copied = task.copyWithStatus(DownloadStatus.Progress(downloaded = 10, total = 100))

        assertEquals(DownloadStatus.Progress(downloaded = 10, total = 100), copied.status)
        assertEquals(model, (copied as ExternalSpeechRecognitionDownloadTask).model)
    }

    @Test
    fun downloadTask_onComplete_invokesCallback() {
        val model = definition()
        var completed: ExternalSpeechRecognitionModelDefinition? = null
        val task = ExternalSpeechRecognitionDownloadTask(
            model = model,
            status = DownloadStatus.Queued,
            onComplete = { completed = it },
        )

        task.onComplete()

        assertSame(model, completed)
    }

    @Test
    fun downloadTask_defaultOnComplete_doesNothing() {
        val task = ExternalSpeechRecognitionDownloadTask(model = definition(), status = DownloadStatus.Queued)

        task.onComplete()
    }
}

