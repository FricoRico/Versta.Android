package app.versta.translate.core.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class LanguageModelFiles(
    val path: Path,
    val tokenizer: LanguageModelTokenizerFiles,
    val inference: LanguageModelInferenceFiles)
{
    fun isValid() = tokenizer.isValid() &&
            inference.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(path: Path): LanguageModelFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Language model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata = serializer.decodeFromString<LanguageMetadata>(metadataFile.readText())
            val files = LanguageModelFiles(
                path = path,
                tokenizer = LanguageModelTokenizerFiles(
                    config = path.resolve(metadata.files.tokenizer.config),
                    sourceVocabulary = path.resolve(metadata.files.tokenizer.sourceVocabulary),
                    targetVocabulary = metadata.files.tokenizer.targetVocabulary?.let { path.resolve(it) },
                    source = path.resolve(metadata.files.tokenizer.source),
                    target = path.resolve(metadata.files.tokenizer.target)
                ),
                inference = LanguageModelInferenceFiles(
                    encoder = path.resolve(metadata.files.inference.encoder),
                    decoder = path.resolve(metadata.files.inference.decoder)
                )
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Language model files are not complete and valid: $files")
            }

            return files
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
    val decoder: Path
) {
    fun isValid() = encoder.exists() &&
            decoder.exists()
}
