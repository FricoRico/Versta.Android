package app.versta.translate.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.ui.component.ButtonCard
import app.versta.translate.ui.component.ButtonCardDefaults
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.TranslationTextField
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState

@SuppressLint("InlinedApi")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun Home(
    navController: NavHostController,
    licenseViewModel: LicenseViewModel,
    languageViewModel: LanguageViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val orientation = LocalContext.current.resources.configuration.orientation

    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(false)

    val notificationPermissionState = rememberPermissionState(
        Manifest.permission.POST_NOTIFICATIONS
    )

    LaunchedEffect(notificationPermissionState) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return@LaunchedEffect
        }

        if (notificationPermissionState.status == PermissionStatus.Granted) {
            return@LaunchedEffect
        }

        notificationPermissionState.launchPermissionRequest()
    }

    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }


    return ScaffoldLargeHeader(
        title = {
            Text(
                text = stringResource(R.string.app_name),
            )
        },
        actions = {
            IconButton(onClick = {
                navController.navigate(Screens.Settings())
            }) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
            }
        },
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarPrimaryColor(),
        content = { insets, scrollConnection ->
            LazyColumn(
                modifier = Modifier
                    .nestedScroll(scrollConnection),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                contentPadding = PaddingValues(
                    top = landscapeContentPadding + MaterialTheme.spacing.extraSmall,
                    bottom = insets.calculateBottomPadding() + landscapeContentPadding,
                    start = landscapeContentPadding,
                    end = landscapeContentPadding
                )
            ) {
                item {
                    LanguageSelector(
                        languageViewModel = languageViewModel,
                    )
                }

                item {
                    TranslationTextField(
                        textTranslationViewModel = textTranslationViewModel,
                        onSubmit = {
                            textTranslationViewModel.setTranslateOnInput(true)
                            navController.navigate(Screens.TextTranslation())
                        },
                        onClear = {
                            textTranslationViewModel.clearInput()
                            textTranslationViewModel.clearTranslation()
                        }
                    )
                }

                if (!hasLicense) {
                    item {
                        TrialLicenseCard(
                            licenseViewModel = licenseViewModel,
                        )
                    }
                }

                item {
                    ButtonCard(
                        onClick = {
                            navController.navigate(Screens.Camera())
                        },
                        title = "Vision",
                        subtitle = "Use your camera to translate",
                        icon = Icons.Outlined.CameraAlt,
                        colors = ButtonCardDefaults.colorsPrimary(),
                    )
                }

                item {
                    ButtonCard(
                        onClick = { /*TODO*/ },
                        title = "Voice",
                        subtitle = "Translate a conversation",
                        icon = Icons.Outlined.MicNone,
                        colors = ButtonCardDefaults.colorsSecondary(),
                    )
                }
            }
        }
    )
}

@Preview
@Composable
private fun HomePreview() {
    Home(
        navController = rememberNavController(),
        licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        ),
        textTranslationViewModel = TextTranslationViewModel(
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        languageViewModel = LanguageViewModel(
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        )
    )
}
