package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing

@Composable
fun Settings(
    innerPadding: PaddingValues,
    scaffoldViewModel: ScaffoldViewModel,
    navigationViewModel: NavigationViewModel,
    licenseViewModel: LicenseViewModel
) {
    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(false)

    val layoutDirection = LocalLayoutDirection.current

    ScaffoldComponentProvider(
        scaffoldViewModel = scaffoldViewModel,
        title = {
            ScaffoldCompactBarTitle(text = stringResource(R.string.settings))
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
            modifier = Modifier,
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + MaterialTheme.spacing.medium,
                end = innerPadding.calculateEndPadding(layoutDirection) + MaterialTheme.spacing.medium,
                top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.large,
                bottom = innerPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
            )
        ) {
            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.settings_languages_title),
                    supportingContent = stringResource(R.string.settings_languages_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.LanguageSettings, Screens.Settings)
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_language_japanese_kana_24),
                            contentDescription = null,
                        )
                    },
                    index = 0,
                    groupSize = 3
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = "Voices",
                    supportingContent = "Manage voices, download new voices",
                    onClick = {
                        navigationViewModel.navigate(
                            Screens.VoiceSettings,
                            Screens.Settings
                        )
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_record_voice_over_24),
                            contentDescription = null,
                        )
                    },
                    index = 1,
                    groupSize = 3
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = "Vision",
                    supportingContent = "Manage object character recognition models",
                    onClick = {
                        navigationViewModel.navigate(
                            Screens.ObjectCharacterRecognitionSettings,
                            Screens.Settings
                        )
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_record_voice_over_24),
                            contentDescription = null,
                        )
                    },
                    index = 2,
                    groupSize = 3
                )
            }

            if (!hasLicense) {
                ListDivider()

                item {
                    TrialLicenseCard(licenseViewModel = licenseViewModel)
                }
            }

            ListDivider()

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.settings_translation_title),
                    supportingContent = stringResource(R.string.settings_translation_description),
                    onClick = {
                        navigationViewModel.navigate(
                            Screens.TranslationSettings,
                            Screens.Settings
                        )
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_language_japanese_kana_24),
                            contentDescription = null,
                        )
                    },
                    index = 0,
                    groupSize = 2
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.settings_text_to_speech_title),
                    supportingContent = stringResource(R.string.settings_text_to_speech_description),
                    onClick = {
                        navigationViewModel.navigate(
                            Screens.TextToSpeechSettings,
                            Screens.Settings
                        )
                    },
                    leadingContent = {
                        Icon(
                            ImageVector.vectorResource(R.drawable.rounded_graphic_eq_24),
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
                    headlineContent = stringResource(R.string.settings_about_title),
                    supportingContent = stringResource(R.string.settings_about_description),
                    onClick = {
                        navigationViewModel.navigate(Screens.About, Screens.Settings)
                    },
                    index = 0,
                    groupSize = 2
                )
            }

            item {
                SettingsButtonItem(
                    headlineContent = stringResource(R.string.settings_troubleshooting_title),
                    supportingContent = stringResource(R.string.settings_troubleshooting_description),
                    onClick = {
                        navigationViewModel.navigate(
                            Screens.Troubleshooting,
                            Screens.Settings
                        )
                    },
                    index = 1,
                    groupSize = 2
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true)
fun SettingsPreview() {
    val navigationViewModel = NavigationViewModel(Screens.TextTranslation)

    return Settings(
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