package app.versta.translate

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.ModelExtractor
import app.versta.translate.core.model.TextRecognitionViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.adapter.outbound.MarianInference
import app.versta.translate.core.model.ModelInterface
import app.versta.translate.core.model.TokenizerInterface
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.TarExtractor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

val Context.dataStore by preferencesDataStore(name = "preferences")

val translateModule = module {
    single<ModelExtractor> { TarExtractor(get()) }
    single<TokenizerInterface> { MarianTokenizer() }
    single<ModelInterface> { MarianInference() }

    single { get<Context>().dataStore }

    single { LanguageDatabaseRepository(get()) }
    single { LanguagePreferenceRepository(get()) }

    viewModel { LanguageViewModel(get(), get(), get()) }
    viewModel { TranslationViewModel(get(), get(), get(), get()) }
    viewModel { TextRecognitionViewModel() }
    viewModel { TextTranslationViewModel() }
    viewModel { LicenseViewModel() }
}

val databaseModule = module {
    single { DatabaseContainer(get()) }
}

class MainApplication : Application(){
    @SuppressLint("NewApi")
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(level = Level.DEBUG)
            androidContext(this@MainApplication)

            modules(translateModule, databaseModule)
        }
    }
}