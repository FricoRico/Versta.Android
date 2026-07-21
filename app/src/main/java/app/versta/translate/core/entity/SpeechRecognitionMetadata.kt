package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class SpeechRecognitionArchitecture(val value: String) {
    Whisper("Whisper"),
}

@Serializable
enum class SpeechRecognitionModule {
    @SerialName("recognition")
    Recognition,
}

@Serializable
class SpeechRecognitionMetadata(
    val id: String = "",
    val version: String = "",
    @SerialName("base_model") val baseModel: String = "",
    val languages: List<String>,
    val architectures: List<SpeechRecognitionArchitecture> = emptyList(),
    val files: SpeechRecognitionFilesMetadata,
    var root: Path? = null,
) {
    fun isValid() =
        baseModel.isNotBlank() && architectures.isNotEmpty() &&
            (root?.let { it.isAbsolute && files.isValid(it) } == true)

    fun setRootPath(path: Path): SpeechRecognitionMetadata {
        root = path

        return this
    }
}

@Serializable
data class SpeechRecognitionFilesMetadata(
    val inference: SpeechRecognitionInferenceFilesMetadata,
) {
    fun isValid(path: Path) = inference.isValid(path)
}

@Serializable
data class SpeechRecognitionInferenceFilesMetadata(
    val model: String,
    val vad: String,
) {
    fun isValid(path: Path) = path.resolve(model).exists() && path.resolve(vad).exists()
}

@Serializable
data class SpeechRecognitionMetadataFile(
    val directory: String,
    val languages: List<String>,
    val module: SpeechRecognitionModule,
)

@Serializable
class SpeechRecognitionBundleMetadata(
    val id: String,
    val version: String,
    val languages: List<String>,
    val modules: List<String>,
    val metadata: List<SpeechRecognitionMetadataFile>,
) {
    fun isValid() =
        metadata.all { it.directory.isNotEmpty() } &&
            modules.isNotEmpty()
}

@Serializable
data class SpeechRecognitionModel(
    val bundle: SpeechRecognitionBundleMetadata,
    val model: SpeechRecognitionMetadata,
) {
    val id: String
        get() = bundle.id
}
