package app.versta.translate

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.core.model.ModelExtractor
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.adapter.outbound.MarianInference
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.TarExtractor

val Context.dataStore by preferencesDataStore(name = "preferences")

interface ApplicationModuleInterface {
    val languageRepository: LanguageRepository
    val languagePreferenceRepository: LanguagePreferenceDataStoreRepository

    val extractor: ModelExtractor
    val tokenizer: TranslationTokenizer
    val model: TranslationInference
}

class ApplicationModule(context: Context) : ApplicationModuleInterface {
    val database = DatabaseContainer(context)

    override val languageRepository: LanguageRepository by lazy {
        LanguageDatabaseRepository(database)
    }

    override val languagePreferenceRepository: LanguagePreferenceDataStoreRepository by lazy {
        LanguagePreferenceDataStoreRepository(context.dataStore)
    }

    override val extractor: ModelExtractor by lazy {
        TarExtractor(context)
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