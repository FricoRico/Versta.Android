package app.versta.translate.core.entity

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
    val voices: List<ExternalVoice>,
    val architectures: List<VoiceModelArchitecture>,
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
    val id: UUID = UUID.randomUUID(),
    val model: ExternalVoiceModelDefinition,
    val status: DownloadStatus
)

data class ExternalVoiceLanguageVoiceGenders(
    val language: Language,
    val genders: List<VoiceGender>
)