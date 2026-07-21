package app.versta.translate.core.entity

import app.versta.translate.utils.directorySize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class ObjectCharacterRecognitionDetectorWithFiles(
    val id: String,
    val path: Path,
    val baseModel: String,
    val architectures: List<ObjectCharacterRecognitionArchitecture>,
    val languages: List<String>,
    val version: String,
    val size: Long = 0,
    val inference: ObjectCharacterRecognitionDetectorInferenceFiles,
) {
    fun isValid() = inference.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(id: String, path: Path): ObjectCharacterRecognitionDetectorWithFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Object character recognizer model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<ObjectCharacterRecognitionDetectorMetadata>(metadataFile.readText())

            val files = ObjectCharacterRecognitionDetectorWithFiles(
                id = id,
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                languages = metadata.languages,
                version = metadata.version,
                size = path.parent.directorySize(),
                inference = ObjectCharacterRecognitionDetectorInferenceFiles(
                    model = path.resolve(metadata.files.inference.model)
                ),
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Object character recognizer model files are not complete and valid: $files")
            }

            return files
        }
    }
}

@Serializable
data class ObjectCharacterRecognitionDetectorInferenceFiles(
    val model: Path,
) {
    fun isValid() = model.exists()
}

@Serializable
data class ObjectCharacterRecognitionRecognizerWithFiles(
    val id: String,
    val path: Path,
    val baseModel: String,
    val architectures: List<ObjectCharacterRecognitionArchitecture>,
    val languages: List<String>,
    val version: String,
    val size: Long = 0,
    val inference: ObjectCharacterRecognitionRecognizerInferenceFiles,
    val tokenizer: ObjectCharacterRecognitionRecognizerTokenizerFiles,
) {
    fun isValid() = inference.isValid() && tokenizer.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(id: String, path: Path): ObjectCharacterRecognitionRecognizerWithFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Object character recognizer model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<ObjectCharacterRecognitionRecognizerMetadata>(metadataFile.readText())

            val files = ObjectCharacterRecognitionRecognizerWithFiles(
                id = id,
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                languages = metadata.languages,
                version = metadata.version,
                size = path.parent.directorySize(),
                inference = ObjectCharacterRecognitionRecognizerInferenceFiles(
                    model = path.resolve(metadata.files.inference.model)
                ),
                tokenizer = ObjectCharacterRecognitionRecognizerTokenizerFiles(
                    vocabulary = path.resolve(metadata.files.tokenizer.vocabulary)
                ),
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Object character recognizer model files are not complete and valid: $files")
            }

            return files
        }
    }
}

@Serializable
data class ObjectCharacterRecognitionRecognizerInferenceFiles(
    val model: Path,
) {
    fun isValid() = model.exists()
}

@Serializable
data class ObjectCharacterRecognitionRecognizerTokenizerFiles(
    val vocabulary: Path,
) {
    fun isValid() = vocabulary.exists()
}
