package app.versta.translate.core.entity

import app.versta.translate.core.model.DownloadTask
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
data class ExternalDataDefinition(
    val id: String,
    val size: Long,
    val type: DataType,
    var extracted: Long? = null,
    val version: String,
    private val bundle: String,
    private val checksum: String,
) {
    fun bundleUri() = URI(bundle)
    fun checksumUri() = URI(checksum)

    fun isValid() = bundle.isNotBlank()
            && checksum.isNotBlank()
            && size > 0
            && version.isNotBlank()
}

typealias ExternalDataDefinitions = List<ExternalDataDefinition>

fun ExternalDataDefinitions.isValid(): Boolean {
    if (this.isEmpty()) return true

    return this.all {
        it.isValid()
    }
}

data class ExternalData(
    val installed: List<ExternalDataDefinition> = emptyList(),
    val updates: List<ExternalDataDefinition> = emptyList(),
    val available: List<ExternalDataDefinition> = emptyList(),
)

data class ExternalDataDownloadTask(
    override val id: UUID = UUID.randomUUID(),
    val downloadName: String,
    val definition: ExternalDataDefinition,
    override val status: DownloadStatus,
    val onComplete: (ExternalDataDefinition) -> Unit = {}
) : DownloadTask {

    override fun copyWithStatus(status: DownloadStatus): DownloadTask {
        return copy(status = status)
    }

    override fun getWorkData(): Map<String, Any> {
        return mapOf(
            "taskId" to id.toString(),
            "name" to downloadName,
            "uri" to definition.bundleUri().toString(),
            "checksum" to definition.checksumUri().toString()
        )
    }

    override fun getName(): String {
        return downloadName
    }

    override fun onComplete() {
        onComplete(definition)
    }
}
