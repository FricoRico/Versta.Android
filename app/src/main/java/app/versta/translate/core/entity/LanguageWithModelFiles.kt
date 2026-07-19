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

data class LanguageModelPair(
    private val sourceLocale: Locale,
    private val targetLocale: Locale,
    val files: LanguageModel,
) {
    val pair = LanguagePair(
        source = Language.fromLocale(sourceLocale),
        target = Language.fromLocale(targetLocale)
    )
}

@Serializable
data class LanguageModel(
    val path: Path,
    val baseModel: String,
    val score: Double? = 0.0,
    val size: Long = 0,
    val version: String,
    val files: LanguageModelFiles,
    val config: LanguageModelConfiguration
) {
    fun isValid() = files.isValid() &&
            config.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(path: Path): LanguageModel {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Language model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<LanguageModelMetadata>(metadataFile.readText())
            val files = LanguageModel(
                path = path,
                baseModel = metadata.baseModel,
                version = metadata.version,
                score = metadata.score ?: 0.0,
                size = size(path.parent),
                files = LanguageModelFiles(
                    model = path.resolve(metadata.files.model),
                    vocabulary = path.resolve(metadata.files.vocabulary),
                    targetVocabulary = metadata.files.targetVocabulary?.let {
                        path.resolve(it)
                    },
                    shortlist = path.resolve(metadata.files.shortlist)
                ),
                config = LanguageModelConfiguration(
                    encoderLayers = metadata.config.encoderLayers,
                    decoderLayers = metadata.config.decoderLayers,
                    ffnDepth = metadata.config.ffnDepth,
                    numHeads = metadata.config.numHeads,
                    splitMode = metadata.config.splitMode
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
data class LanguageModelFiles(
    val model: Path,
    val vocabulary: Path,
    val targetVocabulary: Path? = null,
    val shortlist: Path,
) {
    fun isValid() = model.exists() &&
            vocabulary.exists() &&
            targetVocabulary?.exists() ?: true &&
            shortlist.exists()
}

@Serializable
data class LanguageModelConfiguration(
    val encoderLayers: Long,
    val decoderLayers: Long,
    val ffnDepth: Long = 2,
    val numHeads: Long,
    val splitMode: String = "sentence"
) {
    fun isValid() = encoderLayers > 0 && decoderLayers > 0 &&
            ffnDepth > 0 && numHeads > 0
}

data class PivotPairModelFiles(
    val intermediary: LanguageModel?,
    val output: LanguageModel?
) {
    fun isValid() = intermediary?.isValid() != false && output?.isValid() != false
}
