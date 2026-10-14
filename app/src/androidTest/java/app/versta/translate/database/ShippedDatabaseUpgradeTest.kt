package app.versta.translate.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.QueryResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Migration verification harness (audit NEU-4 §4 gap, project NEU-56
 * workstream 3): for every database version ever shipped in a public release,
 * write the pristine release-era database, open the app against it, and verify
 * the upgrade lands on the current schema with correct data handling.
 *
 * Per fixture this asserts:
 *  - convergence to [Database.Schema.version] (v8),
 *  - the final schema shape, including `Language.bidirectional`,
 *  - upgraded schema identical to a fresh install,
 *  - version-crossing data migrations applied exactly once, never re-run on
 *    cold starts,
 *  - foreign keys enforced after the upgrade.
 *
 * Two groups of assertions encode the NEU-18 fixes and fail until that work
 * lands: foreign-key enforcement (also drives the orphan cleanup of
 * Migration3's backfill for purged models) and `bidirectional` repair for
 * [ShippedDatabaseFixtures.DATABASE_V1_PRE_BIDIRECTIONAL].
 */
@RunWith(Parameterized::class)
class ShippedDatabaseUpgradeTest {
    @Parameterized.Parameter(0)
    @JvmField
    var fixture: ShippedDatabaseFixture = ShippedDatabaseFixtures.DATABASE_V1

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val containers = mutableListOf<DatabaseContainer>()

    private val databaseFile: File get() = context.getDatabasePath(DatabaseContainer.DB_FILE)
    private val voiceModelsRoot: File get() = context.filesDir.resolve("voice-models")
    private val externalData: File get() = context.filesDir.resolve("external-data")
    private val externalDataHash: File get() = context.filesDir.resolve("external-data.sha256")

    @Before
    fun setUp() = cleanState()

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

    private fun install() = fixture.write(context, databaseFile)

    private fun open(): DatabaseContainer = DatabaseContainer(context).also { containers.add(it) }

    private fun storedVersion(): Long =
        SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            DatabaseUtils.longForQuery(database, "PRAGMA user_version", null)
        }

    private data class ColumnShape(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKey: Boolean,
    )

    private data class ForeignKeyShape(
        val table: String,
        val from: String,
        val to: String,
        val onDelete: String,
    )

    private data class TableShape(
        val columns: List<ColumnShape>,
        val foreignKeys: List<ForeignKeyShape>,
    )

    private data class SchemaInventory(
        val tables: Map<String, TableShape>,
        val indexes: List<String>,
    )

    private fun readRaw(): SQLiteDatabase = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    /**
     * Full structural inventory. Column and foreign-key lists are sorted by
     * name: a repair migration that `ADD COLUMN`s produces a different column
     * order than a fresh `CREATE TABLE`, and order carries no meaning here.
     */
    private fun schemaInventory(): SchemaInventory =
        readRaw().use { database ->
            val tables =
                database
                    .rawQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'table'" +
                            " AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'",
                        null,
                    ).use { cursor ->
                        generateSequence {
                            if (cursor.moveToNext()) cursor.getString(0) else null
                        }.toList()
                    }

            val indexes =
                database
                    .rawQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'index'" +
                            " AND name NOT LIKE 'sqlite_%' ORDER BY name",
                        null,
                    ).use { cursor ->
                        generateSequence {
                            if (cursor.moveToNext()) cursor.getString(0) else null
                        }.toList()
                    }

            SchemaInventory(
                tables =
                    tables.associateWith { table ->
                        TableShape(
                            columns =
                                database.rawQuery("PRAGMA table_info(\"$table\")", null).use { cursor ->
                                    generateSequence {
                                        if (!cursor.moveToNext()) {
                                            null
                                        } else {
                                            ColumnShape(
                                                name = cursor.getString(1),
                                                type = cursor.getString(2),
                                                notNull = cursor.getInt(3) == 1,
                                                defaultValue = cursor.getString(4),
                                                primaryKey = cursor.getInt(5) > 0,
                                            )
                                        }
                                    }.toList().sortedBy { it.name }
                                },
                            foreignKeys =
                                database.rawQuery("PRAGMA foreign_key_list(\"$table\")", null).use { cursor ->
                                    generateSequence {
                                        if (!cursor.moveToNext()) {
                                            null
                                        } else {
                                            ForeignKeyShape(
                                                table = cursor.getString(2),
                                                from = cursor.getString(3),
                                                to = cursor.getString(4),
                                                onDelete = cursor.getString(6),
                                            )
                                        }
                                    }.toList().sortedBy { it.from }
                                },
                        )
                    },
                indexes = indexes,
            )
        }

    private fun tableDump(): Map<String, List<String>> =
        readRaw().use { database ->
            schemaInventory().tables.keys.associateWith { table ->
                database.rawQuery("SELECT * FROM \"$table\"", null).use { cursor ->
                    generateSequence {
                        if (!cursor.moveToNext()) {
                            null
                        } else {
                            (0 until cursor.columnCount).joinToString("|") { index ->
                                cursor.getString(index) ?: "<null>"
                            }
                        }
                    }.toList().sorted()
                }
            }
        }

    private fun foreignKeysEnabledOn(container: DatabaseContainer): Boolean {
        val result =
            container.driver.executeQuery(
                identifier = null,
                sql = "PRAGMA foreign_keys",
                mapper = { cursor ->
                    QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null)
                },
                parameters = 0,
                binders = null,
            )

        return result.value == 1L
    }

    private fun orphanVoiceRows(): Long =
        readRaw().use { database ->
            DatabaseUtils.longForQuery(
                database,
                "SELECT COUNT(*) FROM Voice WHERE modelId NOT IN (SELECT id FROM VoiceModel)",
                null,
            )
        }

    private fun voicesOf(modelId: String): Set<Pair<String, String>> =
        readRaw().use { database ->
            database
                .rawQuery(
                    "SELECT language, gender FROM Voice WHERE modelId = ?",
                    arrayOf(modelId),
                ).use { cursor ->
                    generateSequence {
                        if (!cursor.moveToNext()) null else cursor.getString(0) to cursor.getString(1)
                    }.toSet()
                }
        }

    @Test
    fun convergesToCurrentSchemaVersion() {
        install()
        open()

        assertEquals(Database.Schema.version, storedVersion())
        assertEquals("harness tracks schema v8; bump the harness when the schema moves", 8L, storedVersion())
    }

    @Test
    fun producesExpectedFinalSchemaShape() {
        install()
        open().driver.close()

        val inventory = schemaInventory()

        assertEquals(
            setOf(
                "Language",
                "LanguageModel",
                "Voice",
                "VoiceModel",
                "Data",
                "ObjectCharacterRecognitionDetectorModel",
                "ObjectCharacterRecognitionRecognizerModel",
                "SpeechRecognitionModel",
            ),
            inventory.tables.keys,
        )
        assertEquals(listOf("languagePair"), inventory.indexes)

        val language = inventory.tables.getValue("Language")
        assertEquals(
            setOf(
                ColumnShape("id", "TEXT", notNull = true, defaultValue = null, primaryKey = true),
                ColumnShape("source", "TEXT", notNull = true, defaultValue = null, primaryKey = false),
                ColumnShape("target", "TEXT", notNull = true, defaultValue = null, primaryKey = false),
                ColumnShape("bidirectional", "INTEGER", notNull = false, defaultValue = "0", primaryKey = false),
            ),
            language.columns.toSet(),
        )

        assertEquals(
            setOf(ForeignKeyShape("Language", "languageId", "id", "CASCADE")),
            inventory.tables
                .getValue("LanguageModel")
                .foreignKeys
                .toSet(),
        )
        assertEquals(
            setOf(ForeignKeyShape("VoiceModel", "modelId", "id", "CASCADE")),
            inventory.tables
                .getValue("Voice")
                .foreignKeys
                .toSet(),
        )

        // 6.sqm reshape: LanguageModel keeps `version`, loses `files` and
        // `architectures`, and legacy rows are destroyed.
        assertEquals(
            setOf(
                ColumnShape("languageId", "TEXT", notNull = true, defaultValue = null, primaryKey = true),
                ColumnShape("baseModel", "TEXT", notNull = true, defaultValue = null, primaryKey = false),
                ColumnShape("path", "TEXT", notNull = true, defaultValue = null, primaryKey = false),
                ColumnShape("version", "TEXT", notNull = true, defaultValue = null, primaryKey = false),
            ),
            inventory.tables
                .getValue("LanguageModel")
                .columns
                .toSet(),
        )

        readRaw().use { database ->
            assertEquals(
                0,
                DatabaseUtils.longForQuery(database, "SELECT COUNT(*) FROM LanguageModel", null),
            )

            // Repair contract: every Language row carries bidirectional,
            // backfilled to 0 (corrected from catalog metadata on next
            // refresh).
            assertEquals(
                0,
                DatabaseUtils.longForQuery(
                    database,
                    "SELECT COUNT(*) FROM Language WHERE bidirectional <> 0",
                    null,
                ),
            )
            assertEquals(2, DatabaseUtils.longForQuery(database, "SELECT COUNT(*) FROM Language", null))
        }
    }

    @Test
    fun upgradedSchemaMatchesFreshInstall() {
        install()
        open().driver.close()
        val upgraded = schemaInventory()

        cleanState()
        open().driver.close()
        val fresh = schemaInventory()

        assertEquals(fresh, upgraded)
    }

    @Test
    fun crossedBoundaryMigrationsApplyExactlyOnceAndThenNeverAgain() {
        install()
        val container = open()

        // Boundary 4 (Migration4), crossed from every shipped version:
        // sub-v1.2.0 voice models dropped with their files, survivors
        // re-marked StyleTTS2, legacy external-data bundle removed.
        fixture.voiceModelIdsPurged.forEach { id ->
            assertNull("purged voice model $id", container.voiceModels.getById(id).executeAsOneOrNull())
            assertFalse(voiceModelsRoot.resolve(id).exists())
        }
        fixture.voiceModelIdsSurviving.forEach { id ->
            val survivor = container.voiceModels.getById(id).executeAsOneOrNull()
            assertNotNull("surviving voice model $id", survivor)
            assertEquals(listOf("StyleTTS2"), survivor?.architectures)
        }
        assertFalse(externalData.exists())
        assertFalse(externalDataHash.exists())

        // Boundary 3 (Migration3): Voice backfill from bundled voice files
        // only for fixtures whose release crossed it.
        if (fixture.expectsVoiceBackfill) {
            fixture.voiceModelIdsSurviving.forEach { id ->
                assertEquals(setOf("en" to "female", "pt" to "male"), voicesOf(id))
            }
        } else {
            fixture.voiceModelIdsSurviving.forEach { id ->
                assertEquals(fixture.preSeededVoices, voicesOf(id))
            }
        }

        // Exactly-once: a sub-boundary model arriving after the upgrade must
        // not retrigger Migration3's backfill or Migration4's purge on the
        // next cold start.
        val marker = ShippedDatabaseFixtures.writeVoiceModelFiles(context, "marker-legacy", "v1.1.0")
        container.voiceModels.upsert(
            id = "marker-legacy",
            baseModel = "styletts2",
            architectures = listOf("Kokoro"),
            path = marker.absolutePath,
            version = "v1.1.0",
        )
        val before = tableDump()
        container.driver.close()

        val reopened = open()

        assertNotNull(reopened.voiceModels.getById("marker-legacy").executeAsOneOrNull())
        assertEquals(emptySet<Pair<String, String>>(), voicesOf("marker-legacy"))
        assertEquals(before, tableDump())
    }

    @Test
    fun foreignKeysEnforcedAfterUpgrade() {
        install()
        val container = open()

        assertTrue(
            "the connection serving production queries enforces foreign keys",
            foreignKeysEnabledOn(container),
        )

        // Enforcement is live on the app's connection: deleting the parent
        // cascades. (Foreign keys are per-connection in SQLite, so the delete
        // must go through the driver, not a raw handle.)
        val cascadeProbe = fixture.voiceModelIdsSurviving.firstOrNull()
        if (cascadeProbe != null) {
            assertTrue(
                "cascade probe $cascadeProbe carries Voice rows before the delete",
                voicesOf(cascadeProbe).isNotEmpty(),
            )
            container.voiceModels.deleteById(cascadeProbe)
            assertEquals(emptySet<Pair<String, String>>(), voicesOf(cascadeProbe))
        }

        container.driver.close()

        // No orphaned Voice rows after the upgrade — Migration3's backfill
        // rows for purged models are removed by the cascade.
        assertEquals(0, orphanVoiceRows())
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<ShippedDatabaseFixture> = ShippedDatabaseFixtures.ALL
    }
}
