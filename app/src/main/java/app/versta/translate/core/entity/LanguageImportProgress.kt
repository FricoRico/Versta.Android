package app.versta.translate.core.entity

import android.net.Uri

sealed class LanguageImportProgress {
    data object Idle : LanguageImportProgress()
    data object Started : LanguageImportProgress()

    data class InProgress(val current: String, val extracted: Int, val total: Int) :
        LanguageImportProgress()

    data class Completed(val metadata: ModelMetadata) : LanguageImportProgress()
    data class Error(val exception: Exception) : LanguageImportProgress()
}

sealed class LanguageAnalysisProgress {
    data object Idle : LanguageAnalysisProgress()
    data object InProgress : LanguageAnalysisProgress()

    data class Completed(val metadata: BundleMetadata, val uri: Uri) : LanguageAnalysisProgress()
    data class Error(val exception: Exception) : LanguageAnalysisProgress()
}