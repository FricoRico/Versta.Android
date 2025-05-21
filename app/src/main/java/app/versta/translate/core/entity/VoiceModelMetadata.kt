package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.collections.isNotEmpty
import kotlin.io.path.exists

enum class VoiceModelArchitecture(val value: String) {
    Kokoro("Kokoro"),
    StyleTTS2("StyleTTS2")
}

@Serializable
class VoiceModelMetadata(
    val version: String = "",
    @SerialName("base_model") val baseModel: String,
    val architectures: List<VoiceModelArchitecture>,
    val files: VoiceModelFilesMetadata,
    var root: Path? = null
) {
    fun isValid() =
        baseModel.isNotBlank() && architectures.isNotEmpty() && (root != null && files.isValid(root!!)) && root?.isAbsolute == true

    fun setRootPath(path: Path): VoiceModelMetadata {
        root = path

        return this
    }
}

@Serializable
data class VoiceMetadata(
    val directory: String,
)

@Serializable
class VoiceBundleMetadata(
    val id: String,
    val version: String,
    val metadata: VoiceMetadata,
) {
    fun isValid() = metadata.directory.isNotEmpty()
}

@Serializable
data class VoiceModelFilesMetadata(
    val inference: VoiceInferenceFilesMetadata, val voices: List<String>
) {
    fun isValid(path: Path) = inference.isValid(path) && voices.all { path.resolve(it).exists() }
}

@Serializable
data class VoiceInferenceFilesMetadata(
    val model: String,
) {
    fun isValid(path: Path) = path.resolve(model).exists()
}

@Serializable
data class VoiceModel(
    val bundle: VoiceBundleMetadata,
    val model: VoiceModelMetadata
) {
    val id: String
        get() = bundle.id
}