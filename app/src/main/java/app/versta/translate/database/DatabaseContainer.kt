package app.versta.translate.database

import android.content.Context
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.versta.translate.database.migrations.Migration3
import app.versta.translate.database.migrations.Migration4
import app.versta.translate.database.migrations.Migration6
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.app.versta.translate.database.sqldelight.VoiceModel
import java.app.versta.translate.database.sqldelight.ObjectCharacterRecognitionDetectorModel
import java.app.versta.translate.database.sqldelight.ObjectCharacterRecognitionRecognizerModel

interface Migration {
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
        ObjectCharacterRecognitionDetectorModelAdapter = ObjectCharacterRecognitionDetectorModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
        ObjectCharacterRecognitionRecognizerModelAdapter = ObjectCharacterRecognitionRecognizerModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
    )

    private val _migrations = listOf(
        Migration3,
        Migration4,
        Migration6
    )

    val data = _database.dataQueries
    val languages = _database.languageQueries
    val languageModels = _database.languageModelQueries
    val voices = _database.voiceQueries
    val voiceModels = _database.voiceModelQueries
    val objectCharacterRecognitionModels = _database.objectCharacterRecognitionModelQueries

    fun transaction(body: TransactionWithoutReturn.() -> Unit) = _database.transaction { body() }
    fun <T> transactionForResult(body: TransactionWithReturn<T>.() -> T) =
        _database.transactionWithResult { body() }

    private fun runMigrations() {
        _migrations.forEach { migration ->
            try {
                migration.migrate(this)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Migration failed: ${migration::class.java.simpleName}")
            }
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
