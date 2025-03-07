package app.versta.translate.ui.screen

import android.content.Intent
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Attribution
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.theme.spacing


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Troubleshooting(
    navController: NavController,
    licenseViewModel: LicenseViewModel
) {
    val context = LocalContext.current
    val orientation = context.resources.configuration.orientation

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    fun onBackNavigation() {
        navController.popBackStack()
    }

    fun onResetLicense() {
        licenseViewModel.resetLicense()
        Toast.makeText(context, context.getString(R.string.license_reset), Toast.LENGTH_SHORT).show()
    }

    val onReportIssue = Intent(
        Intent.ACTION_VIEW,
        stringResource(R.string.github_issues_url, stringResource(R.string.github_url)).toUri()
    )

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowColor(),
        title = {
            Text(
                text = stringResource(R.string.troubleshooting_title),
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                onBackNavigation()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollConnection),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                )
            ) {
                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.troubleshooting_application_logs_title),
                        supportingContent = stringResource(R.string.troubleshooting_application_logs_description),
                        onClick = {
                            navController.navigate(Screens.ApplicationLogs())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.DataObject,
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
                                Icons.Outlined.Attribution,
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
                                Icons.Outlined.CreditCardOff,
                                contentDescription = null,
                            )
                        },
                        index = 0,
                        groupSize = 1
                    )
                }
            }
        })
}

@Composable
@Preview(showBackground = true)
fun TroubleshootingPreview() {
    Troubleshooting(
        navController = NavController(LocalContext.current),
        licenseViewModel = LicenseViewModel(
            LicenseMemoryRepository()
        )
    )
}