package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class LanguageModelArchitecture(val value: String) {
    MarianMTModel("MarianMTModel"),
}

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
    val files: LanguageModelFilesMetadata,
    val config: LanguageModelConfigurationMetadata,
    var root: Path? = null
) {
    fun isValid() = baseModel.isNotBlank()
            && sourceLanguage.isNotBlank()
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
    val model: String,
    @SerialName("vocabulary")
    val vocabulary: String,
    @SerialName("target_vocabulary")
    val targetVocabulary: String? = null,
    val shortlist: String,
) {
    fun isValid(path: Path) = path.resolve(model).exists() &&
            path.resolve(vocabulary).exists() &&
            targetVocabulary?.let { path.resolve(it).exists() } ?: true &&
            path.resolve(shortlist).exists()
}

@Serializable
data class LanguageModelConfigurationMetadata(
    @SerialName("encoder_layers")
    val encoderLayers: Long,
    @SerialName("decoder_layers")
    val decoderLayers: Long,
    @SerialName("ffn_depth")
    val ffnDepth: Long = 2,
    @SerialName("num_heads")
    val numHeads: Long,
    @SerialName("split_mode")
    val splitMode: String = "sentence"
) {
    fun isValid() = encoderLayers > 0 && decoderLayers > 0 &&
            ffnDepth > 0 && numHeads > 0
}

@Serializable
data class LanguageBundleData(
    val bundle: LanguageBundleMetadata,
    val languages: List<LanguageModelMetadata>
)