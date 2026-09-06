package app.versta.translate.core.entity

import kotlinx.serialization.Serializable

enum class ObjectCharacterRecognitionArchitecture(val value: String) {
    PaddleOCR("PaddleOCR")
}

@Serializable
data class ObjectCharacterRecognitionMetadataFile(
    val directory: String,
    val languages: List<String>,
    val module: ObjectCharacterRecognitionModule,
)

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

/**
 * One installed OCR module directory as recorded in the database: the bundle
 * manifest provides identity, the module manifest provides the files.
 */
@Serializable
data class ObjectCharacterRecognitionModuleModel(
    val bundle: ObjectCharacterRecognitionBundleMetadata,
    val model: ObjectCharacterRecognitionModuleMetadata,
    val directory: String,
    @kotlinx.serialization.Transient
    var root: java.nio.file.Path? = null,
) {
    val id: String
        get() = "${bundle.id}:$directory"
}
