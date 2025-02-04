package app.versta.translate

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.inbound.ModelExtractor
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.adapter.outbound.MarianInference
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.adapter.inbound.ModelExtractorTar

val Context.dataStore by preferencesDataStore(name = "preferences")

interface ApplicationModuleInterface {
    val languageRepository: LanguageRepository
    val languagePreferenceRepository: LanguagePreferenceRepository
    val translatorPreferenceRepository: TranslationPreferenceRepository

    val extractor: ModelExtractor
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

    override val translatorPreferenceRepository: TranslationPreferenceRepository by lazy {
        TranslationPreferenceDataStoreRepository(context.dataStore)
    }

    override val extractor: ModelExtractor by lazy {
        ModelExtractorTar(context)
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
    }
}