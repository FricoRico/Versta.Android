package app.versta.translate

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.versta.translate.adapter.inbound.ModelFilePicker
import app.versta.translate.adapter.inbound.TranslateBubbleNotification
import app.versta.translate.adapter.inbound.TranslateBubbleShortcut
import app.versta.translate.adapter.outbound.LogFileSaver
import app.versta.translate.core.model.LanguageImportViewModel
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.component.LanguageSelectionDrawer
import app.versta.translate.ui.component.TrialLicenseDrawer
import app.versta.translate.ui.component.Router
import app.versta.translate.ui.component.TranslationErrorAlertDialog
import app.versta.translate.ui.component.TranslatorLoadingProgressDialog
import app.versta.translate.ui.component.TrialLicenseConfirmationDialog
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.theme.TranslateTheme
import app.versta.translate.utils.viewModelFactory
import timber.log.Timber

open class MainActivity : ComponentActivity() {
    private val languageViewModel by viewModels<LanguageViewModel>(
        factoryProducer = {
            viewModelFactory {
                LanguageViewModel(
                    languageRepository = MainApplication.module.languageRepository,
                    languagePreferenceRepository = MainApplication.module.languagePreferenceRepository
                )
            }
        }
    )

    private val languageImportViewModel by viewModels<LanguageImportViewModel>(
        factoryProducer = {
            viewModelFactory {
                LanguageImportViewModel(
                    modelExtractor = MainApplication.module.extractor,
                    languageRepository = MainApplication.module.languageRepository
                )
            }
        }
    )

    private val licenseViewModel by viewModels<LicenseViewModel>(
        factoryProducer = {
            viewModelFactory {
                LicenseViewModel(
                    licenseRepository = MainApplication.module.licenseRepository
                )
            }
        }
    )

    private var initialRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ModelFilePicker.registerForActivity(this)
        LogFileSaver.registerForActivity(this)
        TranslateBubbleShortcut.registerForActivity(this)
        TranslateBubbleNotification.registerForActivity(this)

        handleStartupAndResume(intent)

        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            TranslateTheme {
                Surface (
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Router(
                        startDestination = initialRoute,
                        languageViewModel = languageViewModel,
                        languageImportViewModel = languageImportViewModel,
                        licenseViewModel = licenseViewModel,
                        translationViewModel = MainApplication.module.translationViewModel,
                        textTranslationViewModel = MainApplication.module.textTranslationViewModel,
                        loggingViewModel = MainApplication.module.loggingViewModel
                    )

                    TranslatorLoadingProgressDialog(
                        translationViewModel = MainApplication.module.translationViewModel,
                        textTranslationViewModel = MainApplication.module.textTranslationViewModel
                    )

                    TranslationErrorAlertDialog(
                        translationViewModel = MainApplication.module.translationViewModel,
                    )

                    TrialLicenseDrawer(
                        licenseViewModel = licenseViewModel
                    )
                    TrialLicenseConfirmationDialog(
                        licenseViewModel = licenseViewModel
                    )

                    LanguageSelectionDrawer(languageViewModel = languageViewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        handleStartupAndResume(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        handleStartupAndResume(intent)
    }

    private fun handleStartupAndResume(intent: Intent) {
        TranslateBubbleNotification.clearNotification(this)

        val input = intent.getStringExtra("input")
        if (input != null) {
            MainApplication.module.textTranslationViewModel.setTranslateOnInput(true)
            MainApplication.module.textTranslationViewModel.setInput(input)

            initialRoute = Screens.TextTranslation()
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}