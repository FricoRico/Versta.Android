package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class VoiceModelArchitecture(val value: String) {
    Kokoro("Kokoro"),
    StyleTTS2("StyleTTS2")
}

@Serializable
class VoiceModelMetadata(
    val version: String = "",
    @SerialName("base_model") val baseModel: String = "",
    val architectures: List<VoiceModelArchitecture> = emptyList(),
    val files: VoiceModelFilesMetadata,
    var root: Path? = null,
) {
    fun isValid() = baseModel.isNotBlank() && architectures.isNotEmpty() && (root != null && files.isValid(root!!)) && root?.isAbsolute == true

    fun setRootPath(path: Path): VoiceModelMetadata {
        root = path

        return this
    }
}

@Serializable
data class VoiceModelFilesMetadata(
    val inference: VoiceInferenceFilesMetadata,
    val tokenizer: VoiceTokenizerFilesMetadata,
    val voices: List<String> = emptyList()
) {
    fun isValid(path: Path) =
        inference.isValid(path) && tokenizer.isValid(path) && voices.all {
            path.resolve(it).exists()
        }
}

@Serializable
data class VoiceInferenceFilesMetadata(
    val model: String,
) {
    fun isValid(path: Path) = path.resolve(model).exists()
}

@Serializable
data class VoiceTokenizerFilesMetadata(
    val vocabulary: String,
) {
    fun isValid(path: Path) = path.resolve(vocabulary).exists()
}

@Serializable
data class VoiceMetadataFile(
    val directory: String,
)

@Serializable
class VoiceBundleMetadata(
    val id: String,
    val version: String,
    val metadata: VoiceMetadataFile,
) {
    fun isValid() = metadata.directory.isNotEmpty()
}

@Serializable
data class VoiceModel(
    val bundle: VoiceBundleMetadata,
    val model: VoiceModelMetadata
) {
    val id: String
        get() = bundle.id
}