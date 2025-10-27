package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class ObjectCharacterRecognitionArchitecture(val value: String) {
    PaddleOCR("PaddleOCR")
}

enum class ObjectCharacterRecognitionModel(val value: String) {
    Detector("detector"),
    Recognizer("recognizer")
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
data class ObjectCharacterRecognitionDetectorMetadataFile(
    val directory: String,
)

@Serializable
class ObjectCharacterRecognitionDetectorBundleMetadata(
    val id: String,
    val version: String,
    val metadata: ObjectCharacterRecognitionDetectorMetadataFile,
) {
    fun isValid() = metadata.directory.isNotEmpty()
}

@Serializable
data class ObjectCharacterRecognitionDetectorModel(
    val bundle: ObjectCharacterRecognitionDetectorBundleMetadata,
    val model: ObjectCharacterRecognitionDetectorMetadata
) {
    val id: String
        get() = bundle.id
}

@Serializable
class ObjectCharacterRecognitionRecognizerMetadata(
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
data class ObjectCharacterRecognitionRecognizerMetadataFile(
    val directory: String,
)

@Serializable
class ObjectCharacterRecognitionRecognizerBundleMetadata(
    val id: String,
    val version: String,
    val metadata: ObjectCharacterRecognitionRecognizerMetadataFile,
) {
    fun isValid() = metadata.directory.isNotEmpty()
}

@Serializable
data class ObjectCharacterRecognitionRecognizerModel(
    val bundle: ObjectCharacterRecognitionRecognizerBundleMetadata,
    val model: ObjectCharacterRecognitionRecognizerMetadata
) {
    val id: String
        get() = bundle.id
}