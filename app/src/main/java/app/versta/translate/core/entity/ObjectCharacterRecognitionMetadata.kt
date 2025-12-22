package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class ObjectCharacterRecognitionArchitecture(val value: String) {
    PaddleOCR("PaddleOCR")
}

@Serializable
enum class ObjectCharacterRecognitionModule {
    @SerialName("detector")
    Detector,

    @SerialName("recognizer")
    Recognizer
}

@Serializable
class ObjectCharacterRecognitionDetectorMetadata(
    val id: String = "",
    val version: String = "",
    @SerialName("base_model") val baseModel: String = "",
    val languages: List<String>,
    val architectures: List<ObjectCharacterRecognitionArchitecture> = emptyList(),
    val files: ObjectCharacterRecognitionDetectorFilesMetadata,
    var root: Path? = null,
) {
    fun isValid() =
        baseModel.isNotBlank() && architectures.isNotEmpty() && (root != null && files.isValid(root!!)) && root?.isAbsolute == true

    fun setRootPath(path: Path): ObjectCharacterRecognitionDetectorMetadata {
        root = path

        return this
    }
}

@Serializable
data class ObjectCharacterRecognitionDetectorFilesMetadata(
    val inference: ObjectCharacterRecognitionDetectorInferenceFilesMetadata,
) {
    fun isValid(path: Path) = inference.isValid(path)
}

@Serializable
data class ObjectCharacterRecognitionDetectorInferenceFilesMetadata(
    val model: String,
) {
    fun isValid(path: Path) = path.resolve(model).exists()
}

@Serializable
data class ObjectCharacterRecognitionMetadataFile(
    val directory: String,
    val languages: List<String>,
    val module: ObjectCharacterRecognitionModule,
)

@Serializable
class ObjectCharacterRecognitionRecognizerMetadata(
    val id: String = "",
    val version: String = "",
    @SerialName("base_model") val baseModel: String = "",
    val languages: List<String>,
    val architectures: List<ObjectCharacterRecognitionArchitecture> = emptyList(),
    val files: ObjectCharacterRecognitionRecognitionRecognizerFilesMetadata,
    var root: Path? = null,
) {
    fun isValid() =
        baseModel.isNotBlank() && architectures.isNotEmpty() && (root != null && files.isValid(root!!)) && root?.isAbsolute == true

    fun setRootPath(path: Path): ObjectCharacterRecognitionRecognizerMetadata {
        root = path

        return this
    }
}

@Serializable
data class ObjectCharacterRecognitionRecognitionRecognizerFilesMetadata(
    val inference: ObjectCharacterRecognitionRecognizerInferenceFilesMetadata,
    val tokenizer: ObjectCharacterRecognitionRecognizerTokenizerFilesMetadata,
) {
    fun isValid(path: Path) =
        inference.isValid(path) && tokenizer.isValid(path)
}

@Serializable
data class ObjectCharacterRecognitionRecognizerInferenceFilesMetadata(
    val model: String,
) {
    fun isValid(path: Path) = path.resolve(model).exists()
}

@Serializable
data class ObjectCharacterRecognitionRecognizerTokenizerFilesMetadata(
    val vocabulary: String,
) {
    fun isValid(path: Path) = path.resolve(vocabulary).exists()
}

@Serializable
class ObjectCharacterRecognitionBundleMetadata(
    val id: String,
    val version: String,
    val languages: List<String>,
    val modules: List<String>,
    val metadata: List<ObjectCharacterRecognitionMetadataFile>,
) {
    fun isValid() =
        metadata.all { it.directory.isNotEmpty() } &&
        modules.isNotEmpty()
}

@Serializable
data class ObjectCharacterRecognitionDetectorModel(
    val bundle: ObjectCharacterRecognitionBundleMetadata,
    val model: ObjectCharacterRecognitionDetectorMetadata
) {
    val id: String
        get() = bundle.id
}

@Serializable
data class ObjectCharacterRecognitionRecognizerModel(
    val bundle: ObjectCharacterRecognitionBundleMetadata,
    val model: ObjectCharacterRecognitionRecognizerMetadata
) {
    val id: String
        get() = bundle.id
}
