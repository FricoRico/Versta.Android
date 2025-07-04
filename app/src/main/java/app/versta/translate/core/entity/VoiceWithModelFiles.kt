package app.versta.translate.core.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
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
    val voices: VoiceModelVoiceFiles,
    val vocabulary: Path? = null
) {
    fun isValid() = inference.isValid() &&
            voices.isValid() &&
            (vocabulary == null || vocabulary.exists())

    fun hasVocabularyFile() = vocabulary != null

    fun supportsVocabularyFile(): Boolean {
        // Vocabulary files are supported from version 1.2.0 onwards
        return compareVersion(version, "v1.2.0") >= 0
    }

    private fun compareVersion(version1: String, version2: String): Int {
        val v1 = version1.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val v2 = version2.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(v1.size, v2.size)) {
            val part1 = v1.getOrNull(i) ?: 0
            val part2 = v2.getOrNull(i) ?: 0
            when {
                part1 > part2 -> return 1
                part1 < part2 -> return -1
            }
        }
        return 0
    }

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true }

        fun load(id: String, path: Path): VoiceWithModelFiles {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Text-to-speech model metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<VoiceModelMetadata>(metadataFile.readText())
            val files = VoiceWithModelFiles(
                id = id,
                path = path,
                baseModel = metadata.baseModel,
                architectures = metadata.architectures,
                version = metadata.version,
                size = size(path.parent),
                inference = VoiceModelInferenceFiles(
                    model = path.resolve(metadata.files.inference.model)
                ),
                voices = VoiceModelVoiceFiles().apply {
                    addAll(metadata.files.voices.map {
                        path.resolve(it)
                    })
                },
                vocabulary = metadata.files.vocabulary?.let { path.resolve(it) }
            )

            if (!files.isValid()) {
                throw IllegalArgumentException("Text-to-speech model files are not complete and valid: $files")
            }

            return files
        }

        private fun size(path: Path): Long {
            var folderSize: Long = 0

            Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    folderSize += Files.size(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) {
                        throw exc
                    }

                    return FileVisitResult.CONTINUE
                }
            })

            return folderSize
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
