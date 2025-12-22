package app.versta.translate.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.ui.component.Divider
import app.versta.translate.ui.component.ScaffoldCompactBarBackNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarEmptyActions
import app.versta.translate.ui.component.ScaffoldComponentProvider
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.theme.spacing


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun About(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    licenseViewModel: LicenseViewModel
) {
    val layoutDirection = LocalLayoutDirection.current
    val version = LocalContext.current.packageManager?.getPackageInfo(
        LocalContext.current.packageName, 0
    )?.versionName ?: "x.x.x"

    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(true)

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = { {} },
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
            modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            )
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = MaterialTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.spacing.large)
                            .size(MaterialTheme.spacing.extraLarge * 3)
                            .clip(MaterialTheme.shapes.extraExtraLarge),
                    ) {
                        Image(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_background),
                            contentDescription = stringResource(
                                R.string.icon_description, stringResource(R.string.app_name)
                            ),
                            colorFilter = ColorFilter.tint(
                                MaterialTheme.colorScheme.primary, BlendMode.Color
                            )
                        )
                        Image(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
                            contentDescription = stringResource(
                                R.string.icon_description, stringResource(R.string.app_name)
                            ),
                            modifier = Modifier.requiredSize(144.dp),
                            colorFilter = ColorFilter.tint(
                                MaterialTheme.colorScheme.primary, BlendMode.Color
                            )
                        )
                    }

                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall
                    )

                    Text(
                        text = stringResource(R.string.neurora_attribution),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Divider()

                    Text(
                        text = stringResource(R.string.version_number, version),
                        style = MaterialTheme.typography.bodySmall
                    )

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

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.about_language_models_title),
                    supportingContent = stringResource(R.string.about_language_models_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.LanguageAttributions, Screens.About)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_language_japanese_kana_24),
                            contentDescription = null,
                        )
                    },
                    index = 0,
                    groupSize = 5
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.about_voice_models_title),
                    supportingContent = stringResource(R.string.about_voice_models_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.VoiceAttributions, Screens.About)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_record_voice_over_24),
                            contentDescription = null,
                        )
                    },
                    index = 1,
                    groupSize = 5
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.about_ocr_models_title),
                    supportingContent = stringResource(R.string.about_ocr_models_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.ObjectCharacterRecognitionAttributions, Screens.About)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_photo_camera_24),
                            contentDescription = null,
                        )
                    },
                    index = 2,
                    groupSize = 5
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.about_third_party_title),
                    supportingContent = stringResource(R.string.about_third_party_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.ThirdParty, Screens.About)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_attribution_24),
                            contentDescription = null,
                        )
                    },
                    index = 3,
                    groupSize = 5
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.about_privacy_policy_title),
                    supportingContent = stringResource(R.string.about_privacy_policy_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.PrivacyPolicy, Screens.About)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_shield_person_24),
                            contentDescription = null,
                        )
                    },
                    index = 4,
                    groupSize = 5
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun AboutPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    About(
        innerPadding = PaddingValues(),
        scaffoldViewModel = ScaffoldViewModel(
            navigationViewModel = navigationViewModel
        ),
        navigationViewModel = navigationViewModel,
        licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        )
    )
}