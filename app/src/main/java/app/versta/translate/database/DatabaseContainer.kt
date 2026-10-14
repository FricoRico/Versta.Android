package app.versta.translate.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.versta.translate.database.migrations.Migration3
import app.versta.translate.database.migrations.Migration4
import app.versta.translate.database.migrations.Migration6
import app.versta.translate.database.migrations.Migration8
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.app.versta.translate.database.sqldelight.OcrModuleModel
import java.app.versta.translate.database.sqldelight.SpeechRecognitionModel
import java.app.versta.translate.database.sqldelight.VoiceModel

interface Migration {
    /** Schema version this migration's hook applies after (see `AfterVersion`). */
    val afterVersion: Int
    fun migrate(database: DatabaseContainer)
}

class DatabaseContainer(
    context: Context
) {
    companion object {
        private val TAG = DatabaseContainer::class.java.simpleName

        const val DB_FILE = "versta.db"
    }

    private val _migrations = listOf(
        Migration3,
        Migration4,
        Migration6,
        Migration8
    )

    // The AfterVersion hooks must ride the driver's own open path: the default
    // callback migrates an old database during the first open, before any
    // init-time PRAGMA check could observe the pre-migration version — so a
    // post-hoc runner never fires the data repairs (old OCR tables and files
    // would linger forever).
    private inner class MigrationCallback : AndroidSqliteDriver.Callback(
        Database.Schema,
        *_migrations.map { migration ->
            AfterVersion(migration.afterVersion.toLong()) { migration.migrate(this@DatabaseContainer) }
        }.toTypedArray()
    ) {
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            Timber.tag(TAG).i("Migrating database from $oldVersion to $newVersion")
            super.onUpgrade(db, oldVersion, newVersion)
        }
    }

    val driver = AndroidSqliteDriver(
        Database.Schema,
        context,
        DB_FILE,
        callback = MigrationCallback()
    )

    private val _database = Database(
        driver = driver,
        VoiceModelAdapter = VoiceModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
        OcrModuleModelAdapter = OcrModuleModel.Adapter(
            languagesAdapter = ListOfStringsAdapter,
        ),
        SpeechRecognitionModelAdapter = SpeechRecognitionModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
    )

    val data = _database.dataQueries
    val languages = _database.languageQueries
    val languageModels = _database.languageModelQueries
    val voices = _database.voiceQueries
    val voiceModels = _database.voiceModelQueries
    val ocrModules = _database.ocrModuleModelQueries
    val speechRecognitionModels = _database.speechRecognitionModelQueries

    fun transaction(body: TransactionWithoutReturn.() -> Unit) = _database.transaction { body() }
    fun <T> transactionForResult(body: TransactionWithReturn<T>.() -> T) =
        _database.transactionWithResult { body() }

    init {
        // Open eagerly: migration hooks delete and repair model files on disk,
        // which must happen deterministically at startup, not at first query.
        driver.executeQuery(
            null,
            "PRAGMA user_version",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L) },
            0
        ).value
    }
}

val ListOfStringsAdapter = object : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) =
        if (databaseValue.isEmpty()) {
            listOf()
        } else {
            Json.decodeFromString<List<String>>(databaseValue)
        }

    override fun encode(value: List<String>) = Json.encodeToString(value)
}
