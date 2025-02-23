package app.versta.translate

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.LicenseDataStoreRepository
import app.versta.translate.adapter.outbound.LicenseRepository
import app.versta.translate.adapter.outbound.MarianInference
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.database.DatabaseContainer

val Context.dataStore by preferencesDataStore(name = "preferences")

interface ApplicationModuleInterface {
    val languageRepository: LanguageRepository
    val languagePreferenceRepository: LanguagePreferenceRepository
    val licenseRepository: LicenseRepository
    val translatorPreferenceRepository: TranslationPreferenceRepository

    val translationViewModel: TranslationViewModel
    val textTranslationViewModel: TextTranslationViewModel

    val extractor: CompressedFileExtractor
    val tokenizer: TranslationTokenizer
    val model: TranslationInference
}

class ApplicationModule(context: Context) : ApplicationModuleInterface {
    val database = DatabaseContainer(context)

    override val languageRepository: LanguageRepository by lazy {
        LanguageDatabaseRepository(database)
    }

    override val languagePreferenceRepository: LanguagePreferenceRepository by lazy {
        LanguagePreferenceDataStoreRepository(context.dataStore)
    }

    override val licenseRepository: LicenseRepository by lazy {
        LicenseDataStoreRepository(context.dataStore)
    }

    override val translatorPreferenceRepository: TranslationPreferenceRepository by lazy {
        TranslationPreferenceDataStoreRepository(context.dataStore)
    }

    override val translationViewModel: TranslationViewModel by lazy {
        TranslationViewModel(
            tokenizer = MainApplication.module.tokenizer,
            model = MainApplication.module.model,
            languageRepository = MainApplication.module.languageRepository,
            languagePreferenceRepository = MainApplication.module.languagePreferenceRepository,
            translationPreferenceRepository = MainApplication.module.translatorPreferenceRepository
        )
    }

    override val textTranslationViewModel: TextTranslationViewModel by lazy {
        TextTranslationViewModel(
            languagePreferenceRepository = MainApplication.module.languagePreferenceRepository
        )
    }


    override val extractor: CompressedFileExtractor by lazy {
        TarballExtractor(context)
    }

    override val tokenizer: TranslationTokenizer by lazy {
        MarianTokenizer()
    }

    override val model: TranslationInference by lazy {
        MarianInference()
    }
}

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        module = ApplicationModule(this)
    }

    companion object {
        lateinit var module: ApplicationModuleInterface

        const val TRANSLATION_BUBBLE_SHORTCUT_ID = "translation_bubble_shortcut"
        const val TRANSLATION_NOTIFICATION_CHANNEL_ID = "translation_bubble_channel"
        const val TRANSLATION_NOTIFICATION_ID = 1
    }
}