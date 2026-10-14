package app.versta.translate.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Upgrade-path tests for [DatabaseContainer]: every supported on-disk version
 * must migrate cleanly, each manual migration runs only when its version
 * boundary is crossed, and migration failures crash instead of being swallowed.
 *
 * Fixtures hand-build the schema of each historical version (the `.sqm` files
 * are the authoritative deltas) and let the real driver auto-migrate forward,
 * exactly like a device upgrade.
 */
class DatabaseContainerTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)
    private val externalData: File get() = context.filesDir.resolve("external-data")
    private val externalDataHash: File get() = context.filesDir.resolve("external-data.sha256")
    private val voiceModelsRoot: File get() = context.filesDir.resolve("voice-models")

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
        externalData.deleteRecursively()
        externalDataHash.delete()
        voiceModelsRoot.deleteRecursively()
    }

    private fun container(): DatabaseContainer = DatabaseContainer(context).also { containers.add(it) }

    private val languageV1 =
        """
        CREATE TABLE Language (
            id TEXT NOT NULL PRIMARY KEY,
            source TEXT NOT NULL,
            target TEXT NOT NULL
        );
        """.trimIndent()

    private val languageModelV1 =
        """
        CREATE TABLE LanguageModel (
            languageId TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            files TEXT NOT NULL
        );
        """.trimIndent()

    private val voiceModelV2 =
        """
        CREATE TABLE VoiceModel (
            id TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    private val voiceV4 =
        """
        CREATE TABLE Voice (
            modelId TEXT NOT NULL REFERENCES VoiceModel(id) ON DELETE CASCADE,
            language TEXT NOT NULL,
            gender TEXT NOT NULL,

            UNIQUE(modelId, language, gender)
        );
        """.trimIndent()

    private val dataV5 =
        """
        CREATE TABLE Data (
            id TEXT NOT NULL PRIMARY KEY,
            type TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    private val ocrV6 =
        """
        CREATE TABLE ObjectCharacterRecognitionDetectorModel (
            id TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );

        CREATE TABLE ObjectCharacterRecognitionRecognizerModel (
            id TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    private fun legacyDatabase(
        version: Int,
        statements: List<String>,
    ) {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        statements.forEach { database.execSQL(it) }
        database.version = version
        database.close()
    }

    private fun databaseV1() = legacyDatabase(1, listOf(languageV1, languageModelV1))

    private fun databaseV3() = legacyDatabase(3, listOf(languageV1, languageModelV1, voiceModelV2))

    private fun databaseV4() = legacyDatabase(4, listOf(languageV1, languageModelV1, voiceModelV2, voiceV4))

    private fun databaseV6() = legacyDatabase(6, listOf(languageV1, languageModelV1, voiceModelV2, voiceV4, dataV5, ocrV6))

    private fun storedVersion(): Long =
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            DatabaseUtils.longForQuery(database, "PRAGMA user_version", null)
        }

    private fun insertLegacyLanguageModel(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO LanguageModel (languageId, baseModel, architectures, path, files) VALUES (?, ?, ?, ?, ?)",
            arrayOf("en", "bergamot-tiny", "[\"Bergamot\"]", "/nonexistent", "{}"),
        )
    }

    private fun writeExternalDataFixtures() {
        externalData.mkdirs()
        externalData.resolve("payload.bin").writeText("legacy")
        externalDataHash.writeText("hash")
    }

    private fun writeVoiceModelFixtures(
        id: String,
        withMetadata: Boolean = true,
    ): File {
        val root = voiceModelsRoot.resolve(id)
        val model = root.resolve("model")
        model.mkdirs()

        if (withMetadata) {
            model.resolve("model.onnx").writeBytes(byteArrayOf(0))
            model.resolve("vocab.txt").writeText("")
            model.resolve("af_alpha.bin").writeBytes(byteArrayOf(0))
            model.resolve("pm_beta.bin").writeBytes(byteArrayOf(0))
            model.resolve("metadata.json").writeText(
                """
                {
                    "version": "v1.4.0",
                    "base_model": "styletts2",
                    "architectures": ["StyleTTS2"],
                    "files": {
                        "inference": { "model": "model.onnx" },
                        "tokenizer": { "vocabulary": "vocab.txt" },
                        "voices": ["af_alpha.bin", "pm_beta.bin"]
                    }
                }
                """.trimIndent(),
            )
        }

        return model
    }

    private fun insertVoiceModel(
        database: SQLiteDatabase,
        id: String,
        version: String,
        withMetadata: Boolean = true,
    ): File {
        val model = writeVoiceModelFixtures(id, withMetadata)
        database.execSQL(
            "INSERT INTO VoiceModel (id, baseModel, architectures, path, version) VALUES (?, ?, ?, ?, ?)",
            arrayOf(id, "styletts2", "[\"Kokoro\"]", model.absolutePath, version),
        )

        return model.parentFile!!
    }

    @Test
    fun freshInstall_createsCurrentSchemaWithoutRunningMigrations() {
        val container = container()

        assertEquals(Database.Schema.version, storedVersion())
        assertTrue(
            container.voices
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            container.voiceModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            container.speechRecognitionModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
    }

    @Test
    fun v1Database_upgradesAcrossEverySchemaMigration() {
        databaseV1()
        writeExternalDataFixtures()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { insertLegacyLanguageModel(it) }

        val container = container()

        assertEquals(Database.Schema.version, storedVersion())
        assertTrue(
            container.speechRecognitionModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertTrue(
            container.languageModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )

        assertFalse(externalData.exists())
        assertFalse(externalDataHash.exists())
    }

    @Test
    fun v3Database_backfillsVoicesAndCleansLegacyVoiceModels() {
        databaseV3()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertVoiceModel(database, "voice-surviving", "v1.4.0")
            insertVoiceModel(database, "voice-legacy", "v1.1.0")
        }

        val container = container()

        val backfilled =
            container.voices
                .getAll()
                .executeAsList()
                .filter { it.modelId == "voice-surviving" }
                .map { "${it.language}:${it.gender}" }
                .toSet()
        assertEquals(setOf("en:female", "pt:male"), backfilled)

        assertNull(container.voiceModels.getById("voice-legacy").executeAsOneOrNull())
        assertFalse(voiceModelsRoot.resolve("voice-legacy").exists())

        val surviving = container.voiceModels.getById("voice-surviving").executeAsOne()
        assertEquals(listOf("StyleTTS2"), surviving.architectures)
        assertTrue(voiceModelsRoot.resolve("voice-surviving/model/model.onnx").exists())
    }

    @Test
    fun v4Database_keepsCurrentAndFutureVersionedVoiceModels() {
        databaseV4()
        writeExternalDataFixtures()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertVoiceModel(database, "voice-legacy", "v1.1.0")
            insertVoiceModel(database, "voice-current", "v1.4.0")
            insertVoiceModel(database, "voice-future", "v1.10.0")
        }

        val container = container()

        assertNull(container.voiceModels.getById("voice-legacy").executeAsOneOrNull())
        assertNotNull(container.voiceModels.getById("voice-current").executeAsOneOrNull())
        assertNotNull(container.voiceModels.getById("voice-future").executeAsOneOrNull())

        assertFalse(voiceModelsRoot.resolve("voice-legacy").exists())
        assertTrue(voiceModelsRoot.resolve("voice-future/model/model.onnx").exists())

        assertEquals(
            listOf("StyleTTS2"),
            container.voiceModels
                .getById("voice-future")
                .executeAsOne()
                .architectures,
        )

        assertFalse(externalData.exists())
    }

    @Test
    fun v6Database_runsOnlyLanguageModelMigrationWhichCannotSeeDroppedRows() {
        databaseV6()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertVoiceModel(database, "voice-legacy", "v1.1.0")
            insertLegacyLanguageModel(database)
        }

        val container = container()

        assertEquals(Database.Schema.version, storedVersion())
        assertTrue(
            container.languageModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )

        // Database is past Migration4's boundary: legacy voice models and files
        // stay untouched instead of being re-processed on every cold start.
        assertNotNull(container.voiceModels.getById("voice-legacy").executeAsOneOrNull())
        assertTrue(voiceModelsRoot.resolve("voice-legacy/model/model.onnx").exists())
    }

    @Test
    fun currentDatabase_runsNoMigrationsAcrossColdStarts() {
        val first = container()
        val installed = writeVoiceModelFixtures("voice-legacy")
        first.voiceModels.upsert(
            id = "voice-legacy",
            baseModel = "styletts2",
            architectures = listOf("Kokoro"),
            path = installed.absolutePath,
            version = "v1.1.0",
        )
        writeExternalDataFixtures()
        first.driver.close()

        val second = container()

        assertNotNull(second.voiceModels.getById("voice-legacy").executeAsOneOrNull())
        assertTrue(voiceModelsRoot.resolve("voice-legacy/model/model.onnx").exists())
        assertTrue(externalData.exists())
    }

    @Test
    fun migrationFailure_crashesInsteadOfContinuing() {
        databaseV3()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertVoiceModel(database, "voice-incomplete", "v1.4.0", withMetadata = false)
        }

        try {
            container()
            fail("Migration failure must crash instead of being logged and swallowed")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("metadata"))
        }
    }
}
