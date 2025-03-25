package app.versta.translate

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.FileHashValidator
import app.versta.translate.adapter.inbound.PrecomputedHashFileValidator
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.AudioTrackPlayer
import app.versta.translate.adapter.outbound.KokoroInference
import app.versta.translate.adapter.outbound.KokoroTokenizer
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.LicenseDataStoreRepository
import app.versta.translate.adapter.outbound.LicenseRepository
import app.versta.translate.adapter.outbound.MarianInference
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.adapter.outbound.TextToSpeechDatabaseRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.FileLoggingTree
import timber.log.Timber
import timber.log.Timber.Forest.plant


val Context.dataStore by preferencesDataStore(name = "preferences")

interface ApplicationModuleInterface {
    val languageRepository: LanguageRepository
    val languagePreferenceRepository: LanguagePreferenceRepository
    val licenseRepository: LicenseRepository
    val translatorPreferenceRepository: TranslationPreferenceRepository
    val textToSpeechRepository: TextToSpeechRepository
    val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository

    val translationViewModel: TranslationViewModel
    val textTranslationViewModel: TextTranslationViewModel
    val textToSpeechViewModel: TextToSpeechViewModel
    val loggingViewModel: LoggingViewModel

    val ortEnvironment: OrtEnvironment
    val extractor: CompressedFileExtractor
    val validator: FileHashValidator
    val translationTokenizer: TranslationTokenizer
    val translationInference: TranslationInference
    val textToSpeechTokenizer: TextToSpeechTokenizer
    val textToSpeechInference: TextToSpeechInference
}

class ApplicationModule(private val context: Context) : ApplicationModuleInterface {
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

    override val textToSpeechRepository: TextToSpeechRepository by lazy {
        TextToSpeechDatabaseRepository(database)
    }

    override val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository by lazy {
        TextToSpeechPreferenceDataStoreRepository(context.dataStore)
    }

    override val loggingViewModel: LoggingViewModel by lazy {
        LoggingViewModel(context.getExternalFilesDir(null))
    }

    override val translationViewModel: TranslationViewModel by lazy {
        TranslationViewModel(
            tokenizer = MainApplication.module.translationTokenizer,
            model = MainApplication.module.translationInference,
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

    override val textToSpeechViewModel: TextToSpeechViewModel by lazy {
        TextToSpeechViewModel(
            tokenizer = MainApplication.module.textToSpeechTokenizer,
            model = MainApplication.module.textToSpeechInference,
            audioPlayer = AudioTrackPlayer(),
            textToSpeechRepository = MainApplication.module.textToSpeechRepository,
            textToSpeechPreferenceRepository = MainApplication.module.textToSpeechPreferenceRepository,
            languagePreferenceRepository = MainApplication.module.languagePreferenceRepository,
        )
    }

    override val ortEnvironment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment(
            OrtLoggingLevel.ORT_LOGGING_LEVEL_FATAL,
            "VerstaInference",
            OrtEnvironment.ThreadingOptions().apply {
                setGlobalSpinControl(true)
            })
    }

    override val extractor: CompressedFileExtractor by lazy {
        TarballExtractor(context)
    }

    override val validator: FileHashValidator by lazy {
        PrecomputedHashFileValidator()
    }

    override val translationTokenizer: TranslationTokenizer by lazy {
        MarianTokenizer()
    }

    override val translationInference: TranslationInference by lazy {
        MarianInference(ortEnvironment)
    }

    override val textToSpeechTokenizer: TextToSpeechTokenizer by lazy {
        ESpeakNG.createSession(
            context,
            extractor,
            validator
        )

        KokoroTokenizer()
    }

    override val textToSpeechInference: TextToSpeechInference by lazy {
        KokoroInference(ortEnvironment)
    }
}

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        handleLogging()

        module = ApplicationModule(this)
    }

    private fun handleLogging() {
        if (BuildConfig.DEBUG) {
            plant(Timber.DebugTree())
        }

        plant(FileLoggingTree(getExternalFilesDir(null)))
    }

    companion object {
        lateinit var module: ApplicationModuleInterface

        const val TRANSLATION_BUBBLE_SHORTCUT_ID = "translation_bubble_shortcut"
        const val TRANSLATION_NOTIFICATION_CHANNEL_ID = "translation_bubble_channel"
        const val TRANSLATION_NOTIFICATION_ID = 1
    }
}