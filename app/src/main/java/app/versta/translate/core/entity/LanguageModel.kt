package app.versta.translate.core.entity

import java.nio.file.Path

data class LanguageModelFiles(
    val path: Path,
    val tokenizer: LanguageModelTokenizerFiles,
    val inference: LanguageModelInferenceFiles
)

data class LanguageModelTokenizerFiles(
    val config: String,
    val vocabulary: String,
    val source: String,
    val target: String
)

data class LanguageModelInferenceFiles(
    val encoder: String,
    val decoder: String
)

typealias LanguageModelFilesMap = Map<String, Map<String, String>>
