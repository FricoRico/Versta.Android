package app.versta.translate.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.databaseModule
import app.versta.translate.translateModule
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.koinActivityViewModel
import app.versta.translate.utils.shift
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

@Composable
fun TrialLicensePill(
    licenseViewModel: LicenseViewModel = koinActivityViewModel(),
    modifier: Modifier = Modifier
) {
    val isTrialLicense by licenseViewModel.isTrialLicense.collectAsStateWithLifecycle()

    if (!isTrialLicense) return

    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .then(modifier),
    ) {
        BasicText(
            text = "Trial license",
            style = TextStyle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiary.shift(-30f, 1.2f, 0.9f),
                        MaterialTheme.colorScheme.tertiary,
                    )
                ),
                fontSize = MaterialTheme.typography.bodySmall.fontSize,
                fontWeight = MaterialTheme.typography.bodySmall.fontWeight,
                textAlign = MaterialTheme.typography.bodySmall.textAlign,
            ),
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.small,
                vertical = MaterialTheme.spacing.extraSmall,
            ),
        )
    }
}

@Composable
@Preview
private fun TrialLicensePillPreview() {
    return KoinApplication(
        application = {
            modules(
                translateModule,
                databaseModule,
            )
        }
    ) {
        TrialLicensePill(licenseViewModel = koinInject())
    }
}