package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import app.versta.translate.utils.compareVersions
import okio.Path.Companion.toPath
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists

/**
 * Drops voice models published before `v1.2.0` (Kokoro era) together with their
 * files, marks the surviving models as StyleTTS2 and removes the legacy
 * `external-data` bundle from internal storage.
 */
object Migration4 : Migration {
    override val afterVersion = 4L

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        database.voiceModels.getAll().executeAsList().forEach { data ->
            if (compareVersions(data.version, "v1.2.0") < 0) {
                database.voiceModels.deleteById(data.id)
                removeOldVoiceModels(
                    data.path
                        .toPath()
                        .toNioPath()
                        .parent,
                )

                return@forEach
            }

            database.voiceModels.upsert(
                id = data.id,
                path = data.path,
                version = data.version,
                baseModel = data.baseModel,
                architectures = listOf(VoiceModelArchitecture.StyleTTS2.toString()),
            )
        }

        removeOldExternalData(context)
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
