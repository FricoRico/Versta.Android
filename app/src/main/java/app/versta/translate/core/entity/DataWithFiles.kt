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

interface DataFilesInterface {
    fun isValid(): Boolean
}

@Serializable
data class DataWithFiles(
    val id: String,
    val path: Path,
    val version: String,
    val size: Long = 0,
    val files: DataFilesInterface
) {
    fun isValid() = files.isValid()

    companion object {
        private val serializer = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

        fun load(path: Path): DataWithFiles? {
            val metadataFile = File(path.toFile(), "metadata.json")
            if (!metadataFile.exists()) {
                throw IllegalArgumentException("Text-to-speech data metadata file not found: ${metadataFile.absolutePath}")
            }

            val metadata =
                serializer.decodeFromString<DataMetadata>(metadataFile.readText())

            val files = when (metadata) {
                is TextToSpeechDataMetadata -> TextToSpeechDataFiles(
                    espeak = path.resolve(metadata.files.espeak),
                    openJTalk = path.resolve(metadata.files.openJTalk)
                )
            }

            val data = DataWithFiles(
                id = metadata.id,
                path = path,
                version = metadata.version,
                size = size(path.parent),
                files = files
            )

            if (!data.isValid()) {
                throw IllegalArgumentException("Data files are not complete and valid: $data")
            }

            return data
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
data class TextToSpeechDataFiles(
    val espeak: Path,
    val openJTalk: Path
) : DataFilesInterface {
    override fun isValid(): Boolean {
        return Files.exists(espeak) && Files.exists(openJTalk)
    }
}
