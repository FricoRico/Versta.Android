package app.versta.translate.ui.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.AudioMockPlayer
import app.versta.translate.adapter.outbound.DataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalDataMemoryRepository
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.VoiceMemoryRepository
import app.versta.translate.adapter.outbound.TextToSpeechMockInference
import app.versta.translate.adapter.outbound.TextToSpeechMockTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.spacing

@Composable
fun ErrorAlertDialog(
    modifier: Modifier = Modifier,
    translationViewModel: TranslationViewModel,
    textToSpeechViewModel: TextToSpeechViewModel
) {
    val translationError by translationViewModel.translationError.collectAsStateWithLifecycle()
    val textToSpeechError by textToSpeechViewModel.textToSpeechError.collectAsStateWithLifecycle()

    fun onDismissRequest() {
        translationViewModel.clearTranslationError()
        textToSpeechViewModel.clearTextToSpeechError()
    }

    if (translationError == null && textToSpeechError == null) {
        return
    }

    Dialog(
        onDismissRequest = { onDismissRequest() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(modifier),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.spacing.medium,
                    Alignment.CenterVertically
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Icon(
                        ImageVector.vectorResource(R.drawable.round_error_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                when {
                    translationError != null -> TranslationError(translationError)
                    textToSpeechError != null -> TextToSpeechError(textToSpeechError)
                }

                item {
                    Button(
                        onClick = { onDismissRequest() },
                        colors = ButtonDefaults.transparentButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                    ) {
                        Text(text = stringResource(R.string.dismiss))
                    }
                }
            }
        }
    }
}

fun LazyListScope.TranslationError(error: Throwable?) {
    item {
        Text(
            text = stringResource(R.string.translation_error_title),
            style = MaterialTheme.typography.headlineSmall
        )
    }

    item {
        Text(
            text = stringResource(R.string.translation_error_description),
        )
    }

    item {
        Text(
            text = error?.message ?: stringResource(R.string.unknown_error),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Light,
            fontStyle = FontStyle.Italic,
            maxLines = 6,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.large),
        )
    }
}

fun LazyListScope.TextToSpeechError(error: Throwable?) {
    item {
        Text(
            text = stringResource(R.string.text_to_speech_error_title),
            style = MaterialTheme.typography.headlineSmall
        )
    }

    item {
        Text(
            text = stringResource(R.string.text_to_speech_error_description),
        )
    }

    item {
        Text(
            text = error?.message ?: stringResource(R.string.unknown_error),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Light,
            fontStyle = FontStyle.Italic,
            maxLines = 6,
            modifier = Modifier.padding(vertical = MaterialTheme.spacing.large),
        )
    }
}

@Composable
@Preview(showBackground = true)
fun ErrorAlertDialogPreview() {
    val translationMockInference = TranslationMockInference()

    ErrorAlertDialog(
        translationViewModel = TranslationViewModel(
            intermediateModel = translationMockInference,
            outputModel = translationMockInference,
            translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
            languageViewModel = LanguageViewModel(
                context = LocalContext.current,
                languageRepository = LanguageMemoryRepository(),
                languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
                externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
            )
        ).apply {
            setTranslationError(Error("Error message"))
        },
        textToSpeechViewModel = TextToSpeechViewModel(
            context = LocalContext.current,
            espeakNG = ESpeakNG(),
            openJTalk = OpenJTalk(),
            tokenizer = TextToSpeechMockTokenizer(),
            model = TextToSpeechMockInference(),
            audioPlayer = AudioMockPlayer(),
            dataRepository = DataMemoryRepository(),
            voiceRepository = VoiceMemoryRepository(),
            textToSpeechPreferenceRepository = TextToSpeechPreferenceMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalDataRepository = ExternalDataMemoryRepository()
        )
    )
}