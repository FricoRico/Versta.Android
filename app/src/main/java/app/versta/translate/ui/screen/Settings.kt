package app.versta.translate.ui.screen

import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing

@Composable
fun Settings(
    backStack: NavBackStack,
    licenseViewModel: LicenseViewModel
) {
    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(false)

    val orientation = LocalConfiguration.current.orientation
    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }

    return ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarsurfaceContainerLowColor(),
        title = {
            Text(
                text = stringResource(R.string.settings_title),
            )
        },
        navigationIcon = {
            IconButton(onClick = {
                backStack.removeLastOrNull()
            }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
        },
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
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
                        headlineContent = stringResource(R.string.settings_languages_title),
                        supportingContent = stringResource(R.string.settings_languages_description),
                        onClick = {
                            backStack.add(Screens.LanguageSettings)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Translate,
                                contentDescription = null,
                            )
                        },
                        index = 0,
                        groupSize = 2
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Voices",
                        supportingContent = "Manage voices, download new voices",
                        onClick = {
                            backStack.add(Screens.VoicesSettings)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                            )
                        },
                        index = 1,
                        groupSize = 2
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
                            backStack.add(Screens.TranslationSettings)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Language,
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
                            backStack.add(Screens.TextToSpeechSettings)
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.GraphicEq,
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
                            backStack.add(Screens.About)
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
                            backStack.add(Screens.Troubleshooting)
                        },
                        index = 1,
                        groupSize = 2
                    )
                }
            }
        }
    )
}

@Composable
@Preview(showBackground = true)
@SuppressLint("ViewModelConstructorInComposable")
fun SettingsPreview() {
    return Settings(
        backStack = rememberNavBackStack<Screens>(),
        licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        )
    )
}