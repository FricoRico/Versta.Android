package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceGender
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Supertonic 3 cut-over: deletes every pre-Supertonic voice model row and its
 * on-disk files (Kokoro/StyleTTS2 bundles), removes the espeak-ng/open-jtalk
 * TTS data download, and registers the sixty-two `supertonic3` voice mappings
 * (thirty-one languages, two genders) so availability follows the database.
 *
 * Data-only migration: no `.sqm` of its own. It is gated on version boundary 7
 * so it runs exactly once for devices upgrading from a shipped schema (1/2/4)
 * to the current one (8) and is skipped by fresh installs, which receive the
 * mappings when the bundle is installed instead. Every step is idempotent.
 *
 * Note: the inserted `Voice` rows reference model id [Supertonic3.ID] before
 * its `VoiceModel` row exists (the bundle installs later). Foreign keys are
 * off until NEU-65; the FK-enforcement design has to tolerate this.
 */
object Migration7 : Migration {
    override val afterVersion = 7L

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        removeLegacyVoiceModels(database)
        removeTextToSpeechData(database, context)
        registerSupertonic3Voices(database)
    }

    private fun removeLegacyVoiceModels(database: DatabaseContainer) {
        database.voiceModels.getAll().executeAsList().forEach { data ->
            if (data.architectures.contains(VoiceModelArchitecture.Supertonic3.value)) {
                return@forEach
            }

            database.voices.deleteById(data.id)
            database.voiceModels.deleteById(data.id)
            removeDirectory(
                data.path
                    .toPath()
                    .toNioPath()
                    .parent,
            )
        }
    }

    private fun removeTextToSpeechData(
        database: DatabaseContainer,
        context: Context,
    ) {
        database.data.getByType(DataType.TTS.value).executeAsList().forEach { data ->
            removeDirectory(
                data.path
                    .toPath()
                    .toNioPath(),
            )
        }

        database.data.deleteByType(DataType.TTS.value)

        removeDirectory(context.filesDir.resolve("external-data").toPath())
        removeDirectory(context.filesDir.resolve("external-data.sha256").toPath())
    }

    private fun registerSupertonic3Voices(database: DatabaseContainer) {
        database.voices.transaction {
            Supertonic3.VOICES.forEach { voice ->
                database.voices.insertOrIgnore(
                    modelId = Supertonic3.ID,
                    language = voice.language,
                    gender =
                        when (voice.gender) {
                            VoiceGender.Female -> "female"
                            VoiceGender.Male -> "male"
                        },
                )
            }
        }
    }

    private fun removeDirectory(path: Path?) {
        if (path == null || !path.exists()) {
            return
        }

        path.toFile().deleteRecursively()
    }
}
