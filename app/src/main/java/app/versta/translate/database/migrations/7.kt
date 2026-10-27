package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.core.entity.DataType
import app.versta.translate.core.entity.Supertonic3
import app.versta.translate.core.entity.VoiceModelArchitecture
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * Supertonic 3 cut-over: deletes every pre-Supertonic voice model row and its
 * on-disk files (Kokoro/StyleTTS2 bundles) and removes the espeak-ng/open-jtalk
 * TTS data download.
 *
 * The sixty-two `supertonic3` voice mappings are deliberately NOT registered
 * here: a `Voice` row inserted now would reference the not-yet-installed
 * [Supertonic3.ID] model row, which violates the foreign key NEU-65 starts
 * enforcing (and its orphan sweep would delete the rows regardless). The
 * canonical mapping registers in the bundle-install transaction instead
 * ([app.versta.translate.adapter.outbound.VoiceDatabaseRepository.upsertVoiceModel]).
 *
 * Data-only migration: no `.sqm` of its own. It is gated on version boundary 7
 * so it runs exactly once for devices upgrading from a shipped schema (1/2/4)
 * to the current one (8) and is skipped by fresh installs. Every step is
 * idempotent.
 */
object Migration7 : Migration {
    override val afterVersion = 7L

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        removeLegacyVoiceModels(database)
        removeTextToSpeechData(database, context)
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

    private fun removeDirectory(path: Path?) {
        if (path == null || !path.exists()) {
            return
        }

        path.toFile().deleteRecursively()
    }
}
