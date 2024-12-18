package app.versta.translate

import android.content.Intent
import android.os.Bundle
import app.versta.translate.core.model.TextTranslationViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class TranslateActivity : MainActivity() {
    private val textTranslationViewModel: TextTranslationViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val selectedText = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: ""
            textTranslationViewModel.setSourceText(selectedText)
        }

        super.onCreate(savedInstanceState)
    }
}