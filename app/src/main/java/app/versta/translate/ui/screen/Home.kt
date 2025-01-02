package app.versta.translate.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Settings
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
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.versta.translate.R
import app.versta.translate.adapter.outbound.LanguageMemoryRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceMemoryRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.ui.component.ButtonCard
import app.versta.translate.ui.component.ButtonCardDefaults
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldLargeHeader
import app.versta.translate.ui.component.TranslationTextField
import app.versta.translate.ui.component.TrialLicenseCard
import app.versta.translate.ui.theme.spacing
import app.versta.translate.utils.TarExtractor

@Composable
fun Home(
    navController: NavHostController,
    licenseViewModel: LicenseViewModel,
    languageViewModel: LanguageViewModel,
    textTranslationViewModel: TextTranslationViewModel
) {
    val scrollBehavior = rememberScrollState()

    return ScaffoldLargeHeader(
        title = {
            Text(
                text = "Welcome back",
            )
        },
        actions = {
            IconButton(onClick = {
                navController.navigate(Screens.Settings())
            }) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
            }
        },
        content = { _, scrollConnection ->
            Column(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                modifier = Modifier
                    .verticalScroll(scrollBehavior)
                    .nestedScroll(scrollConnection)
                    .padding(top = MaterialTheme.spacing.small)
                    .padding(horizontal = MaterialTheme.spacing.small)
            ) {
                LanguageSelector(languageViewModel = languageViewModel)

                TranslationTextField(
                    textTranslationViewModel = textTranslationViewModel,
                    onSubmit = {
                        navController.navigate(Screens.TextTranslation())
                    },
                    onClear = {
                        textTranslationViewModel.clearSourceText()
                        textTranslationViewModel.clearTargetText()
                    }
                )

                TrialLicenseCard(
                    licenseViewModel = licenseViewModel,
                )

                ButtonCard(
                    onClick = {
                        navController.navigate(Screens.Camera())
                    },
                    title = "Vision",
                    subtitle = "Use your camera to translate",
                    icon = Icons.Outlined.CameraAlt,
                    colors = ButtonCardDefaults.colorsPrimary(),
                )

                ButtonCard(
                    onClick = { /*TODO*/ },
                    title = "Voice",
                    subtitle = "Translate a conversation",
                    icon = Icons.Outlined.MicNone,
                    colors = ButtonCardDefaults.colorsSecondary(),
                )
            }
        }
    )
}

@Preview
@Composable
private fun HomePreview() {
    Home(
        navController = rememberNavController(),
        licenseViewModel = LicenseViewModel(),
        textTranslationViewModel = TextTranslationViewModel(
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        ),
        languageViewModel = LanguageViewModel(
            modelExtractor = TarExtractor(LocalContext.current),
            languageDatabaseRepository = LanguageMemoryRepository(),
            languagePreferenceRepository = LanguagePreferenceMemoryRepository()
        )
    )
}
