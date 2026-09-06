package app.versta.translate.database

import android.content.Context
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.versta.translate.database.migrations.Migration3
import app.versta.translate.database.migrations.Migration4
import app.versta.translate.database.migrations.Migration6
import app.versta.translate.database.migrations.Migration8
import kotlinx.serialization.json.Json
import timber.log.Timber
import app.cash.sqldelight.db.AfterVersion
import java.app.versta.translate.database.sqldelight.VoiceModel
import java.app.versta.translate.database.sqldelight.OcrModuleModel
import java.app.versta.translate.database.sqldelight.SpeechRecognitionModel

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

    val driver = AndroidSqliteDriver(Database.Schema, context, DB_FILE)

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

    private val _migrations = listOf(
        Migration3,
        Migration4,
        Migration6,
        Migration8
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

    private fun runMigrations() {
        // Single migration run from the on-disk version to latest; re-running
        // per-object migrations unconditionally used to re-execute every .sqm
        // (logged CREATE TABLE collisions and resurrected dropped tables).
        val currentVersion: Long = driver.executeQuery(
            null,
            "PRAGMA user_version",
            { cursor -> app.cash.sqldelight.db.QueryResult.Value(
                if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
            },
            0
        ).value

        if (currentVersion >= Database.Schema.version) {
            return
        }

        Timber.tag(TAG).i("Migrating database from $currentVersion to ${Database.Schema.version}")

        try {
            val hooks = _migrations.map { m ->
                AfterVersion(m.afterVersion.toLong()) { m.migrate(this@DatabaseContainer) }
            }
            Database.Schema.migrate(
                driver = driver,
                oldVersion = currentVersion,
                newVersion = Database.Schema.version,
                *hooks.toTypedArray()
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Database migration failed")
        }
    }

    init {
        runMigrations()
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
