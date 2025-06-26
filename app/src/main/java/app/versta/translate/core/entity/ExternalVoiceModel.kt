package app.versta.translate.core.entity

import app.versta.translate.core.model.DownloadTask
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable
data class ExternalVoiceModelDefinition (
    val id: String,
    val name: String,
    @SerialName("base_model")
    val baseModel: String,
    val size: Long,
    var extracted: Long? = null,
    val version: String,
    val voices: List<ExternalVoice> = emptyList(),
    val architectures: List<VoiceModelArchitecture> = emptyList(),
    private val bundle: String,
    private val checksum: String,
) {
    fun bundleUri() = URI(bundle)
    fun checksumUri() = URI(checksum)

    fun isValid() = baseModel.isNotBlank()
            && bundle.isNotBlank()
            && checksum.isNotBlank()
            && size > 0
            && version.isNotBlank()
}

typealias ExternalVoiceModelDefinitions = List<ExternalVoiceModelDefinition>

fun ExternalVoiceModelDefinitions.isValid(): Boolean {
    if (this.isEmpty()) return true

    return this.all {
        it.isValid()
    }
}

@Serializable
data class ExternalVoice (
    val gender: VoiceGender,
    val language: String,
)

data class ExternalVoiceModels(
    val installed: List<ExternalVoiceModelDefinition> = emptyList(),
    val updates: List<ExternalVoiceModelDefinition> = emptyList(),
    val available: List<ExternalVoiceModelDefinition> = emptyList(),
)

data class ExternalVoiceDownloadTask(
    override val id: UUID = UUID.randomUUID(),
    val model: ExternalVoiceModelDefinition,
    override val status: DownloadStatus
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
}

data class ExternalVoiceLanguageVoiceGenders(
    val language: Language,
    val genders: List<VoiceGender>
)