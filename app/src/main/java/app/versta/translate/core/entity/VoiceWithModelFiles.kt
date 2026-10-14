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
    val voices: VoiceModelVoiceFiles,
) {
    val isSupertonic3: Boolean
        get() = architectures.contains(VoiceModelArchitecture.Supertonic3)

    fun isValid() =
        inference.isValid() &&
            tokenizer.isValid() &&
            voices.isValid()

    /**
     * Resolves the voice file for [language] and [gender]. Supertonic 3 voices
     * resolve to the fixed default style of the language; legacy bundles match
     * on Kokoro file naming.
     */
    fun getVoice(
        language: Language,
        gender: VoiceGender = VoiceGender.Female,
    ): Path? =
        if (isSupertonic3) {
            if (!Supertonic3.supports(language)) {
                return null
            }

            val style = path.resolve(Supertonic3.styleFileName(Supertonic3.defaultStyle(language, gender)))
            voices.find { it == style }
        } else {
            voices.getVoiceByLanguage(language, gender)
        }

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(
            id: String,
            path: Path,
        ): VoiceWithModelFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Voice model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<VoiceModelMetadata>(metadataFile.readText())

            val files =
                VoiceWithModelFiles(
                    id = id,
                    path = path,
                    baseModel = metadata.baseModel,
                    architectures = metadata.architectures,
                    version = metadata.version,
                    size = path.parent.directorySize(),
                    inference =
                        VoiceModelInferenceFiles(
                            model =
                                metadata.files.inference.model
                                    .takeIf { it.isNotBlank() }
                                    ?.let { path.resolve(it) },
                            models =
                                metadata.files.inference.models
                                    .mapValues { path.resolve(it.value) },
                            config =
                                metadata.files.inference.config
                                    .takeIf { it.isNotBlank() }
                                    ?.let { path.resolve(it) },
                        ),
                    tokenizer =
                        VoiceModelTokenizerFiles(
                            vocabulary =
                                metadata.files.tokenizer.vocabulary
                                    .takeIf { it.isNotBlank() }
                                    ?.let { path.resolve(it) },
                            unicodeIndexer =
                                metadata.files.tokenizer.unicodeIndexer
                                    .takeIf { it.isNotBlank() }
                                    ?.let { path.resolve(it) },
                        ),
                    voices =
                        VoiceModelVoiceFiles().apply {
                            addAll(
                                metadata.files.voices.map {
                                    path.resolve(it)
                                },
                            )
                        },
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
    val model: Path? = null,
    val models: Map<String, Path> = emptyMap(),
    val config: Path? = null,
) {
    fun isValid() =
        if (models.isEmpty()) {
            model?.exists() == true
        } else {
            config?.exists() == true && models.values.all { it.exists() }
        }
}

@Serializable
data class VoiceModelTokenizerFiles(
    val vocabulary: Path? = null,
    val unicodeIndexer: Path? = null,
) {
    fun isValid() = vocabulary?.exists() == true || unicodeIndexer?.exists() == true
}

@Serializable
class VoiceModelVoiceFiles : ArrayList<Path>() {
    fun isValid() = all { it.exists() }

    fun languages(): Map<Path, Language> =
        associate {
            val file = it.fileName.toString()

            it to
                when {
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

    fun genders(): Map<Path, VoiceGender> =
        associate {
            val file = it.fileName.toString()

            it to
                when {
                    file.startsWith("f", 1) -> VoiceGender.Female
                    file.startsWith("m", 1) -> VoiceGender.Male
                    else -> null
                }
        }.filterValues { it != null }.mapValues { it.value!! }

    fun getVoiceByLanguage(
        language: Language,
        gender: VoiceGender = VoiceGender.Female,
    ): Path? =
        find {
            languages()[it] == language && genders()[it] == gender
        } ?: find {
            languages()[it] == language
        }
}
