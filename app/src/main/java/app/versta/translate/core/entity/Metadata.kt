package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path

enum class ModelArchitecture(val value: String) {
    MarianMTModel("MarianMTModel")
}

@Serializable
class BundleMetadata(
    @SerialName("language_pairs")
    val isLanguagePair: Boolean,
    val metadata: List<LanguageModel>,
    private val languages: List<String>
) {
    fun isValid() =
        languages.isNotEmpty()
            && (if (isLanguagePair) languages.size % 2 == 0 else true)
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
    @SerialName("base_model")
    val baseModel: String,
    @SerialName("source_language")
    val sourceLanguage: String,
    @SerialName("target_language")
    val targetLanguage: String,
    val files: LanguageModelFilesMap,
    val architectures: List<ModelArchitecture>,
    var root: Path? = null
) {
    fun isValid() = baseModel.isNotBlank()
            && sourceLanguage.isNotBlank()
            && architectures.isNotEmpty()
            && files.isNotEmpty()
            && root?.isAbsolute ?: false

    fun setRootPath(path: Path): LanguageMetadata {
        root = path

        return this
    }
}

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

