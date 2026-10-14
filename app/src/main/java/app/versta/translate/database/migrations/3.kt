package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import java.app.versta.translate.database.sqldelight.Voice

/**
 * Backfills the `Voice` table (added by `3.sqm`) with one row per voice file
 * bundled in each installed voice model.
 */
object Migration3 : Migration {
    override val afterVersion = 3L

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        database.voiceModels.getAll().executeAsList().forEach { data ->
            val path = data.path.toPath().toNioPath()
            val voices = voicesFor(data.id, VoiceWithModelFiles.load(data.id, path))

            database.voices.transaction {
                voices.forEach {
                    database.voices.insertOrIgnore(
                        modelId = it.modelId,
                        language = it.language,
                        gender = it.gender,
                    )
                }
            }
        }
    }

    private fun voicesFor(
        modelId: String,
        model: VoiceWithModelFiles,
    ): List<Voice> =
        model.voices.map {
            val file = it.fileName.toString()

            val language =
                when {
                    file.startsWith("a") -> "en"
                    file.startsWith("b") -> "en"
                    file.startsWith("j") -> "ja"
                    file.startsWith("f") -> "fr"
                    file.startsWith("e") -> "es"
                    file.startsWith("h") -> "hi"
                    file.startsWith("i") -> "it"
                    file.startsWith("z") -> "zh"
                    file.startsWith("p") -> "pt"
                    else -> throw Exception("Determining language from file name: $it")
                }

            val gender =
                when {
                    file.startsWith("f", 1) -> "female"
                    file.startsWith("m", 1) -> "male"
                    else -> throw Exception("Determining gender from file name: $it")
                }

            Voice(
                modelId = modelId,
                language = language,
                gender = gender,
            )
        }
}
