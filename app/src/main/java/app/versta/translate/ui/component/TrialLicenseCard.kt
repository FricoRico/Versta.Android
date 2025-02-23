package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.DialogState
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.shift

@Composable
fun TrialLicenseCard(
    licenseViewModel: LicenseViewModel,
    modifier: Modifier = Modifier,
) {
    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(true)

    if (hasLicense) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiary.shift(-30f, 1.2f, 0.9f),
                        MaterialTheme.colorScheme.tertiary,
                    )
                ),
                shape = MaterialTheme.shapes.extraLarge,
            )
            .then(modifier),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        onClick = {
            licenseViewModel.setLicenseDialogState(DialogState.Open)
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            modifier = Modifier.padding(
                MaterialTheme.spacing.medium,
            )
        ) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.onTertiaryContainer,
                        MaterialTheme.shapes.extraLarge
                    )
                    .padding(MaterialTheme.spacing.small),
            ) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = "License",
                    tint = MaterialTheme.colorScheme.tertiary.shift(-30f, 1.2f, 0.9f),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(0.8f) // 65% of the width
            ) {
                Text(
                    text = "Trial license",
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleSmall,
                    fontSize = 18.sp,
                )
                Text(
                    text = "You are currently on a trial license, support the project by upgrading.",
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.shapes.extraLarge
                    )
                    .padding(MaterialTheme.spacing.small),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.CallMade,
                    contentDescription = "Open in browser",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
@Preview
private fun TrialLicenseCardPreview() {
        TrialLicenseCard(
            licenseViewModel = LicenseViewModel(
                licenseRepository = LicenseMemoryRepository()
            )
        )
}