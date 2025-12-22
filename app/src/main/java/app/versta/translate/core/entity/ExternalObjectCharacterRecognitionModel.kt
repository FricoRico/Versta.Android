package app.versta.translate.core.entity

import app.versta.translate.core.model.DownloadTask
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
data class ExternalObjectCharacterRecognitionModelDefinition(
    val id: String,
    val name: String,
    @SerialName("base_model")
    val baseModel: String,
    val architectures: List<ObjectCharacterRecognitionArchitecture>,
    val languages: List<String>,
    val size: Long,
    val version: String,
    private val bundle: String,
    private val checksum: String,
) {
    fun bundleUri() = URI(bundle)
    fun checksumUri() = URI(checksum)

    fun isValid() = id.isNotBlank()
            && name.isNotBlank()
            && baseModel.isNotBlank()
            && bundle.isNotBlank()
            && checksum.isNotBlank()
            && size > 0
            && version.isNotBlank()
            && languages.isNotEmpty()
            && architectures.isNotEmpty()
}

data class ExternalObjectCharacterRecognitionModelWithState(
    val definition: ExternalObjectCharacterRecognitionModelDefinition,
    var extracted: Long? = null,
)

data class ExternalObjectCharacterRecognitionModels(
    val installed: List<ExternalObjectCharacterRecognitionModelWithState> = emptyList(),
    val updates: List<ExternalObjectCharacterRecognitionModelWithState> = emptyList(),
    val available: List<ExternalObjectCharacterRecognitionModelWithState> = emptyList()
)

data class ExternalObjectCharacterRecognitionDownloadTask(
    override val id: UUID = UUID.randomUUID(),
    val model: ExternalObjectCharacterRecognitionModelDefinition,
    override val status: DownloadStatus,
    val onComplete: (ExternalObjectCharacterRecognitionModelDefinition) -> Unit = {},
) : DownloadTask {

    override fun copyWithStatus(status: DownloadStatus): DownloadTask {
        return copy(status = status)
    }

    override fun getWorkData(): Map<String, Any> {
        return mapOf(
            "taskId" to id.toString(),
            "name" to model.name,
            "uri" to model.bundleUri().toString(),
            "checksum" to model.checksumUri().toString()
        )
    }

    override fun getName(): String {
        return model.name
    }

    override fun onComplete() {
        onComplete(model)
    }
}

