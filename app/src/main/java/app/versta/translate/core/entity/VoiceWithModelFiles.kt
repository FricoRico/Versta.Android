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
            voices.isValid() &&
            (!isSupertonic3 || inference.models.keys == Supertonic3.MODEL_STAGES)

    /**
     * Resolves the voice style file for [language] and [gender]. Supertonic 3
     * voices resolve to the fixed default style of the language; languages
     * without Supertonic 3 coverage resolve to nothing.
     */
    fun getVoice(
        language: Language,
        gender: VoiceGender = VoiceGender.Female,
    ): Path? {
        if (!isSupertonic3 || !Supertonic3.supports(language)) {
            return null
        }

        val style = path.resolve(Supertonic3.styleFileName(Supertonic3.defaultStyle(language, gender)))
        return voices.find { it == style }
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
    val models: Map<String, Path> = emptyMap(),
    val config: Path? = null,
) {
    fun isValid() = config?.exists() == true && models.values.all { it.exists() }
}

@Serializable
data class VoiceModelTokenizerFiles(
    val unicodeIndexer: Path? = null,
) {
    fun isValid() = unicodeIndexer?.exists() == true
}

@Serializable
class VoiceModelVoiceFiles : ArrayList<Path>() {
    fun isValid() = all { it.exists() }
}
