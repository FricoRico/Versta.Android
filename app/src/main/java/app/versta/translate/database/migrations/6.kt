package app.versta.translate.database.migrations

import app.cash.sqldelight.db.AfterVersion
import app.versta.translate.database.Database
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import java.nio.file.Path
import kotlin.io.path.exists

object Migration6 : Migration {
    override fun migrate(database: DatabaseContainer) {
        Database.Schema.migrate(
            driver = database.driver,
            oldVersion = 5,
            newVersion = Database.Schema.version,
            AfterVersion(6) {
                database.languageModels.getAll().executeAsList().forEach { data ->
                    if (data.version < "v2.0.0") {
                        database.languageModels.deleteById(data.languageId)
                        removeOldLanguageModels(data.path.toPath().toNioPath().parent)

                        return@forEach
                    }
                }
            }
        )
    }

    private fun removeOldLanguageModels(path: Path) {
        if (!path.exists()) {
            return
        }

        path.toFile().deleteRecursively()
    }
}
