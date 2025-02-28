package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.ui.theme.spacing

@Composable
fun TranslationTextField(
    modifier: Modifier = Modifier,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    textTranslationViewModel: TextTranslationViewModel
) {
    val input by textTranslationViewModel.input.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .then(modifier)
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.extraLarge
                )
                .fillMaxWidth()
        ) {
            TextField(
                placeholder = "Type something",
                modifier = Modifier
                    .padding(top = MaterialTheme.spacing.small)
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .defaultMinSize(minHeight = 192.dp),
                value = input,
                onValueChange = {
                    textTranslationViewModel.setInput(it)
                },
                onSubmit = {
                    onSubmit(input)
                },
                maxLines = 8,
                colors = TextFieldDefaults.colorsTransparent()
            )
            Row (
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .padding(bottom = MaterialTheme.spacing.small)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
                ) {
                    FilledIconButton(
                        onClick = {
                            onClear()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear"
                        )
                    }
                    FilledIconButton(
                        onClick = {},
                        enabled = false,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.surfaceContainerLowest
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MicNone,
                            contentDescription = "Dictate"
                        )
                    }
                }
                FilledIconButton(
                    onClick = {
                        onSubmit(input)
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "Translate"
                    )
                }
            }
        }

    }
}

@Composable
@Preview(showBackground = true)
fun TranslationTextFieldMinimalPreview() {
    TranslationTextField (
        textTranslationViewModel = TextTranslationViewModel(
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        onSubmit = {},
        onClear = {}
    )
}