package app.versta.translate.database.migrations

import app.cash.sqldelight.db.QueryResult
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import timber.log.Timber
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Replaces the v1 OCR detector/recognizer tables with the module-oriented
 * layout (one row per bundle module directory). The old bundle's extracted
 * files are deleted; the v1 pack format is not forward-compatible.
 */
object Migration8 : Migration {
    private val TAG = Migration8::class.java.simpleName

    override val afterVersion = 8

    override fun migrate(database: DatabaseContainer) {
        // The old tables still exist at this point; read their model
        // directories with raw SQL (the generated queries are gone),
        // remove the extracted files, then drop the tables.
        val paths = readOldModulePaths(database)
        paths.forEach(::removeOldModuleFiles)

        database.driver.execute(null, "DROP TABLE IF EXISTS ObjectCharacterRecognitionDetectorModel", 0)
        database.driver.execute(null, "DROP TABLE IF EXISTS ObjectCharacterRecognitionRecognizerModel", 0)
    }

    private fun readOldModulePaths(database: DatabaseContainer): List<Path> {
        val paths = mutableListOf<Path>()
        for (table in listOf("ObjectCharacterRecognitionDetectorModel", "ObjectCharacterRecognitionRecognizerModel")) {
            try {
                database.driver.executeQuery(
                    null,
                    "SELECT path FROM $table",
                    { cursor ->
                        val result = mutableListOf<String>()
                        while (cursor.next().value) {
                            result.add(cursor.getString(0) ?: continue)
                        }
                        QueryResult.Value(result)
                    },
                    0
                ).value.forEach { paths.add(it.toPath().toNioPath()) }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not read old OCR table $table (already gone?)")
            }
        }
        return paths
    }

    private fun removeOldModuleFiles(path: Path) {
        if (!path.exists()) {
            return
        }

        path.toFile().deleteRecursively()
    }
}
