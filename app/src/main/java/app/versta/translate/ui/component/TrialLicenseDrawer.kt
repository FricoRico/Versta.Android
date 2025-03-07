package app.versta.translate.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.DialogState
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.theme.ButtonDefaults
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.annotateSentence
import kotlinx.coroutines.launch
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrialLicenseDrawer(
    licenseViewModel: LicenseViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val drawerOpenedState by licenseViewModel.licenseDialogState.collectAsStateWithLifecycle()
    val drawerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val drawerScope = rememberCoroutineScope()

    val onBuyLicense = Intent(
        Intent.ACTION_VIEW,
        stringResource(R.string.license_url, stringResource(R.string.site_url)).toUri()
    )
    
    fun onConfirmLicense() {
        drawerScope.launch {
            licenseViewModel.setLicense(true)
            licenseViewModel.setLicenseDialogState(DialogState.Confirm)
        }
    }

    if (drawerOpenedState != DialogState.Open) {
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
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                contentPadding = PaddingValues(
                    horizontal = MaterialTheme.spacing.medium,
                )
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Text(
                            text = stringResource(R.string.license_upgrade_title),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = MaterialTheme.spacing.large)
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.license_upgrade_thank_you),
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.license_upgrade_explanation)
                    )
                }

                item {
                    Text(
                        text = annotateSentence(
                            sentence = stringResource(R.string.license_upgrade_hint),
                            annotation = stringResource(R.string.license_upgrade_hint_highlight),
                            style = SpanStyle(
                                fontWeight = FontWeight.Light,
                                fontStyle = FontStyle.Italic
                            )
                        )
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .padding(top = MaterialTheme.spacing.large)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                            colors = ButtonDefaults.transparentButtonColors(),
                            onClick = { onConfirmLicense() }
                        ) {
                            Text(
                                text = stringResource(R.string.license_upgrade_has_license),
                            )
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            contentPadding = PaddingValues(MaterialTheme.spacing.medium),
                            onClick = {
                                context.startActivity(onBuyLicense)
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(
                                    MaterialTheme.spacing.small
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.license_uprade_buy_license),
                                )
                                Icon(
                                    Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun TrialLicenseDrawerPreview() {
    TrialLicenseDrawer(
        LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        ).apply {
            setLicenseDialogState(DialogState.Open)
        }
    )
}
