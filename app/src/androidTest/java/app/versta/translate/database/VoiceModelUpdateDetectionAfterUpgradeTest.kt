package app.versta.translate.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.adapter.outbound.ExternalVoiceModelsFileRepository
import app.versta.translate.core.entity.VoiceWithModelFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/**
 * The audit case (NEU-4 §4, project NEU-56 workstream 3): on a device
 * upgraded through the migration chain, model update detection must order
 * dotted versions numerically. Installed `v1.2.0` with catalog `v1.10.0`
 * offers exactly one update; the reverse offers none. String compares invert
 * that ordering and silently stop model packs from reaching devices.
 *
 * The fixture is the v4 database all Play devices run today (v1.3.1), so the
 * production upgrade path runs before the detection is exercised.
 */
class VoiceModelUpdateDetectionAfterUpgradeTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)
    private val voiceModelsRoot: File get() = context.filesDir.resolve("voice-models")
    private val externalData: File get() = context.filesDir.resolve("external-data")
    private val externalDataHash: File get() = context.filesDir.resolve("external-data.sha256")

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
        voiceModelsRoot.deleteRecursively()
        externalData.deleteRecursively()
        externalDataHash.delete()
    }

    private fun catalog(version: String): String =
        """
        [
            {
                "id": "voice-kept",
                "name": "StyleTTS2 English/Dutch",
                "base_model": "styletts2",
                "size": 123456789,
                "version": "$version",
                "voices": [
                    { "gender": "female", "language": "en" },
                    { "gender": "male", "language": "pt" }
                ],
                "architectures": ["StyleTTS2"],
                "bundle": "https://mock.versta.app/voice-kept-bundle.tar.gz",
                "checksum": "https://mock.versta.app/voice-kept-bundle.tar.gz.sha256"
            }
        ]
        """.trimIndent()

    /**
     * Upgrades a pristine v4 database that still has "voice-kept" installed at
     * [installedVersion], then hands the on-device state to the production
     * update-detection seam with a catalog entry at [catalogVersion].
     */
    private fun detectUpdates(
        installedVersion: String,
        catalogVersion: String,
    ) = runBlocking {
        val model =
            ShippedDatabaseFixtures.writeVoiceModelFiles(context, "voice-kept", installedVersion)

        ShippedDatabaseFixtures.DATABASE_V4.write(context, databaseFile)
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                "UPDATE VoiceModel SET path = ?, version = ? WHERE id = ?",
                arrayOf(model.absolutePath, installedVersion, "voice-kept"),
            )
        }

        val container = DatabaseContainer(context).also { containers.add(it) }
        container.driver.close()

        val repository = ExternalVoiceModelsFileRepository(ByteArrayInputStream(catalog(catalogVersion).toByteArray()))
        val definitions = repository.getDefinitions().first { it.isNotEmpty() }

        val imported = flowOf(listOf(VoiceWithModelFiles.load("voice-kept", model.toPath())))
        val models = repository.getDefinitionsByState(imported).first()

        definitions to models
    }

    @Test
    fun catalogAheadOfInstalledOffersExactlyOneUpdate() {
        val (definitions, models) = detectUpdates(installedVersion = "v1.2.0", catalogVersion = "v1.10.0")

        assertEquals(
            "catalog v1.10.0 over installed v1.2.0 offers exactly one update",
            listOf(definitions.single()),
            models.updates,
        )
        assertTrue(models.available.isEmpty())
    }

    @Test
    fun installedAheadOfCatalogOffersNoUpdate() {
        val (_, models) = detectUpdates(installedVersion = "v1.10.0", catalogVersion = "v1.2.0")

        assertTrue("installed v1.10.0 with catalog v1.2.0 offers no update", models.updates.isEmpty())
        assertTrue(models.available.isEmpty())
    }
}
