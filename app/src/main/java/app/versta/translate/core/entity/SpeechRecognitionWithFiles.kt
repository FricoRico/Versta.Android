package app.versta.translate.core.entity

import app.versta.translate.utils.directorySize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * On-device speech recognition model bundle: a whisper.cpp GGML model plus the
 * Silero VAD model used for speech segmentation. Loaded from a directory
 * containing a `metadata.json` describing the two model files.
 */
@Serializable
data class SpeechRecognitionWithFiles(
    val id: String,
    val path: Path,
    val baseModel: String,
    val architectures: List<SpeechRecognitionArchitecture>,
    val languages: List<String>,
    val version: String,
    val size: Long = 0,
    val inference: SpeechRecognitionInferenceFiles,
) {
    fun isValid() = inference.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(id: String, path: Path): SpeechRecognitionWithFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException(
                    "Speech recognition model metadata file not found: ${metadataFile.absolutePath}",
                )
            }

            val metadata =
                serializer.decodeFromString<SpeechRecognitionMetadata>(metadataFile.readText())
                    .setRootPath(path)

            if (!metadata.isValid()) {
                throw IllegalArgumentException(
                    "Speech recognition model metadata is not complete and valid: $metadata",
                )
            }

            val files = SpeechRecognitionWithFiles(
                id = id,
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                languages = metadata.languages,
                version = metadata.version,
                size = path.parent.directorySize(),
                inference = SpeechRecognitionInferenceFiles(
                    model = path.resolve(metadata.files.inference.model),
                    vad = path.resolve(metadata.files.inference.vad),
                ),
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Speech recognition model files are not complete and valid: $files")
            }

            return files
        }
    }
}

@Serializable
data class SpeechRecognitionInferenceFiles(
    val model: Path,
    val vad: Path,
) {
    fun isValid(): Boolean {
        return model.exists() && vad.exists()
    }
}
