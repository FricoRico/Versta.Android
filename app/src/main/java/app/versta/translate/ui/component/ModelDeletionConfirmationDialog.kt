package app.versta.translate.ui.component

import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.versta.translate.R

@Composable
fun ModelDeletionConfirmationDialog(
    model: String?,
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int,
    onConfirmation: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (model == null) return

    AlertDialog(
        icon = {},
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = stringResource(descriptionRes, model),
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
