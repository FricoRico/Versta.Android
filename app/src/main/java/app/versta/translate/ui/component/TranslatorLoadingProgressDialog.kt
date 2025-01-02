package app.versta.translate.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.MockInference
import app.versta.translate.adapter.outbound.MockTokenizer
import app.versta.translate.core.model.LoadingProgress
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.spacing

@Composable
fun TranslatorLoadingProgressDialog(
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val translationModelLoadingProgress =
        translationViewModel.loadingProgress.collectAsStateWithLifecycle()
    val textTranslationLoadingProgress =
        textTranslationViewModel.loadingProgress.collectAsStateWithLifecycle()

    if (translationModelLoadingProgress.value == LoadingProgress.InProgress || textTranslationLoadingProgress.value == LoadingProgress.InProgress) {
        Dialog(onDismissRequest = { /* Can not be dismissed */ }) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.extraLarge)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
@Preview
fun TranslatorLoadingDialogPreview() {
    TranslatorLoadingProgressDialog(
        translationViewModel = TranslationViewModel(
            tokenizer = MockTokenizer(),
            model = MockInference(),
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        textTranslationViewModel = TextTranslationViewModel(
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        )
    )
}