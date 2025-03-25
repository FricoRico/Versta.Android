package app.versta.translate.core.entity

import android.net.Uri

sealed class TextToSpeechImportProgress {
    data object Idle : TextToSpeechImportProgress()
    data object Started : TextToSpeechImportProgress()

    data class InProgress(val current: String, val extracted: Int, val total: Int) :
        TextToSpeechImportProgress()

    data class Completed(val metadata: TextToSpeechModel) : TextToSpeechImportProgress()
    data class Error(val exception: Exception) : TextToSpeechImportProgress()
}

sealed class TextToSpeechAnalysisProgress {
    data object Idle : TextToSpeechAnalysisProgress()
    data object InProgress : TextToSpeechAnalysisProgress()

    data class Completed(val metadata: TextToSpeechBundleMetadata, val uri: Uri) : TextToSpeechAnalysisProgress()
    data class Error(val exception: Exception) : TextToSpeechAnalysisProgress()
}