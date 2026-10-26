package app.versta.translate.adapter.outbound

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.versta.translate.core.entity.LanguageBundleData
import app.versta.translate.core.entity.LanguageBundleMetadata
import app.versta.translate.core.entity.LanguageMetadata
import app.versta.translate.core.entity.LanguageModelConfigurationMetadata
import app.versta.translate.core.entity.LanguageModelFilesMetadata
import app.versta.translate.core.entity.LanguageModelMetadata
import app.versta.translate.database.DatabaseContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LanguageDatabaseRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun repository(): LanguageDatabaseRepository = LanguageDatabaseRepository(DatabaseContainer(context))

    private fun moduleMetadata(
        source: String = "ar",
        target: String = "en",
        directory: String = "module",
        version: String = "v1.12.14",
    ): LanguageModelMetadata {
        val moduleDir = File(bundleDir(), directory).apply { mkdirs() }
        moduleDir.resolve("model.bin").writeText("model")
        moduleDir.resolve("vocabulary.bin").writeText("vocabulary")
        moduleDir.resolve("shortlist.bin").writeText("shortlist")
        moduleDir.resolve("metadata.json").writeText(
            """
            {
                "version": "$version",
                "base_model": "$source-$target:base-memory",
                "source_language": "$source",
                "target_language": "$target",
                "score": 1.0,
                "files": {
                    "model": "model.bin",
                    "vocabulary": "vocabulary.bin",
                    "shortlist": "shortlist.bin"
                },
                "config": {
                    "encoder_layers": 1,
                    "decoder_layers": 1,
                    "ffn_depth": 2,
                    "num_heads": 8
                }
            }
            """.trimIndent(),
        )

        return LanguageModelMetadata(
            baseModel = "$source-$target:base-memory",
            sourceLanguage = source,
            targetLanguage = target,
            files =
                LanguageModelFilesMetadata(
                    model = "model.bin",
                    vocabulary = "vocabulary.bin",
                    shortlist = "shortlist.bin",
                ),
            config =
                LanguageModelConfigurationMetadata(
                    encoderLayers = 1,
                    decoderLayers = 1,
                    numHeads = 8,
                ),
            root = moduleDir.toPath(),
        )
    }

    private fun bundleDir(): File = File(context.cacheDir, "test-bundle").apply { mkdirs() }

    private fun writeBundleMetadata(version: String) {
        bundleDir().resolve("metadata.json").writeText(
            """
            {
                "version": "$version",
                "bidirectional": false,
                "languages": ["ar", "en"],
                "metadata": [
                    {
                        "directory": "module",
                        "source_language": "ar",
                        "target_language": "en"
                    }
                ]
            }
            """.trimIndent(),
        )
    }

    private fun bundleData(version: String = "v2.0.0") =
        LanguageBundleData(
            bundle =
                LanguageBundleMetadata(
                    version = version,
                    metadata =
                        listOf(
                            LanguageMetadata(
                                directory = "module",
                                sourceLanguage = "ar",
                                targetLanguage = "en",
                            ),
                        ),
                    bidirectional = false,
                    languages = listOf("ar", "en"),
                ),
            languages = listOf(moduleMetadata()),
        )

    @Test
    fun upsertLanguageModels_storesBundleVersion_notModuleEngineVersion() =
        runBlocking {
            val repo = repository()

            repo.upsertLanguageModels(bundleData(version = "v2.0.0"))

            val pair = repo.getLanguages().first().first()
            assertEquals("ar-en", pair.pair.uniqueId())
            assertEquals("v2.0.0", pair.files.version)
        }

    @Test
    fun getLanguages_prefersBundleMetadataForLegacyRows() =
        runBlocking {
            val repo = repository()
            // Simulate a pre-fix install: only the per-module engine version reached the database.
            repo.upsertLanguageModel(moduleMetadata(version = "v1.12.14"))
            writeBundleMetadata(version = "v2.0.0")

            val pair = repo.getLanguages().first().first()

            assertEquals("v2.0.0", pair.files.version)
        }

    @Test
    fun getLanguagePairs_emitsAfterUpsert() =
        runBlocking {
            val repo = repository()
            val channel = repo.getLanguagePairs().produceIn(this)
            channel.receive()

            repo.upsertLanguageModels(bundleData())

            val updated = withTimeout(5_000) { channel.receive() }
            channel.cancel()
            assertTrue(updated.any { it.uniqueId() == "ar-en" })
        }

    @Test
    fun getLanguages_emitsAfterUpsert() =
        runBlocking {
            val repo = repository()
            val channel = repo.getLanguages().produceIn(this)
            channel.receive()

            repo.upsertLanguageModels(bundleData())

            val updated = withTimeout(5_000) { channel.receive() }
            channel.cancel()
            assertTrue(updated.any { it.pair.uniqueId() == "ar-en" })
        }
}
