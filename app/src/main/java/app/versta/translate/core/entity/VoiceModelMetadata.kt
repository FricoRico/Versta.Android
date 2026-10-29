package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

enum class VoiceModelArchitecture(
    val value: String,
) {
    Supertonic3("Supertonic3"),
}

@Serializable
class VoiceModelMetadata(
    val version: String = "",
    @SerialName("base_model") val baseModel: String = "",
    val architectures: List<VoiceModelArchitecture> = emptyList(),
    val files: VoiceModelFilesMetadata,
    var root: Path? = null,
) {
    fun isValid() =
        baseModel.isNotBlank() &&
            architectures.isNotEmpty() &&
            (root != null && files.isValid(root!!)) &&
            root?.isAbsolute == true &&
            (!architectures.contains(VoiceModelArchitecture.Supertonic3) || files.inference.models.keys == Supertonic3.MODEL_STAGES)

    fun setRootPath(path: Path): VoiceModelMetadata {
        root = path

        return this
    }
}

@Serializable
data class VoiceModelFilesMetadata(
    val inference: VoiceInferenceFilesMetadata,
    val tokenizer: VoiceTokenizerFilesMetadata,
    val voices: List<String> = emptyList(),
) {
    fun isValid(path: Path) =
        inference.isValid(path) && tokenizer.isValid(path) &&
            voices.all {
                path.resolve(it).exists()
            }
}

/**
 * Inference files of a Supertonic 3 voice model bundle: the four-model set
 * (`models` keyed by stage) plus the `tts.json` engine [config].
 */
@Serializable
data class VoiceInferenceFilesMetadata(
    val models: Map<String, String> = emptyMap(),
    val config: String = "",
) {
    fun isValid(path: Path) =
        config.isNotBlank() &&
            path.resolve(config).exists() &&
            models.values.all { path.resolve(it).exists() }
}

/**
 * Tokenizer files of a voice model bundle. Supertonic 3 bundles carry the
 * [unicodeIndexer] table; no phonemizer vocabulary is needed.
 */
@Serializable
data class VoiceTokenizerFilesMetadata(
    @SerialName("unicode_indexer") val unicodeIndexer: String = "",
) {
    fun isValid(path: Path) = unicodeIndexer.isNotBlank() && path.resolve(unicodeIndexer).exists()
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
    val model: VoiceModelMetadata,
) {
    val id: String
        get() = bundle.id
}
