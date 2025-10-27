package app.versta.translate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.ui.component.ErrorAlertDialog
import app.versta.translate.ui.component.LanguageSelectionDrawer
import app.versta.translate.ui.component.LanguageSuggestionDrawer
import app.versta.translate.ui.component.ModelLoadingProgressDialog
import app.versta.translate.ui.component.Router
import app.versta.translate.ui.component.TrialLicenseConfirmationDialog
import app.versta.translate.ui.component.TrialLicenseDrawer
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.theme.TranslateTheme
import app.versta.translate.utils.setEdgeToEdgeConfig
import app.versta.translate.utils.viewModelFactory

open class MainActivity : ComponentActivity() {
    private var initialRoute by mutableStateOf<Screens?>(null)

    private val _licenseViewModel by viewModels<LicenseViewModel>(
        factoryProducer = {
            viewModelFactory {
                LicenseViewModel(
                    licenseRepository = MainApplication.module.licenseRepository
                )
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ModelFilePicker.registerForActivity(this)
        LogFileSaver.registerForActivity(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            TranslateBubbleShortcut.registerForActivity(this)
        }

        handleStartupAndResume(intent)

        installSplashScreen()
        setEdgeToEdgeConfig()
        setContent {
            TranslateTheme(
                customThemeViewModel = MainApplication.module.customThemeViewModel
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Router(
                        scaffoldViewModel = MainApplication.module.scaffoldViewModel,
                        customThemeViewModel = MainApplication.module.customThemeViewModel,
                        navigationViewModel = MainApplication.module.navigationViewModel,
                        cameraTranslationViewModel = MainApplication.module.cameraTranslationViewModel,
                        languageViewModel = MainApplication.module.languageViewModel,
                        licenseViewModel = _licenseViewModel,
                        translationViewModel = MainApplication.module.translationViewModel,
                        textTranslationViewModel = MainApplication.module.textTranslationViewModel,
                        textToSpeechViewModel = MainApplication.module.textToSpeechViewModel,
                        voiceViewModel = MainApplication.module.voiceViewModel,
                        loggingViewModel = MainApplication.module.loggingViewModel
                    )

                    ModelLoadingProgressDialog(
                        translationViewModel = MainApplication.module.translationViewModel,
                        textTranslationViewModel = MainApplication.module.textTranslationViewModel,
                        textToSpeechViewModel = MainApplication.module.textToSpeechViewModel
                    )

                    ErrorAlertDialog(
                        translationViewModel = MainApplication.module.translationViewModel,
                        textToSpeechViewModel = MainApplication.module.textToSpeechViewModel
                    )

                    TrialLicenseDrawer(
                        licenseViewModel = _licenseViewModel
                    )
                    TrialLicenseConfirmationDialog(
                        licenseViewModel = _licenseViewModel
                    )

                    LanguageSelectionDrawer(languageViewModel = MainApplication.module.languageViewModel)

                    LanguageSuggestionDrawer(languageViewModel = MainApplication.module.languageViewModel)
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

            initialRoute = Screens.TextTranslationLegacy
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}