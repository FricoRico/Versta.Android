package app.versta.translate.database

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.versta.translate.database.migrations.Migration3
import app.versta.translate.database.migrations.Migration4
import app.versta.translate.database.migrations.Migration6
import app.versta.translate.database.migrations.Migration7
import app.versta.translate.database.migrations.Migration8
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.app.versta.translate.database.sqldelight.OcrModuleModel
import java.app.versta.translate.database.sqldelight.SpeechRecognitionModel
import java.app.versta.translate.database.sqldelight.VoiceModel

/**
 * A manual post-schema data migration. Runs at most once per database, only
 * when the database being upgraded has not crossed [afterVersion] yet. The
 * `.sqm` schema migrations themselves are applied by the driver on first open;
 * these classes hold the data transforms that accompany them.
 */
interface Migration {
    /**
     * Schema version boundary this migration belongs to, mirroring the
     * `AfterVersion(N)` the data transform was attached to. Runs only when the
     * on-disk database version is at or below this value.
     */
    val afterVersion: Long

    /**
     * Runs the data migration. Exceptions propagate: a failed migration crashes
     * the app instead of leaving silently half-migrated data behind.
     *
     * @param database database container holding the driver and queries.
     * @param context application context for file access.
     */
    fun migrate(
        database: DatabaseContainer,
        context: Context,
    )
}

class DatabaseContainer(
    context: Context,
) {
    companion object {
        private val TAG = DatabaseContainer::class.java.simpleName

        const val DB_FILE = "versta.db"
    }

    private val appContext: Context = context.applicationContext

    private val driverCallback =
        object : AndroidSqliteDriver.Callback(Database.Schema) {
            /**
             * Runs after the `.sqm` migration chain, before any query is
             * served: repair the pre-release `Language.bidirectional` gap
             * (NEU-18). Idempotent; one PRAGMA on healthy databases.
             */
            override fun onOpen(db: SupportSQLiteDatabase) {
                LanguageBidirectionalRepair.runIfNeeded(db, appContext)
            }
        }

    val driver = AndroidSqliteDriver(Database.Schema, context, DB_FILE, callback = driverCallback)

    private val _database =
        Database(
            driver = driver,
            VoiceModelAdapter =
                VoiceModel.Adapter(
                    architecturesAdapter = ListOfStringsAdapter,
                ),
            OcrModuleModelAdapter =
                OcrModuleModel.Adapter(
                    languagesAdapter = ListOfStringsAdapter,
                ),
            SpeechRecognitionModelAdapter =
                SpeechRecognitionModel.Adapter(
                    architecturesAdapter = ListOfStringsAdapter,
                ),
        )

    private val _migrations =
        listOf(
            Migration3,
            Migration4,
            Migration6,
            Migration7,
            Migration8,
        )

    val data = _database.dataQueries
    val languages = _database.languageQueries
    val languageModels = _database.languageModelQueries
    val voices = _database.voiceQueries
    val voiceModels = _database.voiceModelQueries
    val ocrModules = _database.ocrModuleModelQueries
    val speechRecognitionModels = _database.speechRecognitionModelQueries

    fun transaction(body: TransactionWithoutReturn.() -> Unit) = _database.transaction { body() }

    fun <T> transactionForResult(body: TransactionWithReturn<T>.() -> T) = _database.transactionWithResult { body() }

    /**
     * Runs each pending manual migration exactly once. A migration applies when
     * the on-disk database version is at or below its [Migration.afterVersion]
     * boundary; databases already past it skip straight through, so nothing
     * re-runs on cold starts. Failures crash the app: continuing past a failed
     * migration previously hid a data-loss bug.
     */
    private fun runMigrations() {
        val version = storedDatabaseVersion()

        _migrations.forEach { migration ->
            if (version > migration.afterVersion) {
                return@forEach
            }

            Timber.tag(TAG).i("Running ${migration::class.java.simpleName} on database version $version")

            try {
                migration.migrate(this, appContext)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Database migration failed: ${migration::class.java.simpleName}")
                throw e
            }
        }
    }

    /**
     * Reads the on-disk schema version before the driver opens the database —
     * the first driver access applies the `.sqm` migrations and bumps
     * `user_version`, after which the stored version no longer says which data
     * migrations still need to run. A fresh install is created straight at the
     * current version, so no data migrations apply.
     */
    private fun storedDatabaseVersion(): Long {
        val file = appContext.getDatabasePath(DB_FILE)
        if (!file.exists()) {
            return Database.Schema.version
        }

        return SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            DatabaseUtils.longForQuery(database, "PRAGMA user_version", null)
        }
    }

    init {
        runMigrations()
    }
}

val ListOfStringsAdapter =
    object : ColumnAdapter<List<String>, String> {
        override fun decode(databaseValue: String) =
            if (databaseValue.isEmpty()) {
                listOf()
            } else {
                Json.decodeFromString<List<String>>(databaseValue)
            }

        override fun encode(value: List<String>) = Json.encodeToString(value)
    }
