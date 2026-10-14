package app.versta.translate.database.migrations

import android.content.Context
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.database.Migration
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * One-time orphan sweep ahead of foreign-key enforcement (NEU-65): deletes
 * `Voice` rows without a parent `VoiceModel` and `LanguageModel` rows without
 * a parent `Language` (both creatable only while constraints went
 * unenforced), then removes legacy language-model bundle directories under
 * `filesDir` that no surviving `LanguageModel` row references — the fold-in
 * of the file cleanup the now-dead `Migration6` can never reach.
 *
 * Emits one `orphan_sweep` crash-reporting event with pre/post row counts
 * (post counts are the NEU-56 invariant: 0) and the number of model
 * directories removed.
 *
 * Data-only migration paired with the witness `8.sqm`, which bumps the schema
 * version to 9 purely so devices already on version 8 cross this boundary and
 * sweep exactly once. Idempotent: orphan queries and file pruning converge to
 * zero on a second run. Registered last so it sweeps whatever the earlier
 * data migrations of the same release leave behind.
 */
object Migration8 : Migration {
    override val afterVersion = 8L

    private const val EVENT = "orphan_sweep"
    private const val BUNDLE_METADATA_FILE = "metadata.json"
    private val TAG = Migration8::class.java.simpleName

    private val json = Json { ignoreUnknownKeys = true }

    override fun migrate(
        database: DatabaseContainer,
        context: Context,
    ) {
        val voiceOrphansBefore = database.voices.countOrphaned().executeAsOne()
        val languageModelOrphansBefore =
            database.languageModels
                .countOrphaned()
                .executeAsOne()

        database.transaction {
            database.voices.deleteOrphaned()
            database.languageModels.deleteOrphaned()
        }

        val removedModelDirectories = removeOrphanedLanguageModelFiles(database, context)

        database.crashReporter.recordEvent(
            EVENT,
            mapOf(
                "voice_orphans_before" to voiceOrphansBefore,
                "language_model_orphans_before" to languageModelOrphansBefore,
                "voice_orphans_after" to
                    database.voices
                        .countOrphaned()
                        .executeAsOne(),
                "language_model_orphans_after" to
                    database.languageModels
                        .countOrphaned()
                        .executeAsOne(),
                "language_model_dirs_removed" to removedModelDirectories,
            ),
        )
    }

    /**
     * Deletes language-model content under `filesDir` that no surviving
     * `LanguageModel` row points at. A directory is only treated as a
     * language bundle when its [BUNDLE_METADATA_FILE] parses and validates as
     * [LanguageBundleMetadata]; anything else (voice models, logs, future
     * stores) is left untouched. Unreferenced model directories are pruned
     * individually; the bundle root itself is removed once no model inside it
     * is referenced anymore.
     */
    private fun removeOrphanedLanguageModelFiles(
        database: DatabaseContainer,
        context: Context,
    ): Long {
        val referenced =
            database.languageModels
                .getAll()
                .executeAsList()
                .map { File(it.path).canonicalFile }
                .toSet()

        var removed = 0L

        context.filesDir.listFiles().orEmpty().forEach { root ->
            val bundle = root.languageBundleOrNull() ?: return@forEach

            val unreferenced =
                bundle.metadata
                    .map { File(root, it.directory).canonicalFile }
                    .filter { it.exists() && it !in referenced }

            unreferenced.forEach { model ->
                model.deleteRecursively()
                removed++
            }

            val hasReferencedContent =
                root.listFiles().orEmpty().any { it.isDirectory && it.canonicalFile in referenced }
            if (!hasReferencedContent) {
                root.deleteRecursively()
            }
        }

        return removed
    }

    private fun File.languageBundleOrNull(): LanguageBundleMetadata? {
        val metadataFile = resolve(BUNDLE_METADATA_FILE)
        if (!isDirectory || !metadataFile.isFile) {
            return null
        }

        return try {
            json
                .decodeFromString<LanguageBundleMetadata>(metadataFile.readText())
                .takeIf { it.isValid() }
        } catch (e: SerializationException) {
            Timber.tag(TAG).w(e, "Skipping directory with unreadable bundle metadata: $absolutePath")
            null
        } catch (e: IOException) {
            Timber.tag(TAG).w(e, "Skipping directory with unreadable bundle metadata: $absolutePath")
            null
        }
    }
}
