package app.versta.translate.ui.screen

import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.SettingsDefaults
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navController: NavController,
    licenseViewModel: LicenseViewModel
) {
    val orientation = LocalContext.current.resources.configuration.orientation

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
                navController.popBackStack()
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
                            navController.navigate(Screens.LanguageSettings())
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
                        headlineContent = stringResource(R.string.settings_translation_title),
                        supportingContent = stringResource(R.string.settings_translation_description),
                        onClick = {
                            navController.navigate(Screens.TranslationSettings())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = null,
                            )
                        },
                        index = 1,
                        groupSize = 2
                    )
                }

                ListDivider()

                item {
                    TrialLicenseCard(licenseViewModel = licenseViewModel)
                }

                ListDivider()

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.settings_vision_title),
                        supportingContent = stringResource(R.string.settings_vision_description),
                        onClick = {/*TODO*/ },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = null,
                            )
                        },
                        index = 0,
                        groupSize = 2
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = stringResource(R.string.settings_voice_title),
                        supportingContent = stringResource(R.string.settings_voice_description),
                        onClick = {/*TODO*/ },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.MicNone,
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
                            navController.navigate(Screens.About())
                        },
                        index = 0,
                        groupSize = 1
                    )
                }
            }
        }
    )
}

@Composable
@Preview
fun SettingsPreview() {
    return Settings(
        navController = rememberNavController(),
        licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        )
    )
}