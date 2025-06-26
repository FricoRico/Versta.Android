package app.versta.translate.database.migrations

import android.content.Context
import app.cash.sqldelight.db.AfterVersion
import app.versta.translate.MainApplication
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.database.Database
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

object Migration4 : Migration {
    override fun migrate(database: DatabaseContainer) {
        Database.Schema.migrate(
            driver = database.driver,
            oldVersion = 3,
            newVersion = Database.Schema.version,
            AfterVersion(4) {
                database.voiceModels.getAll().executeAsList().forEach { data ->
                    if (data.version < "v1.2.0") {
                        database.voiceModels.deleteById(data.id)
                        removeOldVoiceModels(data.path.toPath().toNioPath().parent)

                        return@forEach
                    }

                    database.voiceModels.upsert(
                        id = data.id,
                        path = data.path,
                        version = data.version,
                        baseModel = data.baseModel,
                        architectures = listOf(VoiceModelArchitecture.StyleTTS2.toString())
                    )
                }

                removeOldExternalData(MainApplication.context)
            }
        )
    }

    private fun removeOldVoiceModels(path: Path) {
        if (!path.exists()) {
            return
        }

        path.toFile().deleteRecursively()
    }

    private fun removeOldExternalData(context: Context) {
        val externalDataPath = context.filesDir.resolve("external-data")
        val externalDataHashPath = context.filesDir.resolve("external-data.sha256")

        if (externalDataPath.exists()) {
            externalDataPath.deleteRecursively()
        }

        if (externalDataHashPath.exists()) {
            externalDataHashPath.deleteRecursively()
        }
    }
}
