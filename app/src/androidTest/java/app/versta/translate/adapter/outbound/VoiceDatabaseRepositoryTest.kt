package app.versta.translate.adapter.outbound

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.core.entity.Language
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceBundleMetadata
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.entity.VoiceInferenceFilesMetadata
import app.versta.translate.core.entity.VoiceMetadataFile
import app.versta.translate.core.entity.VoiceModel
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.core.entity.VoiceModelFilesMetadata
import app.versta.translate.core.entity.VoiceModelMetadata
import app.versta.translate.core.entity.VoiceTokenizerFilesMetadata
import app.versta.translate.database.DatabaseContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Bundle-install registration tests for [VoiceDatabaseRepository]: installing
 * a Supertonic 3 bundle registers the model row and its sixty-two voice
 * mappings in one transaction — the only place `Voice` rows come into
 * existence, so they stay parented to their model row under foreign-key
 * enforcement (NEU-65). Reinstalls are idempotent, and a broken bundle must
 * leave the database untouched.
 */
class VoiceDatabaseRepositoryTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)
    private val bundleRoot: File get() = context.cacheDir.resolve("voice-database-repository-test")

    @Before
    fun setUp() {
        cleanState()
    }

    @After
    fun tearDown() {
        containers.forEach { it.driver.close() }
        cleanState()
    }

    private fun cleanState() {
        SQLiteDatabase.deleteDatabase(databaseFile)
        bundleRoot.deleteRecursively()
    }

    private fun container(): DatabaseContainer = DatabaseContainer(context).also { containers.add(it) }

    private fun repository(container: DatabaseContainer) = VoiceDatabaseRepository(container)

    private fun writeSupertonic3Bundle(): File {
        bundleRoot.deleteRecursively()
        bundleRoot.mkdirs()

        Supertonic3.MODEL_STAGES.forEach { bundleRoot.resolve("$it.mnn").writeBytes(byteArrayOf(0)) }
        bundleRoot.resolve("tts.json").writeText("{}")
        bundleRoot.resolve("unicode_indexer.json").writeText("[]")
        bundleRoot.resolve(Supertonic3.STYLES_DIRECTORY).mkdirs()
        ((1..5).map { "F$it" } + (1..5).map { "M$it" }).forEach {
            bundleRoot.resolve(Supertonic3.styleFileName(it)).writeText("{}")
        }

        return bundleRoot
    }

    private fun supertonic3Model(root: File): VoiceModel {
        val metadata =
            VoiceModelMetadata(
                version = "v2.0.0",
                baseModel = "Supertone/supertonic-3",
                architectures = listOf(VoiceModelArchitecture.Supertonic3),
                files =
                    VoiceModelFilesMetadata(
                        inference =
                            VoiceInferenceFilesMetadata(
                                models = Supertonic3.MODEL_STAGES.associateWith { "$it.mnn" },
                                config = "tts.json",
                            ),
                        tokenizer = VoiceTokenizerFilesMetadata(unicodeIndexer = "unicode_indexer.json"),
                        voices =
                            ((1..5).map { "F$it" } + (1..5).map { "M$it" }).map {
                                Supertonic3.styleFileName(it)
                            },
                    ),
            ).setRootPath(root.toPath())

        require(metadata.isValid()) { "Fixture bundle must satisfy the metadata contract" }

        return VoiceModel(
            bundle = VoiceBundleMetadata(Supertonic3.ID, "v2.0.0", VoiceMetadataFile("supertonic3")),
            model = metadata,
        )
    }

    private fun brokenModel(root: File): VoiceModel {
        root.mkdirs()

        return VoiceModel(
            bundle = VoiceBundleMetadata("voice-broken", "v1.4.0", VoiceMetadataFile("voice-broken")),
            model =
                VoiceModelMetadata(
                    version = "v1.4.0",
                    baseModel = "unknown/unknown",
                    // No recognized architecture: voice mapping fails.
                    architectures = emptyList(),
                    files =
                        VoiceModelFilesMetadata(
                            inference = VoiceInferenceFilesMetadata(),
                            tokenizer = VoiceTokenizerFilesMetadata(),
                            voices = emptyList(),
                        ),
                ).setRootPath(root.toPath()),
        )
    }

    @Test
    fun upsertSupertonic3BundleRegistersModelWithSixtyTwoVoices() {
        val container = container()
        repository(container).upsertVoiceModel(supertonic3Model(writeSupertonic3Bundle()))

        assertNotNull(container.voiceModels.getById(Supertonic3.ID).executeAsOneOrNull())

        val voices = container.voices.getAll().executeAsList()
        assertEquals(62, voices.size)
        assertTrue(voices.all { it.modelId == Supertonic3.ID })
        assertEquals(
            Supertonic3.LANGUAGES,
            voices.map { it.language }.toSet(),
        )
        assertEquals(
            Supertonic3.LANGUAGES.size * 2,
            voices.map { "${it.language}:${it.gender}" }.distinct().size,
        )
    }

    @Test
    fun upsertSupertonic3BundleTwiceStaysIdempotent() {
        val container = container()
        val repository = repository(container)
        val model = supertonic3Model(writeSupertonic3Bundle())

        repository.upsertVoiceModel(model)
        repository.upsertVoiceModel(model)

        assertEquals(
            1,
            container.voiceModels
                .getAll()
                .executeAsList()
                .size,
        )
        assertEquals(
            62,
            container.voices
                .getAll()
                .executeAsList()
                .size,
        )
    }

    @Test
    fun voicesBecomeAvailableForCoveredLanguagesOnly() {
        val container = container()
        val repository = repository(container)

        repository.upsertVoiceModel(supertonic3Model(writeSupertonic3Bundle()))

        val files = repository.getVoiceModelsByLanguage(Language.fromIsoCode("en"))
        assertNotNull(files)
        assertNotNull(files?.getVoice(Language.fromIsoCode("en"), VoiceGender.Female))
        assertNotNull(files?.getVoice(Language.fromIsoCode("en"), VoiceGender.Male))

        assertNull(repository.getVoiceModelsByLanguage(Language.fromIsoCode("ca")))
    }

    @Test
    fun brokenBundleLeavesNoRowsBehind() {
        val container = container()
        val repository = repository(container)

        assertThrows(Exception::class.java) {
            repository.upsertVoiceModel(brokenModel(context.cacheDir.resolve("voice-broken")))
        }

        assertTrue(
            container.voiceModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            container.voices
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
    }
}
