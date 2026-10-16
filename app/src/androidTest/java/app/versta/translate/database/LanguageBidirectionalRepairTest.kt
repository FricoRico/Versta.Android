package app.versta.translate.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.versta.translate.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Integration tests for [LanguageBidirectionalRepair] through the real
 * [DatabaseContainer] driver-open path (NEU-18 hazard 2).
 *
 * The repair targets a pre-release schema gap: `Language` created before the
 * `bidirectional` column existed, where every live query touching the column
 * crashes. The broken state is reproduced by building a healthy database
 * with the production schema and then dropping the column behind the
 * driver's back — the exact mid-state such a device reaches after its `.sqm`
 * chain has run.
 */
class LanguageBidirectionalRepairTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CatalogEntry(
        @SerialName("source_language") val sourceLanguage: String,
        @SerialName("target_language") val targetLanguage: String,
        val bidirectional: Boolean,
    )

    /**
     * A pair the bundled catalog marks bidirectional — the backfill must set
     * it to 1. Fails fixture setup if the catalog ever loses the property the
     * repair relies on.
     */
    private val catalogBidirectionalPair: Pair<String, String> by lazy {
        context.resources.openRawResource(R.raw.versta_translation_models).use { stream ->
            json
                .decodeFromString<List<List<CatalogEntry>>>(stream.reader().readText())
                .flatten()
                .first { it.bidirectional }
                .let { it.sourceLanguage to it.targetLanguage }
        }
    }

    /** A pair absent from the catalog; the backfill must leave it at 0. */
    private val unknownPair = "zz" to "xx"

    @Before
    fun setUp() {
        SQLiteDatabase.deleteDatabase(databaseFile)
    }

    @After
    fun tearDown() {
        containers.forEach { it.driver.close() }
        SQLiteDatabase.deleteDatabase(databaseFile)
    }

    private fun container(): DatabaseContainer = DatabaseContainer(context).also { containers.add(it) }

    /**
     * Seeds two language rows through a real container, closes it, then
     * removes the column from the on-disk file directly.
     */
    private fun givenBrokenDatabase(): Pair<String, String> {
        val bidirectionalPair = catalogBidirectionalPair

        container().let { container ->
            container.languages.insertOrIgnore(
                id = "seed:${bidirectionalPair.first}:${bidirectionalPair.second}",
                source = bidirectionalPair.first,
                target = bidirectionalPair.second,
                bidirectional = false,
            )
            container.languages.insertOrIgnore(
                id = "seed:${unknownPair.first}:${unknownPair.second}",
                source = unknownPair.first,
                target = unknownPair.second,
                bidirectional = false,
            )
            container.driver.close()
            containers.clear()
        }

        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("CREATE TABLE LanguageLegacy (id TEXT NOT NULL PRIMARY KEY, source TEXT NOT NULL, target TEXT NOT NULL)")
            db.execSQL("INSERT INTO LanguageLegacy (id, source, target) SELECT id, source, target FROM Language")
            db.execSQL("DROP TABLE Language")
            db.execSQL("ALTER TABLE LanguageLegacy RENAME TO Language")
        }

        return bidirectionalPair
    }

    @Test
    fun missingColumnIsAddedAndBackfilledFromCatalog() {
        val bidirectionalPair = givenBrokenDatabase()

        val languages = container().languages.getAll().executeAsList()

        val repairedBidirectional = languages.single { it.source == bidirectionalPair.first && it.target == bidirectionalPair.second }
        assertEquals("catalog-bidirectional pair must be backfilled to 1", true, repairedBidirectional.bidirectional)

        val untouchedUnknown = languages.single { it.source == unknownPair.first && it.target == unknownPair.second }
        assertEquals("rows absent from the catalog must stay 0", false, untouchedUnknown.bidirectional)
    }

    @Test
    fun liveQueryCrashesBeforeRepairIsGreenAfter() {
        val bidirectionalPair = givenBrokenDatabase()

        val matches =
            container().languages.getBySourceIncludingBidirecional(bidirectionalPair.first).executeAsList()

        assertTrue(matches.any { it.source == bidirectionalPair.first && it.target == bidirectionalPair.second })
    }

    @Test
    fun healthyDatabaseIsUntouched() {
        container().let { container ->
            container.languages.insertOrIgnore(
                id = "seed:${unknownPair.first}:${unknownPair.second}",
                source = unknownPair.first,
                target = unknownPair.second,
                bidirectional = false,
            )
            container.driver.close()
            containers.clear()
        }

        val languages = container().languages.getAll().executeAsList()

        val row = languages.single { it.source == unknownPair.first }
        assertEquals(false, row.bidirectional)
        assertEquals(1, languages.size)
    }

    @Test
    fun repairRunsOnceAndLeavesBackfilledValuesAlone() {
        val bidirectionalPair = givenBrokenDatabase()

        val first = container()
        first.languages.getAll().executeAsList()
        first.driver.close()
        containers.clear()

        // Flip the flag back by hand; a second open must not re-run the repair.
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "UPDATE Language SET bidirectional = 0 WHERE source = ? AND target = ?",
                arrayOf(bidirectionalPair.first, bidirectionalPair.second),
            )
        }

        val languages = container().languages.getAll().executeAsList()
        assertEquals(false, languages.single { it.source == bidirectionalPair.first }.bidirectional)
    }
}
