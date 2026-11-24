package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.core.entity.VoiceWithModelFiles
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File

/**
 * Backfilled the `Voice` table (added by `3.sqm`) from the voice files of
 * installed pre-Supertonic voice models.
 *
 * Since the Supertonic 3 cut-over the app schema only reads Supertonic 3
 * bundles, which cannot exist on a database this old, so legacy bundles are
 * skipped: every row this migration could write for them is purged by
 * [Migration7] in the same upgrade. A bundle whose metadata file is missing
 * entirely is corrupt state and still crashes the migration instead of being
 * swallowed.
 */
object Migration3 : Migration {
    override val afterVersion = 3L

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        database.voiceModels.getAll().executeAsList().forEach { data ->
            val path = data.path.toPath().toNioPath()

            require(File(path.toFile(), "metadata.json").exists()) {
                "Voice model metadata file not found: ${path.toAbsolutePath()}"
            }

            try {
                VoiceWithModelFiles.load(data.id, path)
            } catch (e: IllegalArgumentException) {
                Timber.tag(TAG).i(e, "Skipping legacy voice model ${data.id}: no longer parsable")
            }
        }
    }

    private val TAG: String = Migration3::class.java.simpleName
}
