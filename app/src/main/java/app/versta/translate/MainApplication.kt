package app.versta.translate

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.outbound.ExternalLanguageModelsFileRepository
import app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository
import app.versta.translate.adapter.inbound.FileHashValidator
import app.versta.translate.adapter.inbound.PrecomputedHashFileValidator
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.AudioTrackPlayer
import app.versta.translate.adapter.outbound.DataDatabaseRepository
import app.versta.translate.adapter.outbound.DataRepository
import app.versta.translate.adapter.outbound.ExternalDataFileRepository
import app.versta.translate.adapter.outbound.ExternalDataRepository
import app.versta.translate.adapter.outbound.ExternalVoiceModelsFileRepository
import app.versta.translate.adapter.outbound.ExternalVoiceModelsRepository
import app.versta.translate.adapter.outbound.StyleTextToSpeechInference
import app.versta.translate.adapter.outbound.StyleTextToSpeech2Tokenizer
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.LicenseDataStoreRepository
import app.versta.translate.adapter.outbound.LicenseRepository
import app.versta.translate.adapter.outbound.MarianInference
import app.versta.translate.adapter.outbound.MarianTokenizer
import app.versta.translate.adapter.outbound.VoiceDatabaseRepository
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.TranslationTokenizer
import app.versta.translate.bridge.speech.ESpeakNG
import app.versta.translate.bridge.speech.OpenJTalk
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.core.model.VoiceViewModel
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.utils.FileLoggingTree
import timber.log.Timber
import timber.log.Timber.Forest.plant


val Context.dataStore by preferencesDataStore(name = "preferences")

interface ApplicationModuleInterface {
    val database: DatabaseContainer

    val dataRepository: DataRepository
    val languageRepository: LanguageRepository
    val languagePreferenceRepository: LanguagePreferenceRepository
    val licenseRepository: LicenseRepository
    val translatorPreferenceRepository: TranslationPreferenceRepository
    val voiceRepository: VoiceRepository
    val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository
    val externalLanguageModelsRepository: ExternalLanguageModelsRepository
    val externalVoiceModelsRepository: ExternalVoiceModelsRepository
    val externalDataRepository: ExternalDataRepository

    val languageViewModel: LanguageViewModel
    val translationViewModel: TranslationViewModel
    val textTranslationViewModel: TextTranslationViewModel
    val textToSpeechViewModel: TextToSpeechViewModel
    val voiceViewModel: VoiceViewModel
    val loggingViewModel: LoggingViewModel

    val ortEnvironment: OrtEnvironment
    val extractor: CompressedFileExtractor
    val validator: FileHashValidator
    val eSpeakNG: ESpeakNG
    val openJTalk: OpenJTalk
    val intermediateTranslationTokenizer: TranslationTokenizer
    val intermediateTranslationInference: TranslationInference
    val outputTranslationTokenizer: TranslationTokenizer
    val outputTranslationInference: TranslationInference
    val textToSpeechTokenizer: TextToSpeechTokenizer
    val textToSpeechInference: TextToSpeechInference
}

class ApplicationModule(private val context: Context) : ApplicationModuleInterface {
    override val database = DatabaseContainer(context)

    override val dataRepository: DataRepository by lazy {
        DataDatabaseRepository(database)
    }

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

    override val voiceRepository: VoiceRepository by lazy {
        VoiceDatabaseRepository(database)
    }

    override val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository by lazy {
        TextToSpeechPreferenceDataStoreRepository(context.dataStore)
    }

    override val externalLanguageModelsRepository: ExternalLanguageModelsRepository by lazy {
        ExternalLanguageModelsFileRepository(context.resources.openRawResource(R.raw.versta_translation_models))
    }

    override val externalVoiceModelsRepository: ExternalVoiceModelsRepository by lazy {
        ExternalVoiceModelsFileRepository(context.resources.openRawResource((R.raw.versta_text_to_speech_models)))
    }

    override val externalDataRepository: ExternalDataRepository by lazy {
        ExternalDataFileRepository(context.resources.openRawResource(R.raw.versta_data))
    }

    override val loggingViewModel: LoggingViewModel by lazy {
        LoggingViewModel(context.getExternalFilesDir(null))
    }

    override val languageViewModel: LanguageViewModel by lazy {
        LanguageViewModel(
            context = context,
            languageRepository = languageRepository,
            languagePreferenceRepository = languagePreferenceRepository,
            externalLanguageModelsRepository = externalLanguageModelsRepository
        )
    }

    override val translationViewModel: TranslationViewModel by lazy {
        TranslationViewModel(
            intermediateTokenizer = intermediateTranslationTokenizer,
            intermediateModel = intermediateTranslationInference,
            outputTokenizer = outputTranslationTokenizer,
            outputModel = outputTranslationInference,
            translationPreferenceRepository = translatorPreferenceRepository,
            languageViewModel = languageViewModel
        )
    }

    override val textTranslationViewModel: TextTranslationViewModel by lazy {
        TextTranslationViewModel(
            languageViewModel = languageViewModel,
            translationViewModel = translationViewModel,
        )
    }

    override val textToSpeechViewModel: TextToSpeechViewModel by lazy {
        TextToSpeechViewModel(
            context = context,
            espeakNG = eSpeakNG,
            openJTalk = openJTalk,
            tokenizer = textToSpeechTokenizer,
            model = textToSpeechInference,
            audioPlayer = AudioTrackPlayer(),
            dataRepository = dataRepository,
            voiceRepository = voiceRepository,
            textToSpeechPreferenceRepository = textToSpeechPreferenceRepository,
            languagePreferenceRepository = languagePreferenceRepository,
            externalDataRepository = externalDataRepository
        )
    }

    override val voiceViewModel: VoiceViewModel by lazy {
        VoiceViewModel(
            context = context,
            voiceRepository = voiceRepository,
            externalVoiceModelsRepository = externalVoiceModelsRepository
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

    override val eSpeakNG: ESpeakNG by lazy {
        ESpeakNG()
    }

    override val openJTalk: OpenJTalk by lazy {
        OpenJTalk()
    }

    override val intermediateTranslationTokenizer: TranslationTokenizer by lazy {
        MarianTokenizer()
    }

    override val intermediateTranslationInference: TranslationInference by lazy {
        MarianInference(ortEnvironment)
    }

    override val outputTranslationTokenizer: TranslationTokenizer by lazy {
        MarianTokenizer()
    }

    override val outputTranslationInference: TranslationInference by lazy {
        MarianInference(ortEnvironment)
    }

    override val textToSpeechTokenizer: TextToSpeechTokenizer by lazy {
        StyleTextToSpeech2Tokenizer(eSpeakNG, openJTalk)
    }

    override val textToSpeechInference: TextToSpeechInference by lazy {
        StyleTextToSpeechInference(ortEnvironment)
    }
}

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        handleLogging()
        createNotificationChannels()

        context = this
        module = ApplicationModule(this)
    }

    private fun handleLogging() {
        if (BuildConfig.DEBUG) {
            plant(Timber.DebugTree())
        }

        plant(FileLoggingTree(getExternalFilesDir(null)))
    }

    private fun createNotificationChannels() {
        val translationChannel = NotificationChannel(
            TRANSLATION_NOTIFICATION_CHANNEL_ID,
            getString(R.string.translation_bubbles_notification_channel_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.translation_bubbles_notification_channel_description)
        }

        val downloadChannel = NotificationChannel(
            DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            getString(R.string.download_progress_notification_channel_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
           description = getString(R.string.download_progress_notification_channel_description)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannels(listOf(translationChannel, downloadChannel))
    }

    companion object {
        lateinit var context: Context
        lateinit var module: ApplicationModuleInterface

        const val TRANSLATION_BUBBLE_SHORTCUT_ID = "translation_bubble_shortcut"
        const val TRANSLATION_NOTIFICATION_CHANNEL_ID = "translation_bubble_channel"
        const val TRANSLATION_NOTIFICATION_ID = 1

        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
        const val DOWNLOAD_NOTIFICATION_ID = 2
    }
}