package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.versta.translate.R
import app.versta.translate.core.entity.ExternalVoiceModelDefinition
import app.versta.translate.ui.theme.spacing

@Composable
fun VoiceDeletionConfirmationDialog(
    model: ExternalVoiceModelDefinition?,
    onConfirmation: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (model == null) {
        return
    }

    AlertDialog(onDismissRequest = { onDismissRequest() },
        shape = MaterialTheme.shapes.extraLarge,
        icon = {
            Icon(
                ImageVector.vectorResource(R.drawable.round_error_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = stringResource(R.string.delete_voice_title, model.name),
                textAlign = TextAlign.Center)
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.delete_voice_description),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirmation(model.id)
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
fun VoiceDeletionConfirmationDialogPreview() {
    VoiceDeletionConfirmationDialog(
        model = ExternalVoiceModelDefinition(
            id = "kokoro",
            name = "Kokoro",
            baseModel = "hexgrad/Kokoro-82M",
            version = "v1.0.0",
            size = 163899505,
            voices = emptyList(),
            architectures = emptyList(),
            bundle = "https://mock.versta.app/kokoro-bundle.tar.gz",
            checksum = "https://mock.versta.app/kokoro-bundle.tar.gz.sha256",
        ),
        onConfirmation = {},
        onDismissRequest = {}
    )
}