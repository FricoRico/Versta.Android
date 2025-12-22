package app.versta.translate.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.versta.translate.R

@Composable
fun OcrDeletionConfirmationDialog(
    model: String?,
    onConfirmation: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (model == null) return

    AlertDialog(
        icon = {},
        title = {
            Text(
                text = stringResource(R.string.delete_ocr_model_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(R.string.delete_ocr_model_description, model),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation(model)
                }
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

