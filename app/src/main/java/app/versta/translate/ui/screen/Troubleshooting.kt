package app.versta.translate.ui.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldCompactBarTitle
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.theme.spacing

@Composable
fun Troubleshooting(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    licenseViewModel: LicenseViewModel
) {
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    fun onResetLicense() {
        licenseViewModel.resetLicense()
        Toast.makeText(context, context.getString(R.string.license_reset), Toast.LENGTH_SHORT)
            .show()
    }

    val onReportIssue = Intent(
        Intent.ACTION_VIEW,
        stringResource(R.string.github_issues_url, stringResource(R.string.github_url)).toUri()
    )

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.troubleshooting_title))
        },
        navigationIcon = {
            ScaffoldCompactBarBackNavigationIcon(navigationViewModel = navigationViewModel)
        },
        navigationIconContentKey = "ScaffoldCompactBarBackNavigationIcon",
        actions = {
            ScaffoldCompactBarEmptyActions()
        },
        actionsContentKey = "ScaffoldCompactBarEmptyActions",
        wrapContent = true
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            )
        ) {
            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.troubleshooting_application_logs_title),
                    supportingContent = stringResource(R.string.troubleshooting_application_logs_description),
                    onClick = {
                        navigationViewModel.navigate(
                            Screens.ApplicationLogs,
                            Screens.Troubleshooting
                        )
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_code_24),
                            contentDescription = null,
                        )
                    },
                    index = 0,
                    groupSize = 2
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.troubleshooting_report_issue_title),
                    supportingContent = stringResource(R.string.troubleshooting_report_issue_description),
                    onClick = {
                        context.startActivity(onReportIssue)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_bug_report_24),
                            contentDescription = null,
                        )
                    },
                    index = 1,
                    groupSize = 2
                )
            }

            ListDivider()

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.troubleshooting_reset_license_title),
                    supportingContent = stringResource(R.string.troubleshooting_reset_license_description),
                    onClick = {
                        onResetLicense()
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_unlicense_24),
                            contentDescription = null,
                        )
                    },
                    index = 0,
                    groupSize = 1
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun TroubleshootingPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)
    Troubleshooting(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        licenseViewModel = LicenseViewModel(
            LicenseMemoryRepository()
        )
    )
}