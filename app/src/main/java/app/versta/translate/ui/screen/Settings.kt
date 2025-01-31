package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.component.ListDivider
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.SettingsButtonItem
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    navController: NavController,
    licenseViewModel: LicenseViewModel
) {
    return ScaffoldLargeHeader(
        title = {
            Text(
                text = "Settings",
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
                    .nestedScroll(scrollConnection)
                    .padding(horizontal = MaterialTheme.spacing.small)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    top = MaterialTheme.spacing.small + MaterialTheme.spacing.extraSmall,
                    bottom = insets.asPaddingValues()
                        .calculateBottomPadding() + MaterialTheme.spacing.small
                )
            ) {
                item {
                    SettingsButtonItem(
                        headlineContent = "Languages",
                        supportingContent = "Import languages, download languages",
                        onClick = {
                            navController.navigate(Screens.LanguageSettings())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Translate,
                                contentDescription = "Languages",
                            )
                        },
                        index = 0,
                        groupSize = 2
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Translation",
                        supportingContent = "Manage history, translator fine-tuning",
                        onClick = {
                            navController.navigate(Screens.TranslationSettings())
                        },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.Language,
                                contentDescription = "Translation",
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
                        headlineContent = "Vision",
                        supportingContent = "Configure camera, object tracking",
                        onClick = {/*TODO*/ },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.CameraAlt,
                                contentDescription = "Camera",
                            )
                        },
                        index = 0,
                        groupSize = 2
                    )
                }

                item {
                    SettingsButtonItem(
                        headlineContent = "Voice",
                        supportingContent = "Microphone settings, transcription",
                        onClick = {/*TODO*/ },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.MicNone,
                                contentDescription = "Microphone",
                            )
                        },
                        index = 1,
                        groupSize = 2
                    )
                }

                ListDivider()

                item {
                    SettingsButtonItem(
                        headlineContent = "About",
                        supportingContent = "Version, privacy policy, contributions",
                        onClick = {/*TODO*/ },
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
        licenseViewModel = LicenseViewModel()
    )
}