package app.versta.translate

import android.content.Intent
import android.os.Bundle
import app.versta.translate.core.model.TextTranslationViewModel

class TranslateActivity : MainActivity() {
    private val textTranslationViewModel = TextTranslationViewModel(
        languagePreferenceRepository = MainApplication.module.languagePreferenceRepository
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            textTranslationViewModel.setInput(selectedText)
        }

        super.onCreate(savedInstanceState)
    }
}