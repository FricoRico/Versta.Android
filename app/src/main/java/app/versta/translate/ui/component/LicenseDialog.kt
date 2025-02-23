package app.versta.translate.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.DialogState
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseDialog(
    licenseViewModel: LicenseViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val drawerOpenedState by licenseViewModel.licenseDialogState.collectAsStateWithLifecycle()
    val drawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val onLicense = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(stringResource(R.string.license_url, stringResource(R.string.site_url)))
    )

    if (drawerOpenedState == DialogState.Closed) {
        return
    }

    ModalBottomSheet(
        sheetState = drawerState,
        onDismissRequest = { licenseViewModel.setLicenseDialogState(DialogState.Closed) },
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.spacing.small)
                .padding(bottom = MaterialTheme.spacing.large)
                .then(modifier)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Button(
                    onClick = {
                        context.startActivity(onLicense)
                    }
                ) {
                    Text(
                        text = "Buy a license"
                    )
                }
                Button(
                    onClick = {
                        licenseViewModel.setLicense(true)
                    }
                ) {
                    Text(
                        text = "I have already paid"
                    )
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun LicenseDialogPreview() {
    LicenseDialog(
        LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        )
    )
}
