package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
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


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun About(
    navController: NavController, licenseViewModel: LicenseViewModel
) {
    val orientation = LocalContext.current.resources.configuration.orientation
    val version = LocalContext.current.packageManager?.getPackageInfo(LocalContext.current.packageName, 0)?.versionName ?: "x.x.x"

    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(true)

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    fun onBackNavigation() {
        navController.popBackStack()
    }

    ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowColor(),
        navigationIcon = {
            IconButton(onClick = {
                onBackNavigation()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        header = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(scrollConnection)
                    .padding(insets)
                    .padding(bottom = MaterialTheme.spacing.medium)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Image(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_background),
                        contentDescription = stringResource(R.string.icon_description, stringResource(R.string.app_name)),
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.spacing.large)
                            .size(MaterialTheme.spacing.extraLarge * 3)
                            .clip(MaterialTheme.shapes.extraExtraLarge),
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.neurora_attribution), style = MaterialTheme.typography.bodyMedium
                    )
                }

                ListDivider()

                item {
                    Text(
                        text = stringResource(R.string.version_number, version), style = MaterialTheme.typography.bodySmall
                    )
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier
                            .padding(top = MaterialTheme.spacing.extraSmall)
                            .clip(MaterialTheme.shapes.extraLarge),
                    ) {
                        Text(
                            text = if (hasLicense) {
                                stringResource(R.string.paid_license_badge)
                            } else {
                                stringResource(R.string.trial_license_badge)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(
                                vertical = MaterialTheme.spacing.extraSmall,
                                horizontal = MaterialTheme.spacing.small
                            )
                        )
                    }
                }
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
                        headlineContent = stringResource(R.string.about_language_models_title),
                        supportingContent = stringResource(R.string.about_language_models_description),
                        onClick = {
                            navController.navigate(Screens.LanguageAttributions())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = null,
                            )
                        },
                        index = 0,
                        groupSize = 3
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.about_third_party_title),
                        supportingContent = stringResource(R.string.about_third_party_description),
                        onClick = {
                            navController.navigate(Screens.ThirdParty())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Attribution,
                                contentDescription = null,
                            )
                        },
                        index = 1,
                        groupSize = 3
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.about_privacy_policy_title),
                        supportingContent = stringResource(R.string.about_privacy_policy_description),
                        onClick = {
                            navController.navigate(Screens.PrivacyPolicy())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Shield,
                                contentDescription = null,
                            )
                        },
                        index = 2,
                        groupSize = 3
                    )
                }
            }
        })
}

@Composable
@Preview(showBackground = true)
fun AboutPreview() {
    About(
        navController = NavController(LocalContext.current), licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        )
    )
}