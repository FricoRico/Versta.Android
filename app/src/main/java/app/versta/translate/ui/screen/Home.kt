package app.versta.translate.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
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
import app.versta.translate.R
import app.versta.translate.adapter.outbound.ExternalLanguageModelsMemoryRepository
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.adapter.outbound.LicenseMemoryRepository
import app.versta.translate.adapter.outbound.TranslationMockInference
import app.versta.translate.adapter.outbound.TranslationMockTokenizer
import app.versta.translate.adapter.outbound.TranslationPreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.ScaffoldLargeHeaderDefaults
import app.versta.translate.ui.component.TranslationTextField
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Home(
    backStack: NavBackStack,
    licenseViewModel: LicenseViewModel,
    languageViewModel: LanguageViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val hasLicense by licenseViewModel.hasLicense.collectAsStateWithLifecycle(false)

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        null
    }

    LaunchedEffect(notificationPermissionState) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            return@LaunchedEffect
        }

        if (notificationPermissionState?.status == PermissionStatus.Granted) {
            return@LaunchedEffect
        }

        notificationPermissionState?.launchPermissionRequest()
    }

    val orientation = LocalConfiguration.current.orientation
    val landscapeContentPadding = if (orientation == ORIENTATION_LANDSCAPE) {
        MaterialTheme.spacing.medium
    } else {
        MaterialTheme.spacing.small
    }


    return ScaffoldLargeHeader(
        topAppBarColors = ScaffoldLargeHeaderDefaults.topAppBarSurfaceColor(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(R.string.app_name),
            )
        },
        actions = {
            IconButton(onClick = {
                backStack.add(Screens.Settings)
            }) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
            }
        },
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
                            backStack.add(Screens.TextTranslation)
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
            }
        }
    )
}

@Composable
@Preview(showBackground = true)
@SuppressLint("ViewModelConstructorInComposable")
private fun HomePreview() {
    val languageViewModel = LanguageViewModel(
        context = LocalContext.current,
        languageRepository = LanguageMemoryRepository(),
        languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
        externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
    )

    Home(
        backStack = rememberNavBackStack<Screens>(),
        licenseViewModel = LicenseViewModel(
            licenseRepository = LicenseMemoryRepository()
        ),
        textTranslationViewModel = TextTranslationViewModel(
            languageViewModel = languageViewModel,
            translationViewModel = TranslationViewModel(
                intermediateTokenizer = TranslationMockTokenizer(),
                intermediateModel = TranslationMockInference(),
                outputTokenizer = TranslationMockTokenizer(),
                outputModel = TranslationMockInference(),
                translationPreferenceRepository = TranslationPreferenceMemoryRepository(),
                languageViewModel = languageViewModel
            )
        ),
        languageViewModel = LanguageViewModel(
            context = LocalContext.current,
            languageRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository(),
            externalLanguageModelsRepository = ExternalLanguageModelsMemoryRepository()
        )
    )
}
