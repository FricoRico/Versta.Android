package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.MockInference
import app.versta.translate.adapter.outbound.MockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.spacing

@Composable
fun TranslationErrorAlertDialog(
    modifier: Modifier = Modifier,
    translationViewModel: TranslationViewModel,
) {
    val translationError by translationViewModel.translationError.collectAsStateWithLifecycle()

    fun onDismissRequest() {
        translationViewModel.clearTranslationError()
    }

    if (translationError == null) {
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
            LazyColumn (
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(MaterialTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item {
                    Icon(
                        Icons.Outlined.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                
                item {
                    Text(text = stringResource(R.string.translation_error_title), style = MaterialTheme.typography.headlineSmall)
                }

                item {
                    Text(
                        text = stringResource(R.string.translation_error_description),
                    )
                }
                
                item {
                    Text(
                        text = translationError?.message ?: stringResource(R.string.unknown_error),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Light,
                        fontStyle = FontStyle.Italic,
                        maxLines = 6,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.large),
                    )
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

@Composable
@Preview(showBackground = true)
fun TranslationErrorAlertDialogPreview() {
    TranslationErrorAlertDialog(
        translationViewModel = TranslationViewModel(
            tokenizer = MockTokenizer(),
            model = MockInference(),
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            translationPreferenceRepository = TranslationPreferenceMemoryRepository()
        ).apply {
            setTranslationError(Error("Error message"))
        }
    )
}