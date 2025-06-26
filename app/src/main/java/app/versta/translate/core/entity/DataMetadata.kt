package app.versta.translate.core.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

const val DataTypeTextToSpeech = "tts"

enum class DataType(val value: String) {
    TTS(DataTypeTextToSpeech)
}

internal interface DataMetadataInterface {
    fun isValid(): Boolean
}

@Serializable
sealed class DataMetadata : DataMetadataInterface {
    abstract val id: String
    abstract val version: String
    abstract val type: String
    var root: Path? = null

    fun setRootPath(path: Path): DataMetadata {
        root = path

        return this
    }
}

@Serializable
@SerialName(DataTypeTextToSpeech)
class TextToSpeechDataMetadata(
    override val id: String,
    override val type: String = DataTypeTextToSpeech,
    override val version: String = "",
    val files: TextToSpeechDataFilesMetadata,
) : DataMetadata() {
    override fun isValid() = (root != null && files.isValid(root!!)) && root?.isAbsolute == true
}

@Serializable
data class TextToSpeechDataMetadataFile(
    val directory: String,
)

@Serializable
class DataBundleMetadata(
    val id: String,
    val version: String,
    val metadata: TextToSpeechDataMetadataFile,
) {
    fun isValid() = metadata.directory.isNotEmpty()
}

@Serializable
data class TextToSpeechDataFilesMetadata(
    val espeak: String,
    @SerialName("open_jtalk")
    val openJTalk: String,
) {
    fun isValid(path: Path) =
        path.resolve(espeak).exists() && path.resolve(openJTalk).exists()
}

@Serializable
data class DataModel(
    val bundle: DataBundleMetadata,
    val contents: DataMetadata
) {
    val id: String
        get() = bundle.id
}