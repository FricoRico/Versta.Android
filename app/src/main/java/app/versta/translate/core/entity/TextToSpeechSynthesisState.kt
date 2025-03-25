package app.versta.translate.core.entity

sealed class TextToSpeechSynthesisState {
    data object Idle : TextToSpeechSynthesisState()
    data object Preparing : TextToSpeechSynthesisState()
    data object Synthesizing : TextToSpeechSynthesisState()
}