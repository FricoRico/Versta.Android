package app.versta.translate.core.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.Locale
import kotlin.io.path.exists

data class LanguagePairModelFiles(
    private val sourceLocale: Locale,
    private val targetLocale: Locale,
    val files: LanguageModelFiles,
) {
    val pair = LanguagePair(
        source = Language.fromLocale(sourceLocale),
        target = Language.fromLocale(targetLocale)
    )
}

@Serializable
data class LanguageModelFiles(
    val path: Path,
    val baseModel: String,
    val architectures: List<LanguageModelArchitecture>,
    val architectureConfig: ArchitectureConfig? = null,
    val score: Double? = 0.0,
    val size: Long = 0,
    val version: String,
    val tokenizer: LanguageModelTokenizerFiles,
    val inference: LanguageModelInferenceFiles
) {
    fun isValid() = tokenizer.isValid() &&
            inference.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(path: Path): LanguageModelFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Language model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<LanguageModelMetadata>(metadataFile.readText())
            val files = LanguageModelFiles(
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                architectureConfig = metadata.architectureConfig,
                version = metadata.version,
                tokenizer = LanguageModelTokenizerFiles(
                    config = path.resolve(metadata.files.tokenizer.config),
                    sourceVocabulary = path.resolve(metadata.files.tokenizer.sourceVocabulary),
                    targetVocabulary = metadata.files.tokenizer.targetVocabulary?.let {
                        path.resolve(
                            it
                        )
                    },
                    source = path.resolve(metadata.files.tokenizer.source),
                    target = path.resolve(metadata.files.tokenizer.target)
                ),
                score = metadata.score ?: 0.0,
                size = size(path.parent),
                inference = LanguageModelInferenceFiles(
                    encoder = path.resolve(metadata.files.inference.encoder),
                    decoder = path.resolve(metadata.files.inference.decoder),
                    architectureConfig = metadata.architectureConfig
                )
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Language model files are not complete and valid: $files")
            }

            return files
        }

        private fun size(path: Path): Long {
            var folderSize: Long = 0

            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    folderSize += Files.size(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) {
                        throw exc
                    }

                    return FileVisitResult.CONTINUE
                }
            })

            return folderSize
        }
    }
}

@Serializable
data class LanguageModelTokenizerFiles(
    val config: Path,
    val sourceVocabulary: Path,
    val targetVocabulary: Path? = null,
    val source: Path,
    val target: Path
) {
    fun isValid() = config.exists() &&
            sourceVocabulary.exists() &&
            targetVocabulary?.exists() ?: true &&
            source.exists() &&
            target.exists()
}

@Serializable
data class LanguageModelInferenceFiles(
    val encoder: Path,
    val decoder: Path,
    val architectureConfig: ArchitectureConfig? = null
) {
    fun isValid() = encoder.exists() &&
            decoder.exists()
}

data class PivotPairModelFiles(
    val intermediary: LanguageModelFiles?,
    val output: LanguageModelFiles?
) {
    fun isValid() = intermediary?.isValid() != false && output?.isValid() != false
}
