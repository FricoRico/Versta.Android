package app.versta.translate.database.migrations

import app.cash.sqldelight.db.AfterVersion
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.database.Database
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import java.app.versta.translate.database.sqldelight.Voice

object Migration3 : Migration {
    override fun migrate(database: DatabaseContainer) {
        Database.Schema.migrate(
            driver = database.driver,
            oldVersion = 3,
            newVersion = 4,
            AfterVersion(3) {
                database.voiceModels.getAll().executeAsList().forEach { data ->
                    val path = data.path.toPath().toNioPath()

                    val model = VoiceWithModelFiles.load(data.id, path)
                    val voices = model.voices.map {
                        val file = it.fileName.toString()

                        val language = when {
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

                        val gender = when {
                            file.startsWith("f", 1) -> "female"
                            file.startsWith("m", 1) -> "male"
                            else -> throw Exception("Determining gender from file name: $it")
                        }

                        Voice(
                            modelId = data.id,
                            language = language,
                            gender = gender
                        )
                    }

                    database.voices.transaction {
                        voices.forEach {
                            database.voices.insertOrIgnore(
                                modelId = it.modelId,
                                language = it.language,
                                gender = it.gender
                            )
                        }
                    }
                }
            }
        )
    }
}
