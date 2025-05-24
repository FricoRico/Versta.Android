package app.versta.translate.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationMockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LoadingProgress
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.sample

@Composable
fun ModelLoadingProgressDialog(
    translationViewModel: TranslationViewModel,
    textTranslationViewModel: TextTranslationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel
) {
    val translationModelLoadingProgress =
        translationViewModel.loadingProgress.collectAsStateWithLifecycle(false)
    val textTranslationLoadingProgress =
        textTranslationViewModel.loadingProgress.collectAsStateWithLifecycle(false)
    val textToSpeechLoadingProgress =
        textToSpeechViewModel.loadingProgress.collectAsStateWithLifecycle(false)

    if (translationModelLoadingProgress.value == LoadingProgress.InProgress || textTranslationLoadingProgress.value == LoadingProgress.InProgress || textToSpeechLoadingProgress.value == LoadingProgress.InProgress) {
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
@Preview(showBackground = true)
@SuppressLint("ViewModelConstructorInComposable")
fun ModelLoadingProgressDialogPreview() {
    val languageViewModel = LanguageViewModel(
        context = LocalContext.current,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )

    val translationViewModel = TranslationViewModel(
        intermediateTokenizer = TranslationMockTokenizer(),
        intermediateModel = TranslationMockInference(),
        outputTokenizer = TranslationMockTokenizer(),
        outputModel = TranslationMockInference(),
        translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
        languageViewModel = languageViewModel
    )

    ModelLoadingProgressDialog(
        translationViewModel = translationViewModel,
        textTranslationViewModel = TextTranslationViewModel(
            languageViewModel = languageViewModel,
            translationViewModel = translationViewModel
        ),
        textToSpeechViewModel = TextToSpeechViewModel(
            tokenizer = TextToSpeechMockTokenizer(),
            model = TextToSpeechMockInference(),
            audioPlayer = AudioMockPlayer(),
            voiceRepository = VoiceMemoryRepository(),
            textToSpeechPreferenceRepository = TextToSpeechPreferenceMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        )
    )
}