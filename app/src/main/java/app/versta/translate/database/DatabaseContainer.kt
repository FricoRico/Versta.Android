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
import app.versta.translate.database.migrations.Migration8
import app.versta.translate.utils.CrashReporter
import app.versta.translate.utils.TimberCrashReporter
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.app.versta.translate.database.sqldelight.ObjectCharacterRecognitionDetectorModel
import java.app.versta.translate.database.sqldelight.ObjectCharacterRecognitionRecognizerModel
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
    val crashReporter: CrashReporter = TimberCrashReporter,
) {
    companion object {
        private val TAG = DatabaseContainer::class.java.simpleName

        const val DB_FILE = "versta.db"
    }

    private val appContext: Context = context.applicationContext

    /**
     * Driver with foreign-key enforcement on. `setForeignKeyConstraintsEnabled`
     * belongs in `onConfigure`: the support library invokes it while
     * configuring every connection, before `onCreate`/`onUpgrade`/`onOpen`, so
     * the pragma holds for schema migrations, manual data migrations, and all
     * queries alike. With constraints enforced, `ON DELETE CASCADE` on `Voice`
     * and `LanguageModel` is live, and inserting a child row without a parent
     * fails instead of creating an orphan. The one-time sweep (Migration8)
     * clears historical orphans before this ships.
     */
    val driver =
        AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = DB_FILE,
            callback =
                object : AndroidSqliteDriver.Callback(Database.Schema) {
                    override fun onConfigure(db: SupportSQLiteDatabase) {
                        super.onConfigure(db)
                        db.setForeignKeyConstraintsEnabled(true)
                    }
                },
        )

    private val _database =
        Database(
            driver = driver,
            VoiceModelAdapter =
                VoiceModel.Adapter(
                    architecturesAdapter = ListOfStringsAdapter,
                ),
            ObjectCharacterRecognitionDetectorModelAdapter =
                ObjectCharacterRecognitionDetectorModel.Adapter(
                    architecturesAdapter = ListOfStringsAdapter,
                ),
            ObjectCharacterRecognitionRecognizerModelAdapter =
                ObjectCharacterRecognitionRecognizerModel.Adapter(
                    architecturesAdapter = ListOfStringsAdapter,
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
            // The orphan sweep stays last so it cleans up whatever the earlier
            // migrations of the same release leave behind.
            Migration8,
        )

    val data = _database.dataQueries
    val languages = _database.languageQueries
    val languageModels = _database.languageModelQueries
    val voices = _database.voiceQueries
    val voiceModels = _database.voiceModelQueries
    val objectCharacterRecognitionModels = _database.objectCharacterRecognitionModelQueries
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
     * migrations still need to run. A missing file means a fresh install, which
     * is created straight at the current version, so no data migrations apply.
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
