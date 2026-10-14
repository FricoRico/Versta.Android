package app.versta.translate.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.versta.translate.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Idempotent repair for the `Language.bidirectional` schema gap (NEU-18).
 *
 * Archaeology: every tagged release (earliest v1.0.1) already creates the
 * table with the column, and no `.sqm` ever alters `Language`, so shipped
 * devices are unaffected whether they installed fresh or upgraded. The
 * exposed cohort is untagged versionCode 1 builds from before the column was
 * added, where live queries (`getBySourceIncludingBidirecional`) crash on
 * "no such column: bidirectional".
 *
 * SQLite has no `ADD COLUMN IF NOT EXISTS`, so the check lives in code and
 * runs from the driver's open hook — after the `.sqm` chain has run, before
 * any query is served. On healthy databases the check is one cheap PRAGMA
 * and nothing else is touched.
 *
 * The bundled translation catalog declares per-pair directionality, so a
 * repaired table is backfilled from it immediately; without the backfill the
 * rows would stay `0` forever because catalog refresh only `INSERT OR
 * IGNORE`s / `removeNotIn`s and never updates existing rows.
 */
object LanguageBidirectionalRepair {
    private val TAG = LanguageBidirectionalRepair::class.java.simpleName

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    /**
     * Minimal view onto the bundled translation catalog; unknown fields are
     * ignored and missing keys fall back to defaults, so catalog evolution
     * cannot break the repair.
     */
    @Serializable
    private data class CatalogEntry(
        @SerialName("source_language") val sourceLanguage: String = "",
        @SerialName("target_language") val targetLanguage: String = "",
        val bidirectional: Boolean = false,
    )

    /**
     * Adds the missing `bidirectional` column and backfills it from the
     * bundled catalog. No-op when the column already exists.
     *
     * Must be called after the schema migrations have run (the driver open
     * hook guarantees this). Throws on failure: leaving the table halved is
     * worse than crashing (NEU-10's fail-fast contract).
     */
    fun runIfNeeded(
        db: SupportSQLiteDatabase,
        context: Context,
    ) {
        if (hasBidirectionalColumn(db)) {
            return
        }

        Timber.tag(TAG).w("Language.bidirectional missing: applying schema repair")

        db.beginTransaction()
        try {
            // Mirrors the fresh-install definition (`Language.sq` compiles to
            // `bidirectional INTEGER DEFAULT 0`, nullable) so a repaired table
            // is shape-identical to a fresh one — the NEU-57 harness asserts
            // upgraded==fresh schema parity.
            db.execSQL("ALTER TABLE Language ADD COLUMN bidirectional INTEGER DEFAULT 0")

            val backfilled = backfillFromCatalog(db, context)
            Timber.tag(TAG).i("Language.bidirectional repaired; backfilled $backfilled rows")

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun hasBidirectionalColumn(db: SupportSQLiteDatabase): Boolean =
        db.query("PRAGMA table_info(Language)").use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }
                .any { it == "bidirectional" }
        }

    private fun backfillFromCatalog(
        db: SupportSQLiteDatabase,
        context: Context,
    ): Long {
        val entries =
            context.resources.openRawResource(R.raw.versta_translation_models).use { stream ->
                json.decodeFromString<List<List<CatalogEntry>>>(stream.reader().readText())
            }

        var updated = 0L
        entries
            .flatten()
            .filter { it.bidirectional }
            .forEach { entry ->
                db.execSQL(
                    "UPDATE Language SET bidirectional = 1 WHERE source = ? AND target = ?",
                    arrayOf(entry.sourceLanguage, entry.targetLanguage),
                )
                updated += changedRows(db)
            }
        return updated
    }

    private fun changedRows(db: SupportSQLiteDatabase): Long =
        db.query("SELECT changes()").use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getLong(0)
            } else {
                0L
            }
        }
}
