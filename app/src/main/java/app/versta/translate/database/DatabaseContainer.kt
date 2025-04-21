package app.versta.translate.database

import android.content.Context
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.versta.translate.database.migrations.Migration3
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.app.versta.translate.database.sqldelight.LanguageModel
import java.app.versta.translate.database.sqldelight.VoiceModel

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
        LanguageModelAdapter = LanguageModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
        VoiceModelAdapter = VoiceModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
    )

    private val _migrations = listOf(
        Migration3,
    )

    val languages = _database.languageQueries
    val languageModels = _database.languageModelQueries
    val voices = _database.voiceQueries
    val voiceModels = _database.voiceModelQueries

    fun transaction(body: TransactionWithoutReturn.() -> Unit) = _database.transaction { body() }
    fun <T> transactionForResult(body: TransactionWithReturn<T>.() -> T) =
        _database.transactionWithResult { body() }

    private fun runMigrations() {
        _migrations.forEach { migration ->
            migration.migrate(this)
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
