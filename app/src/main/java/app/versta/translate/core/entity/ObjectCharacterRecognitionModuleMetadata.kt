package app.versta.translate.core.entity

import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists

@Serializable
data class ObjectCharacterRecognitionModuleFile(
    val inference: String,
    val priority: Int = 1,
    val vocab: String? = null
)

@Serializable
data class ObjectCharacterRecognitionModuleMetadata(
    val module: ObjectCharacterRecognitionModule,
    val languages: List<String>,
    val files: List<ObjectCharacterRecognitionModuleFile>,
) {
    fun isValid(path: Path) = files.isNotEmpty() && files.any { path.resolve(it.inference).exists() }

    /**
     * The best file on disk for this module. Higher priority wins; a file
     * only counts when it (and its vocab, for recognizers) exists.
     */
    fun bestFile(path: Path): ObjectCharacterRecognitionModuleFile? {
        return files
            .filter { path.resolve(it.inference).exists() && (it.vocab == null || path.resolve(it.vocab).exists()) }
            .maxByOrNull { it.priority }
    }
}
