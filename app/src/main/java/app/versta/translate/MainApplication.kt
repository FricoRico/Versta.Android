package app.versta.translate

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.view.WindowManager
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.core.entity.TranslationCache
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LicenseViewModel
import app.versta.translate.core.model.ModelExtractor
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.TarExtractor
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.dsl.module

val translateModule = module {
    single { TranslationCache(64) }
    single<ModelExtractor> { TarExtractor(get()) }

    single { LanguageDatabaseRepository(get()) }

    viewModel { LanguageViewModel(get(), get()) }
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