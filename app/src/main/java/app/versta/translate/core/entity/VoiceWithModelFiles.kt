package app.versta.translate.core.entity

import app.versta.translate.utils.directorySize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class VoiceWithModelFiles(
    val id: String,
    val path: Path,
    val baseModel: String,
    val architectures: List<VoiceModelArchitecture>,
    val version: String,
    val size: Long = 0,
    val inference: VoiceModelInferenceFiles,
    val tokenizer: VoiceModelTokenizerFiles,
    val voices: VoiceModelVoiceFiles
) {
    fun isValid() = inference.isValid() &&
            voices.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(id: String, path: Path): VoiceWithModelFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Voice model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<VoiceModelMetadata>(metadataFile.readText())

            val files = VoiceWithModelFiles(
                id = id,
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                version = metadata.version,
                size = path.parent.directorySize(),
                inference = VoiceModelInferenceFiles(
                    model = path.resolve(metadata.files.inference.model)
                ),
                tokenizer = VoiceModelTokenizerFiles(
                    vocabulary = path.resolve(metadata.files.tokenizer.vocabulary)
                ),
                voices = VoiceModelVoiceFiles().apply {
                    addAll(metadata.files.voices.map {
                        path.resolve(it)
                    })
                }
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Voice model files are not complete and valid: $files")
            }

            return files
        }
    }
}

@Serializable
data class VoiceModelInferenceFiles(
    val model: Path,
) {
    fun isValid() = model.exists()
}

@Serializable
data class VoiceModelTokenizerFiles(
    val vocabulary: Path,
) {
    fun isValid() = vocabulary.exists()
}

@Serializable
class VoiceModelVoiceFiles : ArrayList<Path>() {
    fun isValid() = all { it.exists() }

    fun languages(): Map<Path, Language> {
        return associate {
            val file = it.fileName.toString()

            it to when {
                file.startsWith("a") -> Language.fromIsoCode("en")
                file.startsWith("b") -> Language.fromIsoCode("en")
                file.startsWith("j") -> Language.fromIsoCode("ja")
                file.startsWith("f") -> Language.fromIsoCode("fr")
                file.startsWith("e") -> Language.fromIsoCode("es")
                file.startsWith("h") -> Language.fromIsoCode("hi")
                file.startsWith("i") -> Language.fromIsoCode("it")
                file.startsWith("z") -> Language.fromIsoCode("zh")
                file.startsWith("p") -> Language.fromIsoCode("pt")
                else -> null
            }
        }.filterValues { it != null }.mapValues { it.value!! }
    }

    fun genders(): Map<Path, VoiceGender> {
        return associate {
            val file = it.fileName.toString()

            it to when {
                file.startsWith("f", 1) -> VoiceGender.Female
                file.startsWith("m", 1) -> VoiceGender.Male
                else -> null
            }
        }.filterValues { it != null }.mapValues { it.value!! }
    }

    fun getVoiceByLanguage(language: Language, gender: VoiceGender = VoiceGender.Female): Path? {
        return find {
            languages()[it] == language && genders()[it] == gender
        } ?: find {
            languages()[it] == language
        }
    }
}
