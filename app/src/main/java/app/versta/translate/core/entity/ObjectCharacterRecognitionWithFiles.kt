package app.versta.translate.core.entity

import app.versta.translate.utils.directorySize
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * A single installed OCR module directory (e.g. `PP-OCRv6_tiny_rec`) with its
 * manifest and resolved file paths on disk.
 */
data class ObjectCharacterRecognitionModuleWithFiles(
    val id: String,
    val bundleId: String,
    val path: Path,
    val module: ObjectCharacterRecognitionModule,
    val languages: List<String>,
    val version: String,
    val size: Long = 0,
    val files: List<ObjectCharacterRecognitionModuleFile>,
) {
    fun isValid() = files.isNotEmpty() && files.all {
        path.resolve(it.inference).exists() && (it.vocab == null || path.resolve(it.vocab).exists())
    }

    fun inferencePath(file: ObjectCharacterRecognitionModuleFile): Path = path.resolve(file.inference)
    fun vocabPath(file: ObjectCharacterRecognitionModuleFile): Path? = file.vocab?.let { path.resolve(it) }

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(id: String, bundleId: String, version: String, path: Path): ObjectCharacterRecognitionModuleWithFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("OCR module metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<ObjectCharacterRecognitionModuleMetadata>(metadataFile.readText())

            val module = ObjectCharacterRecognitionModuleWithFiles(
                id = id,
                bundleId = bundleId,
                path = path,
                module = metadata.module,
                languages = metadata.languages,
                version = version,
                size = path.directorySize(),
                files = metadata.files
            )

            if (!module.isValid()) {
                throw IllegalArgumentException("OCR module files are not complete and valid: $module")
            }

            return module
        }
    }
}

/**
 * An installed OCR bundle (e.g. `paddle-ocr-bundle`) with all its modules.
 */
data class ObjectCharacterRecognitionBundleWithFiles(
    val id: String,
    val path: Path,
    val version: String,
    val languages: List<String>,
    val modules: List<ObjectCharacterRecognitionModuleWithFiles>,
) {
    val size: Long
        get() = modules.sumOf { it.size }

    fun module(module: ObjectCharacterRecognitionModule) = modules.firstOrNull { it.module == module }

    fun recognizers() = modules.filter { it.module == ObjectCharacterRecognitionModule.Recognizer }

    /**
     * Best recognizer module for a language: exact iso-code match preferred,
     * generation-preferred (PP-OCRv6 directories outrank v5).
     */
    fun recognizerForLanguage(isoCode: String): ObjectCharacterRecognitionModuleWithFiles? {
        return recognizers()
            .filter { it.languages.contains(isoCode) || it.languages.contains("*") }
            .maxByOrNull { if (it.path.fileName.toString().contains("PP-OCRv6")) 1 else 0 }
    }

    val isComplete: Boolean
        get() = module(ObjectCharacterRecognitionModule.Detector) != null && recognizers().isNotEmpty()

    companion object {
        fun load(id: String, path: Path, modules: List<ObjectCharacterRecognitionModuleWithFiles>): ObjectCharacterRecognitionBundleWithFiles? {
            val bundleMetadataFile = File(path.toFile(), "metadata.json")
            if (!bundleMetadataFile.exists()) {
                return null
            }

            val serializer = Json { ignoreUnknownKeys = true }
            val metadata = serializer.decodeFromString<ObjectCharacterRecognitionBundleMetadata>(bundleMetadataFile.readText())

            return ObjectCharacterRecognitionBundleWithFiles(
                id = id,
                path = path,
                version = metadata.version,
                languages = metadata.languages,
                modules = modules
            )
        }
    }
}
