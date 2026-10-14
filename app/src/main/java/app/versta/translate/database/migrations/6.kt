package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import app.versta.translate.utils.compareVersions
import okio.Path.Companion.toPath
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Drops language models published before `v2.0.0` together with their files.
 */
object Migration6 : Migration {
    override val afterVersion = 6L

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        database.languageModels.getAll().executeAsList().forEach { data ->
            if (compareVersions(data.version, "v2.0.0") < 0) {
                database.languageModels.deleteById(data.languageId)
                removeOldLanguageModels(
                    data.path
                        .toPath()
                        .toNioPath()
                        .parent,
                )

                return@forEach
            }
        }
    }

    private fun removeOldLanguageModels(path: Path) {
        if (!path.exists()) {
            return
        }

        path.toFile().deleteRecursively()
    }
}
