package app.versta.translate.core.entity

import app.versta.translate.core.model.DownloadTask
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

const val LANGUAGE_RATING_THRESHOLD = 70

@Serializable
data class ExternalLanguageModelDefinition(
    @SerialName("base_model")
    val baseModel: String,
    @SerialName("source_language")
    val sourceLanguage: String,
    @SerialName("target_language")
    val targetLanguage: String,
    val bidirectional: Boolean,
    val architectures: List<LanguageModelArchitecture>,
    val score: Double,
    val size: Long,
    val version: String,
    private val bundle: String,
    private val checksum: String,
) {
    fun bundleUri() = URI(bundle)
    fun checksumUri() = URI(checksum)

    fun pair() = LanguagePair.fromIsoCodes(
        sourceLanguage,
        targetLanguage
    )

    fun isValid() = baseModel.isNotBlank()
            && sourceLanguage.isNotBlank()
            && targetLanguage.isNotBlank()
            && bundle.isNotBlank()
            && checksum.isNotBlank()
            && size > 0
            && version.isNotBlank()
}

typealias ExternalLanguageModelDefinitions = List<ExternalLanguageModelDefinition>

fun ExternalLanguageModelDefinitions.isValid(): Boolean {
    if (this.isEmpty()) return true

    val bidirectional = this.all {
        it.bidirectional
    }

    if (bidirectional) {
        if (this.size != 2) return false

        val a = this[0]
        val b = this[1]

        return a.sourceLanguage == b.targetLanguage &&
                a.targetLanguage == b.sourceLanguage &&
                a.bidirectional == b.bidirectional &&
                a.size == b.size &&
                a.version == b.version &&
                a.bundleUri() == b.bundleUri() &&
                a.checksumUri() == b.checksumUri()
    }

    return this.all {
        it.isValid()
    }
}

data class ExternalLanguageMetadata(
    val baseModel: String,
    val source: Language,
    val target: Language,
    val score: Double,
    val architectures: List<LanguageModelArchitecture>,
)

data class ExternalLanguagePairDefinition(
    val pair: LanguagePair,
    val bidirectional: Boolean,
    val metadata: List<ExternalLanguageMetadata>,
    val size: Long,
    var extracted: Long? = null,
    val version: String,
    val bundleUri: URI,
    val checksumUri: URI,
)

data class ExternalLanguageModels(
    val installed: List<ExternalLanguagePairDefinition> = emptyList(),
    val updates: List<ExternalLanguagePairDefinition> = emptyList(),
    val available: List<ExternalLanguagePairDefinition> = emptyList()
)

data class ExternalLanguageDownloadTask(
    override val id: UUID = UUID.randomUUID(),
    val model: ExternalLanguagePairDefinition,
    override val status: DownloadStatus,
    val onComplete: (ExternalLanguagePairDefinition) -> Unit = {},
) : DownloadTask {

    override fun copyWithStatus(status: DownloadStatus): DownloadTask {
        return copy(status = status)
    }

    override fun getWorkData(): Map<String, Any> {
        return mapOf(
            "taskId" to id.toString(),
            "name" to "${model.pair.source.name} - ${model.pair.target.name}",
            "uri" to model.bundleUri.toString(),
            "checksum" to model.checksumUri.toString()
        )
    }

    override fun getName(): String {
        return "${model.pair.source.name} - ${model.pair.target.name}"
    }

    override fun onComplete() {
        onComplete(model)
    }
}
