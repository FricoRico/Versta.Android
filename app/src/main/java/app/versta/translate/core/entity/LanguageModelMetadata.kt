package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class LanguageModelArchitecture(val value: String) {
    MarianMTModel("MarianMTModel"),
}

@Serializable
data class ArchitectureConfig(
    @SerialName("num_layers")
    val numLayers: Int,
    @SerialName("num_heads")
    val numHeads: Int,
    @SerialName("head_dim")
    val headDim: Int,
    @SerialName("d_model")
    val dModel: Int
)

@Serializable
data class LanguageModelMetadata(
    val version: String = "",
    @SerialName("base_model")
    val baseModel: String,
    @SerialName("source_language")
    val sourceLanguage: String,
    @SerialName("target_language")
    val targetLanguage: String,
    val score: Double? = 0.0,
    val architectures: List<LanguageModelArchitecture>,
    @SerialName("architecture_config")
    val architectureConfig: ArchitectureConfig? = null,
    val files: LanguageModelFilesMetadata,
    var root: Path? = null
) {
    fun isValid() = baseModel.isNotBlank()
            && sourceLanguage.isNotBlank()
            && architectures.isNotEmpty()
            && (root != null && files.isValid(root!!))
            && root?.isAbsolute ?: false

    fun setRootPath(path: Path): LanguageModelMetadata {
        root = path

        return this
    }
}

@Serializable
data class LanguageMetadata(
    val directory: String,
    @SerialName("source_language")
    val sourceLanguage: String,
    @SerialName("target_language")
    val targetLanguage: String
)

@Serializable
data class LanguageBundleMetadata(
    val version: String = "",
    val metadata: List<LanguageMetadata>,
    val bidirectional: Boolean,
    val languages: List<String>
) {
    fun isValid() =
        languages.isNotEmpty()
                && (if (bidirectional) languages.size % 2 == 0 else true)
                && metadata.isNotEmpty()

    fun languagePairs(): List<LanguagePair> {
        return metadata.map {
            LanguagePair.fromIsoCodes(it.sourceLanguage, it.targetLanguage)
        }
    }

    fun distinctLanguagePairs(): List<LanguagePair> {
        return languagePairs().distinctBy { pair ->
            pair.uniqueId()
        }
    }
}

@Serializable
data class LanguageModelFilesMetadata(
    val tokenizer: LanguageModelTokenizerFilesMetadata,
    val inference: LanguageModelInferenceFilesMetadata
) {
    fun isValid(path: Path) = tokenizer.isValid(path) &&
            inference.isValid(path)
}

@Serializable
data class LanguageModelTokenizerFilesMetadata(
    val config: String,
    @SerialName("vocabulary")
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

@Serializable
data class LanguageModel(
    val bundle: LanguageBundleMetadata,
    val languages: List<LanguageModelMetadata>
)