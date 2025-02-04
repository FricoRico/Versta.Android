package app.versta.translate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.TextRecognitionViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.ui.component.LanguageSelectionDrawer
import app.versta.translate.ui.component.Router
import app.versta.translate.ui.component.TranslatorLoadingProgressDialog
import app.versta.translate.ui.theme.TranslateTheme
import app.versta.translate.adapter.inbound.ModelFilePickerLauncher
import app.versta.translate.utils.viewModelFactory

open class MainActivity : ComponentActivity() {
    private val languageViewModel by viewModels<LanguageViewModel>(
        factoryProducer = {
            viewModelFactory {
                LanguageViewModel(
                    modelExtractor = MainApplication.module.extractor,
                    languageRepository = MainApplication.module.languageRepository,
                    languagePreferenceRepository = MainApplication.module.languagePreferenceRepository
                )
            }
        }
    )

    private val translationViewModel by viewModels<TranslationViewModel>(
        factoryProducer = {
            viewModelFactory {
                TranslationViewModel(
                    tokenizer = MainApplication.module.tokenizer,
                    model = MainApplication.module.model,
                    languageRepository = MainApplication.module.languageRepository,
                    languagePreferenceRepository = MainApplication.module.languagePreferenceRepository,
                    translationPreferenceRepository = MainApplication.module.translatorPreferenceRepository
                )
            }
        }
    )

    private val textRecognitionViewModel by viewModels<TextRecognitionViewModel>(
        factoryProducer = {
            viewModelFactory {
                TextRecognitionViewModel()
            }
        }
    )

    private val textTranslationViewModel by viewModels<TextTranslationViewModel>(
        factoryProducer = {
            viewModelFactory {
                TextTranslationViewModel(
                    languagePreferenceRepository = MainApplication.module.languagePreferenceRepository
                )
            }
        }
    )

    private val licenseViewModel by viewModels<LicenseViewModel>(
        factoryProducer = {
            viewModelFactory {
                LicenseViewModel()
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ModelFilePickerLauncher.registerForActivity(this)

        enableEdgeToEdge()
        setContent {
            TranslateTheme {
                Box(
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    Router(
                        languageViewModel = languageViewModel,
                        licenseViewModel = licenseViewModel,
                        textTranslationViewModel = textTranslationViewModel,
                        textRecognitionViewModel = textRecognitionViewModel,
                        translationViewModel = translationViewModel
                    )

                    TranslatorLoadingProgressDialog(
                        translationViewModel = translationViewModel,
                        textTranslationViewModel = textTranslationViewModel
                    )
                    LanguageSelectionDrawer(languageViewModel = languageViewModel)
                }
            }
        }
    }

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
    }
}