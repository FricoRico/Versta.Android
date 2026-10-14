package app.versta.translate.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Regression coverage for the v6 boundary (NEU-67, NEU-18 hazard 3:
 * accept + delete).
 *
 * `Migration6` read `LanguageModel` rows to delete pre-`v2.0.0` language
 * model files, but it was unreachable by construction: on any upgrade
 * crossing v6 the driver applies `6.sqm` (DROP + empty recreate of
 * `LanguageModel`) before data-migration code can query it, so the row scan
 * always came back empty; databases already past v6 skipped it via the
 * `afterVersion` gate. Deleting it must change nothing.
 *
 * This test pins the observable contract on every shipped upgrade path that
 * crosses the boundary (v1, v2, v4 — all cross v5/v6 on the way to v8;
 * "v5 → v8" in the issue is the same crossing, since DB v5 never shipped):
 * the upgrade still converges to schema v8, `LanguageModel` still ends empty
 * (destroyed by `6.sqm`, not by data code), and legacy language-model files
 * stay on disk, inert and orphaned. Removal of the orphans is owned by the
 * FK-work orphan sweep (NEU-65), not by this boundary.
 */
@RunWith(Parameterized::class)
class LegacyLanguageModelFilesSurviveUpgradeTest {
    @Parameterized.Parameter(0)
    @JvmField
    var fixture: ShippedDatabaseFixture = ShippedDatabaseFixtures.DATABASE_V1

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)
    private val languageModelsRoot: File get() = context.filesDir.resolve("language-models")
    private val legacyModelRoot: File get() = languageModelsRoot.resolve("legacy-lm")

    @Before
    fun setUp() = cleanState()

    @After
    fun tearDown() {
        containers.forEach { it.driver.close() }
        cleanState()
    }

    private fun cleanState() {
        SQLiteDatabase.deleteDatabase(databaseFile)
        languageModelsRoot.deleteRecursively()
    }

    private fun open(): DatabaseContainer = DatabaseContainer(context).also { containers.add(it) }

    private fun languageModelRowCount(): Long =
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            DatabaseUtils.longForQuery(database, "SELECT COUNT(*) FROM LanguageModel", null)
        }

    private fun storedVersion(): Long =
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            DatabaseUtils.longForQuery(database, "PRAGMA user_version", null)
        }

    /**
     * Writes a pre-`v2.0.0` language model the way the era registered it:
     * a row whose `path` points at `<root>/model`, with `<root>` holding the
     * files `Migration6`'s `removeOldLanguageModels` would have deleted — it
     * removed the model directory's parent — had it ever been reachable.
     */
    private fun writeLegacyLanguageModel(): File {
        val model = legacyModelRoot.resolve("model")
        model.mkdirs()
        model.resolve("model.bin").writeBytes(byteArrayOf(0))
        model.resolve("vocab.txt").writeText("")

        return legacyModelRoot
    }

    private fun registerLegacyLanguageModel(path: File) {
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
            database.execSQL(
                "INSERT INTO LanguageModel (languageId, baseModel, architectures, path, version) VALUES (?, ?, ?, ?, ?)",
                arrayOf("nl-en", "bergamot-tiny", "[\"Bergamot\"]", path.resolve("model").absolutePath, "v1.1.0"),
            )
        }
    }

    @Test
    fun upgradeAcrossV6LeavesLegacyLanguageModelFilesUntouched() {
        fixture.write(context, databaseFile)
        val root = writeLegacyLanguageModel()
        registerLegacyLanguageModel(root)
        open()

        // The upgrade still lands on the current schema.
        assertEquals(Database.Schema.version, storedVersion())

        // `6.sqm` destroys the rows; the end state matches a database that
        // never carried Migration6.
        assertEquals(0, languageModelRowCount())

        // The file arm never fired: the model root (parent of the registered
        // `model` path) survives with its payload, orphaned until the sweep.
        assertTrue(root.resolve("model").resolve("model.bin").exists())
        assertTrue(root.resolve("model").resolve("vocab.txt").exists())

        // No late arm on cold starts either: reopening is a no-op for both
        // the table and the files.
        containers.forEach { it.driver.close() }
        containers.clear()
        open()

        assertEquals(0, languageModelRowCount())
        assertTrue(root.resolve("model").resolve("model.bin").exists())
        assertTrue(root.resolve("model").resolve("vocab.txt").exists())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<ShippedDatabaseFixture> =
            listOf(
                ShippedDatabaseFixtures.DATABASE_V1,
                ShippedDatabaseFixtures.DATABASE_V2,
                ShippedDatabaseFixtures.DATABASE_V4,
            )
    }
}
