package app.versta.translate.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.DialogState
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrialLicenseConfirmationDialog(
    licenseViewModel: LicenseViewModel,
) {
    val dialogState by licenseViewModel.licenseDialogState.collectAsStateWithLifecycle(null)
    val dialogVisible = dialogState == DialogState.Confirm

    val dialogCoroutineScope = rememberCoroutineScope()

    fun hideDialog() {
        licenseViewModel.setLicenseDialogState(DialogState.Closed)
    }

    fun autoHideDialog() {
        dialogCoroutineScope.launch {
            delay(10000)
            hideDialog()
        }
    }

    LaunchedEffect(dialogState) {
        if (!dialogVisible) {
            return@LaunchedEffect
        }

        autoHideDialog()
    }

    if (!dialogVisible) {
        return
    }

    Dialog(
        onDismissRequest = { hideDialog() },
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large)
                    .padding(bottom = MaterialTheme.spacing.medium),
                verticalArrangement = Arrangement.spacedBy(
                    MaterialTheme.spacing.medium,
                    Alignment.CenterVertically
                )
            ) {
                item {
                    Text(
                        text = stringResource(R.string.license_confirmation_title),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = MaterialTheme.spacing.small)
                    )
                }

                item {
                    Text(text = stringResource(R.string.license_confirmation_making_a_difference))
                }

                item {
                    Text(text = stringResource(
                        R.string.license_confirmation_contribution_perks,
                        stringResource(R.string.app_name)
                    ))
                }

                item {
                    Text(text = stringResource(R.string.license_confirmation_thank_you))
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun TrialLicenseConfirmationDialogPreview() {
    TrialLicenseConfirmationDialog(
        licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        ).apply {
            setLicenseDialogState(DialogState.Confirm)
        },
    )
}