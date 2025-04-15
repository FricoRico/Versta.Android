package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.core.entity.LanguagePair
import app.versta.translate.ui.theme.spacing

@Composable
fun LanguageDeletionConfirmationDialog(
    pair: LanguagePair?,
    onConfirmation: (LanguagePair) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (pair == null) {
        return
    }

    AlertDialog(onDismissRequest = { onDismissRequest() },
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            LanguagePairBadge(
                pair = pair,
                bidirectional = false,
                icon = Icons.Outlined.Error,
                colors = LanguagePairBadgeDefaults.colors(
                    badgeColor = MaterialTheme.colorScheme.error,
                    badgeContentColor = MaterialTheme.colorScheme.onError,
                )
            )
        },
        title = {
            Text(text = stringResource(R.string.delete_language_title, "${pair.source.name} - ${pair.target.name}"),
                textAlign = TextAlign.Center)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.delete_language_description),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmation(pair)
            }) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest()
            }) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
@Preview(showBackground = true)
fun LanguageDeletionConfirmationDialogPreview() {
    LanguageDeletionConfirmationDialog(
        pair = LanguagePair.fromIsoCodes("nl", "en"),
        onConfirmation = {},
        onDismissRequest = {}
    )
}