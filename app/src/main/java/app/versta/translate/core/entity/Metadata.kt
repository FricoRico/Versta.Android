package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class ModelArchitecture(val value: String) {
    MarianMTModel("MarianMTModel")
}

@Serializable
class BundleMetadata(
    val version: String = "",
    val metadata: List<LanguageModel>,
    val bidirectional: Boolean,
    private val languages: List<String>
) {
    fun isValid() =
        languages.isNotEmpty()
            && (if (bidirectional) languages.size % 2 == 0 else true)
            && metadata.isNotEmpty()
}

@Serializable
data class LanguageModel(
    val directory: String,
    @SerialName("source_language")
    val sourceLanguage: String,
    @SerialName("target_language")
    val targetLanguage: String
)


@Serializable
class LanguageMetadata(
    val version: String = "",
    @SerialName("base_model")
    val baseModel: String,
    @SerialName("source_language")
    val sourceLanguage: String,
    @SerialName("target_language")
    val targetLanguage: String,
    val architectures: List<ModelArchitecture>,
    val files: LanguageModelFilesMetadata,
    var root: Path? = null
) {
    fun isValid() = baseModel.isNotBlank()
            && sourceLanguage.isNotBlank()
            && architectures.isNotEmpty()
            && (root != null && files.isValid(root!!))
            && root?.isAbsolute ?: false

    fun setRootPath(path: Path): LanguageMetadata {
        root = path

        return this
    }
}

@Serializable
data class ModelMetadata(
    val bundleMetadata: BundleMetadata,
    val languageMetadata: List<LanguageMetadata>
) {
    fun setRootPath(path: Path): ModelMetadata {
        languageMetadata.forEach {
            it.setRootPath(path)
        }

        return this
    }
}

@Serializable
data class LanguageModelFilesMetadata(
    val tokenizer: LanguageModelTokenizerFilesMetadata,
    val inference: LanguageModelInferenceFilesMetadata)
{
    fun isValid(path: Path) = tokenizer.isValid(path) &&
            inference.isValid(path)
}

@Serializable
data class LanguageModelTokenizerFilesMetadata(
    val config: String,
    @SerialName("vocabulary_optimized")
    val sourceVocabulary: String,
    @SerialName("target_vocabulary")
    val targetVocabulary: String? = null,
    val source: String,
    val target: String
) {
    fun isValid(path: Path) = path.resolve(config).exists() &&
            path.resolve(sourceVocabulary).exists() &&
            targetVocabulary?.let { path.resolve(it).exists() } ?: true &&
            path.resolve(source).exists() &&
            path.resolve(target).exists()
}

@Serializable
data class LanguageModelInferenceFilesMetadata(
    val encoder: String,
    val decoder: String
) {
    fun isValid(path: Path) = path.resolve(encoder).exists() &&
            path.resolve(decoder).exists()
}

