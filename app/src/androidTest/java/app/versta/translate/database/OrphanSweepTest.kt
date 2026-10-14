package app.versta.translate.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.utils.CrashReporter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Tests for the one-time orphan sweep (Migration8, NEU-65): row deletions,
 * file pruning, telemetry event contents, and the version gating behind the
 * witness `8.sqm` (runs exactly once for devices at or below schema 8, never
 * for fresh installs).
 *
 * Fixtures hand-build the version-8 shape of only the tables the sweep reads;
 * the real driver then applies the witness migration on open, exactly like a
 * device upgrading from the previous release.
 */
class OrphanSweepTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)

    private val languageV8 =
        """
        CREATE TABLE Language (
            id TEXT NOT NULL PRIMARY KEY,
            source TEXT NOT NULL,
            target TEXT NOT NULL,
            bidirectional INTEGER DEFAULT 0
        );
        """.trimIndent()

    private val languageModelV8 =
        """
        CREATE TABLE LanguageModel (
            languageId TEXT NOT NULL PRIMARY KEY REFERENCES Language(id) ON DELETE CASCADE,
            baseModel TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    private val voiceModelV8 =
        """
        CREATE TABLE VoiceModel (
            id TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    private val voiceV8 =
        """
        CREATE TABLE Voice (
            modelId TEXT NOT NULL REFERENCES VoiceModel(id) ON DELETE CASCADE,
            language TEXT NOT NULL,
            gender TEXT NOT NULL,

            UNIQUE(modelId, language, gender)
        );
        """.trimIndent()

    private class RecordingCrashReporter : CrashReporter {
        val events = mutableListOf<Pair<String, Map<String, Long>>>()

        override fun recordEvent(
            name: String,
            attributes: Map<String, Long>,
        ) {
            events += name to attributes
        }

        fun onlyEvent(): Map<String, Long> {
            assertEquals(1, events.size)
            assertEquals("orphan_sweep", events[0].first)
            return events[0].second
        }
    }

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
        context.filesDir
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith(FIXTURE_PREFIX) }
            .forEach { it.deleteRecursively() }
    }

    private fun container(reporter: CrashReporter): DatabaseContainer = DatabaseContainer(context, crashReporter = reporter).also { containers.add(it) }

    private fun databaseV8() {
        val database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        listOf(languageV8, languageModelV8, voiceModelV8, voiceV8).forEach { database.execSQL(it) }
        database.version = 8
        database.close()
    }

    private fun storedVersion(): Long =
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            DatabaseUtils.longForQuery(database, "PRAGMA user_version", null)
        }

    private fun insertOrphanRows() {
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                "INSERT INTO Voice (modelId, language, gender) VALUES (?, ?, ?)",
                arrayOf("ghost-voice-model", "en", "female"),
            )
            database.execSQL(
                "INSERT INTO LanguageModel (languageId, baseModel, path, version) VALUES (?, ?, ?, ?)",
                arrayOf("ghost-language", "leanmt", "/nonexistent", "v2.0.0"),
            )
        }
    }

    private fun insertReferencedRows(
        liveVoiceModel: String,
        liveLanguage: String,
        liveModelPath: String,
    ) {
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                "INSERT INTO VoiceModel (id, baseModel, architectures, path, version) VALUES (?, ?, ?, ?, ?)",
                arrayOf(liveVoiceModel, "styletts2", "[\"StyleTTS2\"]", "/nonexistent", "v1.4.0"),
            )
            database.execSQL(
                "INSERT INTO Voice (modelId, language, gender) VALUES (?, ?, ?)",
                arrayOf(liveVoiceModel, "en", "female"),
            )
            database.execSQL(
                "INSERT INTO Language (id, source, target, bidirectional) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(liveLanguage, "en", "nl", 0),
            )
            database.execSQL(
                "INSERT INTO LanguageModel (languageId, baseModel, path, version) VALUES (?, ?, ?, ?)",
                arrayOf(liveLanguage, "leanmt", liveModelPath, "v2.1.0"),
            )
        }
    }

    private fun writeBundle(
        name: String,
        modelDirectories: List<String>,
    ): File {
        val root = context.filesDir.resolve("$FIXTURE_PREFIX$name")
        root.mkdirs()

        val entries =
            modelDirectories.joinToString(",") { directory ->
                val source = directory.substringBefore("-")
                val target = directory.substringAfter("-")
                """{"directory":"$directory","source_language":"$source","target_language":"$target"}"""
            }
        val languages =
            modelDirectories
                .flatMap { listOf(it.substringBefore("-"), it.substringAfter("-")) }
                .distinct()
                .joinToString(",") { "\"$it\"" }

        root.resolve("metadata.json").writeText(
            """{"version":"v2.1.0","metadata":[$entries],"bidirectional":false,"languages":[$languages]}""",
        )

        modelDirectories.forEach { directory ->
            root.resolve(directory).mkdirs()
            root.resolve(directory).resolve("model.bin").writeBytes(byteArrayOf(0))
        }

        return root
    }

    @Test
    fun v8Database_sweepsOrphanRowsAndReportsCounts() {
        databaseV8()
        insertOrphanRows()
        insertReferencedRows(
            liveVoiceModel = "live-voice-model",
            liveLanguage = "en-nl",
            liveModelPath = "/nonexistent",
        )
        val reporter = RecordingCrashReporter()

        val container = container(reporter)

        assertEquals(9L, storedVersion())
        assertTrue(
            container.voices
                .getAll()
                .executeAsList()
                .none { it.modelId == "ghost-voice-model" },
        )
        assertTrue(
            container.languageModels
                .getAll()
                .executeAsList()
                .none { it.languageId == "ghost-language" },
        )
        assertNotNull(container.voiceModels.getById("live-voice-model").executeAsOneOrNull())
        assertEquals(
            1,
            container.voices
                .getAll()
                .executeAsList()
                .count { it.modelId == "live-voice-model" },
        )
        assertTrue(
            container.languageModels
                .getAllByLanguageId("en-nl")
                .executeAsList()
                .isNotEmpty(),
        )

        val event = reporter.onlyEvent()
        assertEquals(1L, event["voice_orphans_before"])
        assertEquals(1L, event["language_model_orphans_before"])
        assertEquals(0L, event["voice_orphans_after"])
        assertEquals(0L, event["language_model_orphans_after"])
        assertEquals(0L, event["language_model_dirs_removed"])
    }

    @Test
    fun v8Database_prunesOnlyUnreferencedLanguageBundles() {
        databaseV8()
        val liveModel = writeBundle("fixture-live-bundle", listOf("en-nl")).resolve("en-nl")
        val orphanBundle = writeBundle("fixture-orphan-bundle", listOf("en-de"))
        val partialBundle = writeBundle("fixture-partial-bundle", listOf("en-fr", "en-es"))
        val livePartialModel = partialBundle.resolve("en-fr")
        val unknownDirectory = context.filesDir.resolve("${FIXTURE_PREFIX}unknown").also { it.mkdirs() }
        val metadataLessDirectory =
            context.filesDir.resolve("${FIXTURE_PREFIX}no-metadata").also {
                it.mkdirs()
                it.resolve("leftover.bin").writeBytes(byteArrayOf(0))
            }
        val danglingModelPath = orphanBundle.resolve("en-de").absolutePath
        insertReferencedRows(
            liveVoiceModel = "live-voice-model",
            liveLanguage = "en-nl",
            liveModelPath = liveModel.absolutePath,
        )
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL(
                "INSERT INTO Language (id, source, target, bidirectional) VALUES (?, ?, ?, ?)",
                arrayOf<Any?>("en-fr", "en", "fr", 0),
            )
            database.execSQL(
                "INSERT INTO LanguageModel (languageId, baseModel, path, version) VALUES (?, ?, ?, ?)",
                arrayOf("en-fr", "leanmt", livePartialModel.absolutePath, "v2.1.0"),
            )
        }
        val reporter = RecordingCrashReporter()

        container(reporter)

        assertTrue(liveModel.exists())
        assertTrue(livePartialModel.exists())
        assertFalse(orphanBundle.exists())
        assertFalse(partialBundle.resolve("en-es").exists())
        assertTrue("unrelated directories are never classified as bundles", unknownDirectory.exists())
        assertTrue(metadataLessDirectory.exists())
        assertFalse(File(danglingModelPath).exists())

        val event = reporter.onlyEvent()
        assertEquals(0L, event["voice_orphans_before"])
        assertEquals(0L, event["language_model_orphans_before"])
        assertEquals(2L, event["language_model_dirs_removed"])
    }

    @Test
    fun freshInstall_skipsSweep() {
        val reporter = RecordingCrashReporter()

        val container = container(reporter)
        // Force the driver to open and create the fresh database.
        container.languages.getAll().executeAsList()

        assertEquals(Database.Schema.version, storedVersion())
        assertTrue(reporter.events.isEmpty())
    }

    @Test
    fun v9Database_sweepRunsExactlyOnce() {
        databaseV8()
        insertOrphanRows()
        val first = RecordingCrashReporter()
        container(first)
        containers.forEach { it.driver.close() }
        containers.clear()

        // An orphan appearing after the sweep (only possible while foreign keys
        // stay unenforced) must not be swept again: the boundary was crossed.
        insertOrphanRows()
        val second = RecordingCrashReporter()
        val container = container(second)

        assertTrue(second.events.isEmpty())
        assertEquals(
            1,
            container.voices
                .getAll()
                .executeAsList()
                .count { it.modelId == "ghost-voice-model" },
        )
    }

    private companion object {
        const val FIXTURE_PREFIX = "orphan-sweep-test-"
    }
}
