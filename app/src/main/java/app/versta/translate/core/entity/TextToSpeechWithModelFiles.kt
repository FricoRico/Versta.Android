package app.versta.translate.core.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class TextToSpeechModelFiles(
    val path: Path,
    val baseModel: String,
    val architectures: List<TextToSpeechModelArchitecture>,
    val version: String,
    val inference: TextToSpeechInferenceFiles,
    val voices: TextToSpeechVoicesFiles
) {
    fun isValid() = inference.isValid() &&
            voices.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(path: Path): TextToSpeechModelFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Text-to-speech model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<TextToSpeechModelMetadata>(metadataFile.readText())
            val files = TextToSpeechModelFiles(
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                version = metadata.version,
                inference = TextToSpeechInferenceFiles(
                    model = path.resolve(metadata.files.inference.model)
                ),
                voices = TextToSpeechVoicesFiles().apply {
                    addAll(metadata.files.voices.map {
                        path.resolve(it)
                    })
                }
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Text-to-speech model files are not complete and valid: $files")
            }

            return files
        }
    }
}

@Serializable
data class TextToSpeechInferenceFiles(
    val model: Path,
) {
    fun isValid() = model.exists()
}

@Serializable
class TextToSpeechVoicesFiles : ArrayList<Path>() {
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
                file.startsWith("f", 1) -> VoiceGender.FEMALE
                file.startsWith("m", 1) -> VoiceGender.MALE
                else -> null
            }
        }.filterValues { it != null }.mapValues { it.value!! }
    }

    fun getVoiceByLanguage(language: Language, gender: VoiceGender = VoiceGender.FEMALE): Path? {
        return find {
            languages()[it] == language && genders()[it] == gender
        } ?: find {
            languages()[it] == language
        }
    }
}
