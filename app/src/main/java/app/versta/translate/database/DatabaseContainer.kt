package app.versta.translate.database

import android.content.Context
import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.TransactionWithReturn
import app.cash.sqldelight.TransactionWithoutReturn
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.app.versta.translate.database.sqldelight.LanguageModel

class DatabaseContainer(
    context: Context
) {
    companion object {
        const val DB_FILE = "versta.db"
    }

    private val driver = AndroidSqliteDriver(Database.Schema, context, DB_FILE)
    private val database = Database(
        driver = driver,
        LanguageModelAdapter = LanguageModel.Adapter(
            architecturesAdapter = ListOfStringsAdapter,
        ),
    )

    val languages = database.languageQueries
    val languageModels = database.languageModelQueries

    fun transaction(body: TransactionWithoutReturn.() -> Unit) = database.transaction { body() }
    fun <T> transactionForResult(body: TransactionWithReturn<T>.() -> T) =
        database.transactionWithResult { body() }
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
