package app.versta.translate.core.entity

import java.nio.file.Path

data class LanguageModelFiles(
    val path: Path,
    val tokenizer: LanguageModelTokenizerFiles,
    val inference: LanguageModelInferenceFiles
)

data class LanguageModelTokenizerFiles(
    val config: Path,
    val sourceVocabulary: Path,
    val targetVocabulary: Path?,
    val source: Path,
    val target: Path
)

data class LanguageModelInferenceFiles(
    val encoder: Path,
    val decoder: Path
)

typealias LanguageModelFilesMap = Map<String, Map<String, String>>
