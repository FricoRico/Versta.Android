package app.versta.translate.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private val textToSpeechData: File get() = context.filesDir.resolve("tts-data")

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
        textToSpeechData.deleteRecursively()
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

    /**
     * `Language` as tagged releases created it, with the `bidirectional`
     * column that [languageV1] (the pre-release shape) lacks.
     */
    private val languageV1WithBidirectional =
        """
        CREATE TABLE Language (
            id TEXT NOT NULL PRIMARY KEY,
            source TEXT NOT NULL,
            target TEXT NOT NULL,
            bidirectional INTEGER DEFAULT 0
        );
        """.trimIndent()

    @Serializable
    private data class CatalogEntry(
        @SerialName("source_language") val sourceLanguage: String,
        @SerialName("target_language") val targetLanguage: String,
        val bidirectional: Boolean,
    )

    /**
     * A pair the bundled catalog marks bidirectional. Fails fixture setup if
     * the catalog ever loses the property [app.versta.translate.database.migrations.Migration1]
     * backfills from.
     */
    private val catalogBidirectionalPair: Pair<String, String> by lazy {
        context.resources.openRawResource(R.raw.versta_translation_models).use { stream ->
            Json { ignoreUnknownKeys = true }
                .decodeFromString<List<List<CatalogEntry>>>(stream.reader().readText())
                .flatten()
                .first { it.bidirectional }
                .let { it.sourceLanguage to it.targetLanguage }
        }
    }

    /** A pair absent from the catalog; the backfill must leave it at 0. */
    private val unknownPair = "zz" to "xx"

    private fun insertLegacyLanguage(
        database: SQLiteDatabase,
        pair: Pair<String, String>,
    ) {
        database.execSQL(
            "INSERT INTO Language (id, source, target) VALUES (?, ?, ?)",
            arrayOf("${pair.first}-${pair.second}", pair.first, pair.second),
        )
    }

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

    private fun insertVoice(
        database: SQLiteDatabase,
        modelId: String,
        language: String,
        gender: String,
    ) {
        database.execSQL(
            "INSERT INTO Voice (modelId, language, gender) VALUES (?, ?, ?)",
            arrayOf(modelId, language, gender),
        )
    }

    private fun insertTextToSpeechData(database: SQLiteDatabase) {
        val path = textToSpeechData.resolve("payload")
        path.mkdirs()
        path.resolve("espeak-ng-data").writeText("legacy")

        database.execSQL(
            "INSERT INTO Data (id, type, path, version) VALUES (?, ?, ?, ?)",
            arrayOf("versta-tts-data", "tts", path.absolutePath, "v1.0.0"),
        )
    }

    /**
     * Migrations never register Supertonic 3 voice rows: the bundle-install
     * transaction owns them so they stay parented to their model row under
     * foreign-key enforcement (NEU-65). Install-path registration is covered
     * by `VoiceDatabaseRepositoryTest`.
     */
    private fun assertNoVoiceRowsRemained(container: DatabaseContainer) {
        assertTrue(
            container.voices
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
    }

    @Test
    fun freshInstall_createsCurrentSchemaWithoutRunningMigrations() {
        val container = container()

        // The database file is created lazily on first query, so the version
        // assert comes last: storedVersion() opens read-only and throws while
        // the file does not exist yet.
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
        assertEquals(Database.Schema.version, storedVersion())
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
    fun v1Database_addsMissingBidirectionalColumnAndBackfillsFromCatalog() {
        databaseV1()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertLegacyLanguage(database, catalogBidirectionalPair)
            insertLegacyLanguage(database, unknownPair)
        }

        val languages = container().languages.getAll().executeAsList()

        val repaired = languages.single { it.source == catalogBidirectionalPair.first && it.target == catalogBidirectionalPair.second }
        assertEquals("catalog-bidirectional pair must be backfilled to 1", true, repaired.bidirectional)

        val unknown = languages.single { it.source == unknownPair.first && it.target == unknownPair.second }
        assertEquals("rows absent from the catalog must stay 0", false, unknown.bidirectional)
    }

    @Test
    fun v1Database_bidirectionalQueryWorksAfterUpgrade() {
        databaseV1()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertLegacyLanguage(database, catalogBidirectionalPair)
        }

        // Crashes with "no such column: bidirectional" without Migration1.
        val matches =
            container().languages.getBySourceIncludingBidirecional(catalogBidirectionalPair.first).executeAsList()

        assertTrue(matches.any { it.source == catalogBidirectionalPair.first && it.target == catalogBidirectionalPair.second })
    }

    @Test
    fun v1Database_withBidirectionalColumnIsLeftUntouched() {
        legacyDatabase(1, listOf(languageV1WithBidirectional, languageModelV1))
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            // Stored as 0 although the catalog says bidirectional: only a
            // database missing the column is backfilled.
            insertLegacyLanguage(database, catalogBidirectionalPair)
        }

        val languages = container().languages.getAll().executeAsList()

        assertEquals(false, languages.single().bidirectional)
    }

    @Test
    fun v3Database_chainEndsWithLegacyVoiceModelsPurged() {
        databaseV3()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertVoiceModel(database, "voice-surviving", "v1.4.0")
            insertVoiceModel(database, "voice-legacy", "v1.1.0")
        }

        val container = container()

        // Migration4 keeps current versions before Migration7 purges every
        // pre-Supertonic voice model.
        assertTrue(
            container.voiceModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertFalse(voiceModelsRoot.resolve("voice-legacy").exists())
        assertFalse(voiceModelsRoot.resolve("voice-surviving").exists())

        assertNoVoiceRowsRemained(container)
    }

    @Test
    fun v4Database_purgesCurrentAndFutureVersionedVoiceModelsForSupertonic3() {
        databaseV4()
        writeExternalDataFixtures()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertVoiceModel(database, "voice-legacy", "v1.1.0")
            insertVoiceModel(database, "voice-current", "v1.4.0")
            insertVoiceModel(database, "voice-future", "v1.10.0")
            insertVoice(database, "voice-current", "en", "female")
        }

        val container = container()

        assertTrue(
            container.voiceModels
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertFalse(voiceModelsRoot.resolve("voice-legacy").exists())
        assertFalse(voiceModelsRoot.resolve("voice-future").exists())

        assertNoVoiceRowsRemained(container)

        assertFalse(externalData.exists())
    }

    @Test
    fun v6Database_removesTextToSpeechDataWithoutRegisteringVoices() {
        databaseV6()
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            insertTextToSpeechData(database)
        }

        val container = container()

        assertTrue(
            container.data
                .getAll()
                .executeAsList()
                .isEmpty(),
        )
        assertFalse(textToSpeechData.exists())
        assertNoVoiceRowsRemained(container)

        container.driver.close()
        containers.remove(container)

        // Reopening the migrated database runs no migration again; no voice
        // rows appear outside the bundle-install transaction.
        assertNoVoiceRowsRemained(container())
    }

    @Test
    fun v1Database_purgesVoiceRowsThroughFullUpgrade() {
        databaseV1()

        val container = container()

        assertNoVoiceRowsRemained(container)
    }

    @Test
    fun v6Database_runsLanguageModelMigrationAndSupertonic3Cutover() {
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

        // Database is past Migration4's boundary, so the legacy voice model is
        // not deleted twice — Migration7 removes it exactly once, with files.
        assertNull(container.voiceModels.getById("voice-legacy").executeAsOneOrNull())
        assertFalse(voiceModelsRoot.resolve("voice-legacy").exists())
        assertNoVoiceRowsRemained(container)
    }

    @Test
    fun v8Database_runsNoMigrationsAcrossColdStarts() {
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
