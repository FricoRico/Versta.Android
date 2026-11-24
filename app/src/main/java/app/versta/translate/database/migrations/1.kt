package app.versta.translate.database.migrations

import android.content.Context
import app.cash.sqldelight.db.QueryResult
import app.versta.translate.R
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Repairs the `Language.bidirectional` schema gap (NEU-18 hazard 2).
 *
 * The column was added to `Language.sq` without a `.sqm`, so databases created
 * by untagged pre-release builds (before `e4d5060`) are on schema version 1
 * without it, and every live query touching the column crashes with "no such
 * column: bidirectional". Every tagged release already creates the table with
 * the column, so on those databases this migration only runs the column check.
 *
 * SQLite has no `ADD COLUMN IF NOT EXISTS`, so the check lives here instead of
 * in a `.sqm`. A repaired table is backfilled from the bundled translation
 * catalog right away: the catalog refresh only `INSERT OR IGNORE`s and
 * `removeNotIn`s, so it would never correct the defaulted rows.
 */
object Migration1 : Migration {
    private val TAG = Migration1::class.java.simpleName

    override val afterVersion = 1L

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Minimal view onto the bundled translation catalog; unknown fields are
     * ignored and missing keys fall back to defaults, so catalog evolution
     * cannot break the migration.
     */
    @Serializable
    private data class CatalogEntry(
        @SerialName("source_language") val sourceLanguage: String = "",
        @SerialName("target_language") val targetLanguage: String = "",
        val bidirectional: Boolean = false,
    )

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        if (hasBidirectionalColumn(database)) {
            return
        }

        Timber.tag(TAG).w("Language.bidirectional missing: adding the column")

        database.transaction {
            // Same definition `Language.sq` compiles to for a fresh install, so
            // a repaired table is shape-identical to a fresh one.
            database.driver.execute(null, "ALTER TABLE Language ADD COLUMN bidirectional INTEGER DEFAULT 0", 0)

            val backfilled = backfillFromCatalog(database, context)
            Timber.tag(TAG).i("Language.bidirectional added; backfilled $backfilled rows")
        }
    }

    private fun hasBidirectionalColumn(database: DatabaseContainer): Boolean =
        database.driver
            .executeQuery(
                null,
                "PRAGMA table_info(Language)",
                { cursor ->
                    var found = false
                    while (!found && cursor.next().value) {
                        found = cursor.getString(1) == "bidirectional"
                    }
                    QueryResult.Value(found)
                },
                0,
            ).value

    private fun backfillFromCatalog(
        database: DatabaseContainer,
        context: Context,
    ): Long {
        val entries =
            context.resources.openRawResource(R.raw.versta_translation_models).use { stream ->
                json.decodeFromString<List<List<CatalogEntry>>>(stream.reader().readText())
            }

        return entries
            .flatten()
            .filter { it.bidirectional }
            .sumOf { entry ->
                database.driver
                    .execute(null, "UPDATE Language SET bidirectional = 1 WHERE source = ? AND target = ?", 2) {
                        bindString(0, entry.sourceLanguage)
                        bindString(1, entry.targetLanguage)
                    }.value
            }
    }
}
