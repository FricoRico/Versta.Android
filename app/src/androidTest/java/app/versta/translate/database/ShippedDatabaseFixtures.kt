package app.versta.translate.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * A pristine on-disk database as a public release created it: the table set
 * from that release's `.sq` files, written raw, with `PRAGMA user_version`
 * pinned to the database version the release shipped with.
 *
 * Shipped database versions (git archaeology over release tags):
 *
 * | DB version | Releases                 | Schema additions over previous        |
 * |------------|--------------------------|---------------------------------------|
 * | 1          | v1.0.1, v1.0.2           | Language, LanguageModel               |
 * | 2          | v1.1.0, v1.1.1           | + TextToSpeechModel (1.sqm)           |
 * | 4          | v1.2.0 – v1.3.1 (Play)   | TextToSpeechModel → VoiceModel (2.sqm), + Voice (3.sqm) |
 *
 * Database versions 3, 5, 6 and 7 never shipped: 2.sqm and 3.sqm landed within
 * the v1.2.0 release window, and 4.sqm–7.sqm are develop-only (first carried
 * by the beta that ships schema v8).
 *
 * Schemas are stable within a version line: no `.sq` table shape changed
 * between v1.0.1→v1.0.2, v1.1.0→v1.1.1 or v1.2.0→v1.3.1 (verified by diffing
 * the tags; the only change was an added LanguageModel query). Fixtures model
 * the shape of the first release carrying each version and must be updated
 * only if the shipped-version map itself changes.
 *
 * [DATABASE_V1_PRE_BIDIRECTIONAL] models the latent NEU-18 gap: `bidirectional`
 * was added to `Language.sq` (and `files` dropped from `LanguageModel.sq`) by
 * e4d5060 without a `.sqm` ALTER, so databases created by builds predating
 * that commit never receive the column and keep the `files` shape. No public
 * release carried this shape (first release v1.0.1 already includes e4d5060),
 * but it is the worst case the schema repair must handle, so it gets a fixture.
 */
data class ShippedDatabaseFixture(
    /** Value of `PRAGMA user_version` as the release left it. */
    val version: Long,
    /** Release tags that shipped this database version. */
    val shippedIn: List<String>,
    /** `CREATE TABLE`/`CREATE INDEX` statements of the release-era schema. */
    val schema: List<String>,
    /** Seeds representative pre-upgrade rows and on-disk model files. */
    val seed: (SQLiteDatabase, Context) -> Unit,
    /** Voice models Migration4 must purge (below the v1.2.0 boundary). */
    val voiceModelIdsPurged: List<String> = emptyList(),
    /** Voice models that must survive, re-marked as StyleTTS2. */
    val voiceModelIdsSurviving: List<String> = emptyList(),
    /** Whether Migration3's Voice backfill boundary is crossed. */
    val expectsVoiceBackfill: Boolean = false,
    /** Voice rows the seed pre-populates (v4 era had the table). */
    val preSeededVoices: Set<Pair<String, String>> = emptySet(),
) {
    override fun toString(): String =
        if (shippedIn.isEmpty()) {
            "v$version-legacy-shape"
        } else {
            "v$version(${shippedIn.first()}..${shippedIn.last()})"
        }

    /**
     * Writes the pristine database to [databaseFile]. Foreign keys are left
     * disabled on the fixture connection, matching the field reality: the app
     * never enables them (audit NEU-18.1).
     */
    fun write(
        context: Context,
        databaseFile: File,
    ) {
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.execSQL("PRAGMA foreign_keys = OFF")
            schema.forEach { database.execSQL(it) }
            seed(database, context)
            database.version = version.toInt()
        }
    }
}

object ShippedDatabaseFixtures {
    // v1 schema, as shipped in v1.0.1 (2025-03-09). Language already carries
    // `bidirectional`; LanguageModel carries `version` and the FK.
    private val languageV101 =
        """
        CREATE TABLE Language (
            id TEXT NOT NULL PRIMARY KEY,
            source TEXT NOT NULL,
            target TEXT NOT NULL,
            bidirectional INTEGER DEFAULT 0
        );
        CREATE INDEX languagePair ON Language(source, target);
        """.trimIndent()

    private val languageModelV101 =
        """
        CREATE TABLE LanguageModel (
            languageId TEXT NOT NULL PRIMARY KEY REFERENCES Language(id) ON DELETE CASCADE,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    // v1 additions shipped in v1.1.0 (2025-03-30), created by 1.sqm.
    private val textToSpeechModelV110 =
        """
        CREATE TABLE TextToSpeechModel (
            id TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    // v3/v4 additions shipped together in v1.2.0 (2025-04-23): 2.sqm renames
    // TextToSpeechModel to VoiceModel, 3.sqm adds Voice.
    private val voiceModelV120 =
        """
        CREATE TABLE VoiceModel (
            id TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            version TEXT NOT NULL
        );
        """.trimIndent()

    private val voiceV120 =
        """
        CREATE TABLE Voice (
            modelId TEXT NOT NULL REFERENCES VoiceModel(id) ON DELETE CASCADE,
            language TEXT NOT NULL,
            gender TEXT NOT NULL,

            UNIQUE(modelId, language, gender)
        );
        """.trimIndent()

    // Pre-e4d5060 shape (2024-10 era builds, never in a public release):
    // Language without `bidirectional`, LanguageModel keyed without the FK and
    // with a `files` column instead of `version`.
    private val languageLegacy =
        """
        CREATE TABLE Language (
            id TEXT NOT NULL PRIMARY KEY,
            source TEXT NOT NULL,
            target TEXT NOT NULL
        );
        CREATE INDEX languagePair ON Language(source, target);
        """.trimIndent()

    private val languageModelLegacy =
        """
        CREATE TABLE LanguageModel (
            languageId TEXT NOT NULL PRIMARY KEY,
            baseModel TEXT NOT NULL,
            architectures TEXT NOT NULL,
            path TEXT NOT NULL,
            files TEXT NOT NULL
        );
        CREATE INDEX languageModelLanguageId ON LanguageModel(languageId);
        """.trimIndent()

    private val voiceModelsRoot: Context.() -> File = { filesDir.resolve("voice-models") }
    private val externalData: Context.() -> File = { filesDir.resolve("external-data") }
    private val externalDataHash: Context.() -> File = { filesDir.resolve("external-data.sha256") }

    private fun seedLanguages(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO Language (id, source, target) VALUES (?, ?, ?)",
            arrayOf("en-nl", "en", "nl"),
        )
        database.execSQL(
            "INSERT INTO Language (id, source, target) VALUES (?, ?, ?)",
            arrayOf("nl-en", "nl", "en"),
        )
    }

    private fun seedLanguageModel(database: SQLiteDatabase) {
        database.execSQL(
            "INSERT INTO LanguageModel (languageId, baseModel, architectures, path, version) VALUES (?, ?, ?, ?, ?)",
            arrayOf("en-nl", "bergamot-tiny", "[\"Bergamot\"]", "/nonexistent/en-nl/model", "v1.0.0"),
        )
    }

    private fun seedExternalData(context: Context) {
        context.externalData().mkdirs()
        context.externalData().resolve("payload.bin").writeText("legacy")
        context.externalDataHash().writeText("hash")
    }

    /**
     * Writes a voice model's on-disk files in the layout the era produced and
     * [app.versta.translate.core.entity.VoiceWithModelFiles.load] still reads:
     * `<id>/model/` holding the model, tokenizer and voice files plus
     * `metadata.json`. Two bundled voices (af_, pm_) map to en:female and
     * pt:male in [app.versta.translate.database.migrations.Migration3]'s
     * backfill.
     */
    internal fun writeVoiceModelFiles(
        context: Context,
        id: String,
        version: String,
    ): File {
        val model = context.voiceModelsRoot().resolve(id).resolve("model")
        model.mkdirs()
        model.resolve("model.onnx").writeBytes(byteArrayOf(0))
        model.resolve("vocab.txt").writeText("")
        model.resolve("af_alpha.bin").writeBytes(byteArrayOf(0))
        model.resolve("pm_beta.bin").writeBytes(byteArrayOf(0))
        model.resolve("metadata.json").writeText(
            """
            {
                "version": "$version",
                "base_model": "styletts2",
                "architectures": ["StyleTTS2"],
                "files": {
                    "inference": { "model": "model.onnx" },
                    "tokenizer": { "vocabulary": "vocab.txt" },
                    "voices": ["af_alpha.bin", "pm_beta.bin"]
                }
            }
            """.trimIndent(),
        )

        return model
    }

    private fun insertVoiceModel(
        database: SQLiteDatabase,
        table: String,
        id: String,
        path: String,
        version: String,
    ) {
        database.execSQL(
            "INSERT INTO $table (id, baseModel, architectures, path, version) VALUES (?, ?, ?, ?, ?)",
            arrayOf(id, "styletts2", "[\"Kokoro\"]", path, version),
        )
    }

    val DATABASE_V1 =
        ShippedDatabaseFixture(
            version = 1,
            shippedIn = listOf("v1.0.1", "v1.0.2"),
            schema = listOf(languageV101, languageModelV101),
            seed = { database, context ->
                seedLanguages(database)
                seedLanguageModel(database)
                seedExternalData(context)
            },
        )

    /**
     * v1.1.x introduced text-to-speech: one model exactly at the `v1.2.0`
     * purge boundary ("voice-kept", survives) and one below it ("voice-legacy",
     * dropped with its files by Migration4).
     */
    val DATABASE_V2 =
        ShippedDatabaseFixture(
            version = 2,
            shippedIn = listOf("v1.1.0", "v1.1.1"),
            schema = listOf(languageV101, languageModelV101, textToSpeechModelV110),
            seed = { database, context ->
                seedLanguages(database)
                seedLanguageModel(database)
                seedExternalData(context)
                insertVoiceModel(
                    database,
                    table = "TextToSpeechModel",
                    id = "voice-legacy",
                    path = writeVoiceModelFiles(context, "voice-legacy", "v1.1.0").absolutePath,
                    version = "v1.1.0",
                )
                insertVoiceModel(
                    database,
                    table = "TextToSpeechModel",
                    id = "voice-kept",
                    path = writeVoiceModelFiles(context, "voice-kept", "v1.2.0").absolutePath,
                    version = "v1.2.0",
                )
            },
            voiceModelIdsPurged = listOf("voice-legacy"),
            voiceModelIdsSurviving = listOf("voice-kept"),
            expectsVoiceBackfill = true,
        )

    /**
     * The version all Play devices run today (v1.3.1, versionCode 12). Voice
     * rows reference "voice-kept" so the FK cascade is observable post-upgrade.
     */
    val DATABASE_V4 =
        ShippedDatabaseFixture(
            version = 4,
            shippedIn = listOf("v1.2.0", "v1.2.1", "v1.2.2", "v1.3.0", "v1.3.1"),
            schema = listOf(languageV101, languageModelV101, voiceModelV120, voiceV120),
            seed = { database, context ->
                seedLanguages(database)
                seedLanguageModel(database)
                seedExternalData(context)
                insertVoiceModel(
                    database,
                    table = "VoiceModel",
                    id = "voice-legacy",
                    path = writeVoiceModelFiles(context, "voice-legacy", "v1.1.0").absolutePath,
                    version = "v1.1.0",
                )
                insertVoiceModel(
                    database,
                    table = "VoiceModel",
                    id = "voice-kept",
                    path = writeVoiceModelFiles(context, "voice-kept", "v1.2.0").absolutePath,
                    version = "v1.2.0",
                )
                database.execSQL(
                    "INSERT INTO Voice (modelId, language, gender) VALUES (?, ?, ?)",
                    arrayOf("voice-kept", "en", "female"),
                )
            },
            voiceModelIdsPurged = listOf("voice-legacy"),
            voiceModelIdsSurviving = listOf("voice-kept"),
            preSeededVoices = setOf("en" to "female"),
        )

    /**
     * Not a shipped shape (see class KDoc) — included so the schema repair
     * migration is exercised against the worst known starting point.
     */
    val DATABASE_V1_PRE_BIDIRECTIONAL =
        ShippedDatabaseFixture(
            version = 1,
            shippedIn = emptyList(),
            schema = listOf(languageLegacy, languageModelLegacy),
            seed = { database, context ->
                seedLanguages(database)
                database.execSQL(
                    "INSERT INTO LanguageModel (languageId, baseModel, architectures, path, files) VALUES (?, ?, ?, ?, ?)",
                    arrayOf("en-nl", "bergamot-tiny", "[\"Bergamot\"]", "/nonexistent/en-nl/model", "{}"),
                )
                seedExternalData(context)
            },
        )

    val ALL =
        listOf(
            DATABASE_V1,
            DATABASE_V1_PRE_BIDIRECTIONAL,
            DATABASE_V2,
            DATABASE_V4,
        )
}
