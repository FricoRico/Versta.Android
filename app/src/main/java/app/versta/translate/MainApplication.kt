package app.versta.translate

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import app.versta.translate.adapter.inbound.CompressedFileExtractor
import app.versta.translate.adapter.inbound.FileHashValidator
import app.versta.translate.adapter.inbound.PrecomputedHashFileValidator
import app.versta.translate.adapter.inbound.TarballExtractor
import app.versta.translate.adapter.outbound.AudioTrackPlayer
import app.versta.translate.adapter.outbound.BergamotTinyInference
import app.versta.translate.adapter.outbound.DEFAULT_CACHE_SIZE
import app.versta.translate.adapter.outbound.ExternalLanguageModelsFileRepository
import app.versta.translate.adapter.outbound.ExternalLanguageModelsRepository
import app.versta.translate.adapter.outbound.ExternalObjectCharacterRecognitionModelsFileRepository
import app.versta.translate.adapter.outbound.ExternalObjectCharacterRecognitionModelsRepository
import app.versta.translate.adapter.outbound.ExternalSpeechRecognitionModelsFileRepository
import app.versta.translate.adapter.outbound.ExternalSpeechRecognitionModelsRepository
import app.versta.translate.adapter.outbound.ExternalVoiceModelsFileRepository
import app.versta.translate.adapter.outbound.ExternalVoiceModelsRepository
import app.versta.translate.adapter.outbound.LanguageDatabaseRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LanguagePreferenceRepository
import app.versta.translate.adapter.outbound.LanguageRepository
import app.versta.translate.adapter.outbound.LicenseDataStoreRepository
import app.versta.translate.adapter.outbound.LicenseRepository
import app.versta.translate.adapter.outbound.LoggingPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.LoggingPreferenceRepository
import app.versta.translate.adapter.outbound.MnnObjectCharacterRecognition
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionInference
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionRepository
import app.versta.translate.adapter.outbound.ObjectCharacterRecognitionRepositoryDatabaseRepository
import app.versta.translate.adapter.outbound.SpeechRecognitionDatabaseRepository
import app.versta.translate.adapter.outbound.SpeechRecognitionInference
import app.versta.translate.adapter.outbound.SpeechRecognitionRepository
import app.versta.translate.adapter.outbound.Supertonic3Inference
import app.versta.translate.adapter.outbound.Supertonic3Tokenizer
import app.versta.translate.adapter.outbound.TextToSpeechInference
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TextToSpeechPreferenceRepository
import app.versta.translate.adapter.outbound.TextToSpeechTokenizer
import app.versta.translate.adapter.outbound.TranslationInference
import app.versta.translate.adapter.outbound.TranslationPreferenceDataStoreRepository
import app.versta.translate.adapter.outbound.TranslationPreferenceRepository
import app.versta.translate.adapter.outbound.VoiceDatabaseRepository
import app.versta.translate.adapter.outbound.VoiceRepository
import app.versta.translate.adapter.outbound.WhisperSpeechRecognition
import app.versta.translate.bridge.leanmt.Leanmt
import app.versta.translate.core.model.CameraTranslationViewModel
import app.versta.translate.core.model.CustomThemeViewModel
import app.versta.translate.core.model.LanguageViewModel
import app.versta.translate.core.model.LoggingViewModel
import app.versta.translate.core.model.NavigationViewModel
import app.versta.translate.core.model.ObjectCharacterRecognitionViewModel
import app.versta.translate.core.model.ScaffoldActionsComponent
import app.versta.translate.core.model.ScaffoldNavigationIconComponent
import app.versta.translate.core.model.ScaffoldTitleComponent
import app.versta.translate.core.model.ScaffoldViewModel
import app.versta.translate.core.model.SpeechRecognitionViewModel
import app.versta.translate.core.model.TextToSpeechViewModel
import app.versta.translate.core.model.TextTranslationViewModel
import app.versta.translate.core.model.TranslationViewModel
import app.versta.translate.core.model.VoiceViewModel
import app.versta.translate.database.DatabaseContainer
import app.versta.translate.ui.component.LanguageSelector
import app.versta.translate.ui.component.ScaffoldCompactBarMenuNavigationIcon
import app.versta.translate.ui.component.ScaffoldCompactBarSettingsActions
import app.versta.translate.ui.screen.Screens
import app.versta.translate.ui.theme.ObsidianColorScheme
import app.versta.translate.utils.CustomTheme
import app.versta.translate.utils.FileLoggingTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import timber.log.Timber.Forest.plant
import timber.log.Timber.Forest.uproot

val Context.dataStore by preferencesDataStore(name = "preferences")

interface ApplicationModuleInterface {
    val database: DatabaseContainer

    val languageRepository: LanguageRepository
    val languagePreferenceRepository: LanguagePreferenceRepository
    val licenseRepository: LicenseRepository
    val loggingPreferenceRepository: LoggingPreferenceRepository
    val translatorPreferenceRepository: TranslationPreferenceRepository
    val voiceRepository: VoiceRepository
    val textToSpeechPreferenceRepository: TextToSpeechPreferenceRepository
    val externalLanguageModelsRepository: ExternalLanguageModelsRepository
    val externalVoiceModelsRepository: ExternalVoiceModelsRepository
    val externalObjectCharacterRecognitionModelsRepository: ExternalObjectCharacterRecognitionModelsRepository
    val objectCharacterRecognizerRepository: ObjectCharacterRecognitionRepository

    val externalSpeechRecognitionModelsRepository: ExternalSpeechRecognitionModelsRepository
    val speechRecognitionRepository: SpeechRecognitionRepository

    val cameraTranslationViewModel: CameraTranslationViewModel
    val navigationViewModel: NavigationViewModel
    val scaffoldViewModel: ScaffoldViewModel
    val customThemeViewModel: CustomThemeViewModel
    val languageViewModel: LanguageViewModel
    val translationViewModel: TranslationViewModel
    val textTranslationViewModel: TextTranslationViewModel
    val textToSpeechViewModel: TextToSpeechViewModel
    val voiceViewModel: VoiceViewModel
    val objectCharacterRecognitionViewModel: ObjectCharacterRecognitionViewModel
    val loggingViewModel: LoggingViewModel

    val extractor: CompressedFileExtractor
    val validator: FileHashValidator
    val translationService: Leanmt
    val intermediateTranslationInference: TranslationInference
    val outputTranslationInference: TranslationInference
    val textToSpeechTokenizer: TextToSpeechTokenizer
    val textToSpeechInference: TextToSpeechInference
    val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference

    val speechRecognitionInference: SpeechRecognitionInference
    val speechRecognitionViewModel: SpeechRecognitionViewModel
}

class ApplicationModule(
    private val context: Context,
) : ApplicationModuleInterface {
    override val database = DatabaseContainer(context)

    override val languageRepository: LanguageRepository by lazy {
        LanguageDatabaseRepository(database)
    }

    override val languagePreferenceRepository: LanguagePreferenceRepository by lazy {
        LanguagePreferenceDataStoreRepository(context.dataStore)
    }

    override val licenseRepository: LicenseRepository by lazy {
        LicenseDataStoreRepository(context.dataStore)
    }

    override val loggingPreferenceRepository: LoggingPreferenceRepository by lazy {
        LoggingPreferenceDataStoreRepository(context.dataStore)
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

    override val externalObjectCharacterRecognitionModelsRepository: ExternalObjectCharacterRecognitionModelsRepository by lazy {
        ExternalObjectCharacterRecognitionModelsFileRepository(context.resources.openRawResource(R.raw.versta_object_character_recognition_models))
    }

    override val objectCharacterRecognizerRepository: ObjectCharacterRecognitionRepository by lazy {
        ObjectCharacterRecognitionRepositoryDatabaseRepository(database)
    }

    override val externalSpeechRecognitionModelsRepository: ExternalSpeechRecognitionModelsRepository by lazy {
        ExternalSpeechRecognitionModelsFileRepository(context.resources.openRawResource(R.raw.versta_speech_recognition_models))
    }

    override val speechRecognitionRepository: SpeechRecognitionRepository by lazy {
        SpeechRecognitionDatabaseRepository(database)
    }

    override val navigationViewModel: NavigationViewModel by lazy {
        NavigationViewModel(
            initialRoute = Screens.TextTranslation,
        )
    }

    override val scaffoldViewModel: ScaffoldViewModel by lazy {
        ScaffoldViewModel(
            navigationViewModel = navigationViewModel,
            defaultTitle =
                ScaffoldTitleComponent(
                    contentKey = "ScaffoldCompactBarLanguageSelector",
                    component = {
                        LanguageSelector(
                            languageViewModel = languageViewModel,
                        )
                    },
                ),
            defaultNavigationIcon =
                ScaffoldNavigationIconComponent(
                    contentKey = "ScaffoldCompactBarMenuNavigationIcon",
                    component = {
                        ScaffoldCompactBarMenuNavigationIcon(
                            navigationViewModel = navigationViewModel,
                        )
                    },
                ),
            defaultActions =
                ScaffoldActionsComponent(
                    contentKey = "ScaffoldCompactBarSettingsActions",
                    component = {
                        ScaffoldCompactBarSettingsActions(
                            navigationViewModel = navigationViewModel,
                        )
                    },
                ),
        )
    }

    override val cameraTranslationViewModel: CameraTranslationViewModel by lazy {
        CameraTranslationViewModel(
            objectCharacterRecognitionInference = objectCharacterRecognitionInference,
            languageViewModel = languageViewModel,
            languagePreferenceRepository = languagePreferenceRepository,
            translationViewModel = translationViewModel,
            objectCharacterRecognizerRepository = objectCharacterRecognizerRepository,
        )
    }

    override val customThemeViewModel: CustomThemeViewModel by lazy {
        CustomThemeViewModel(
            mapOf(
                CustomTheme.Obsidian to ObsidianColorScheme,
            ),
        )
    }

    override val loggingViewModel: LoggingViewModel by lazy {
        LoggingViewModel(
            context.getExternalFilesDir(null),
            loggingPreferenceRepository,
        )
    }

    override val languageViewModel: LanguageViewModel by lazy {
        LanguageViewModel(
            context = context,
            languageRepository = languageRepository,
            languagePreferenceRepository = languagePreferenceRepository,
            externalLanguageModelsRepository = externalLanguageModelsRepository,
        )
    }

    override val translationViewModel: TranslationViewModel by lazy {
        TranslationViewModel(
            intermediateModel = intermediateTranslationInference,
            outputModel = outputTranslationInference,
            translationPreferenceRepository = translatorPreferenceRepository,
            languageViewModel = languageViewModel,
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
            tokenizer = textToSpeechTokenizer,
            model = textToSpeechInference,
            audioPlayer = AudioTrackPlayer(),
            voiceRepository = voiceRepository,
            textToSpeechPreferenceRepository = textToSpeechPreferenceRepository,
            languagePreferenceRepository = languagePreferenceRepository,
        )
    }

    override val voiceViewModel: VoiceViewModel by lazy {
        VoiceViewModel(
            context = context,
            voiceRepository = voiceRepository,
            externalVoiceModelsRepository = externalVoiceModelsRepository,
        )
    }

    override val objectCharacterRecognitionViewModel: ObjectCharacterRecognitionViewModel by lazy {
        ObjectCharacterRecognitionViewModel(
            context = context,
            objectCharacterRecognitionRepository = objectCharacterRecognizerRepository,
            externalObjectCharacterRecognitionModelsRepository = externalObjectCharacterRecognitionModelsRepository,
        )
    }

    override val speechRecognitionViewModel: SpeechRecognitionViewModel by lazy {
        SpeechRecognitionViewModel(
            context = context,
            speechRecognitionRepository = speechRecognitionRepository,
            externalSpeechRecognitionModelsRepository = externalSpeechRecognitionModelsRepository,
            speechRecognitionInference = speechRecognitionInference,
            languageViewModel = languageViewModel,
        )
    }

    override val extractor: CompressedFileExtractor by lazy {
        TarballExtractor(context)
    }

    override val validator: FileHashValidator by lazy {
        PrecomputedHashFileValidator()
    }

    override val translationService: Leanmt by lazy {
        Leanmt(DEFAULT_CACHE_SIZE.toLong())
    }

    override val intermediateTranslationInference: TranslationInference by lazy {
        BergamotTinyInference(translationService)
    }

    override val outputTranslationInference: TranslationInference by lazy {
        BergamotTinyInference(translationService)
    }

    override val textToSpeechTokenizer: TextToSpeechTokenizer by lazy {
        Supertonic3Tokenizer()
    }

    override val textToSpeechInference: TextToSpeechInference by lazy {
        // The Supertonic3Engine seam binds to the bridge/speech JNI contract
        // when NEU-69 lands; until then synthesis surfaces a loud error.
        Supertonic3Inference()
    }

    override val objectCharacterRecognitionInference: ObjectCharacterRecognitionInference by lazy {
        MnnObjectCharacterRecognition()
    }

    override val speechRecognitionInference: SpeechRecognitionInference by lazy {
        WhisperSpeechRecognition()
    }
}

class MainApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var fileLoggingTree: FileLoggingTree? = null

    override fun onCreate() {
        super.onCreate()

        handleLogging()
        createNotificationChannels()

        context = this
        module = ApplicationModule(this)

        observeLoggingPreference()
    }

    private fun handleLogging() {
        if (BuildConfig.DEBUG) {
            plant(Timber.DebugTree())
        }

        syncFileLoggingTree(diagnosticsEnabled = false)
    }

    private fun observeLoggingPreference() {
        applicationScope.launch {
            module.loggingPreferenceRepository.isLoggingEnabled().collect { enabled ->
                syncFileLoggingTree(diagnosticsEnabled = enabled)
            }
        }
    }

    @Synchronized
    private fun syncFileLoggingTree(diagnosticsEnabled: Boolean) {
        val enabled =
            FileLoggingTree.shouldEnableFileLogging(
                isDebugBuild = BuildConfig.DEBUG,
                diagnosticsEnabled = diagnosticsEnabled,
            )
        val current = fileLoggingTree

        if (enabled && current != null) return

        if (!enabled) {
            if (current != null) {
                uproot(current)
                current.close()
                fileLoggingTree = null
            }
            return
        }

        val tree = FileLoggingTree(getExternalFilesDir(null))
        plant(tree)
        fileLoggingTree = tree
    }

    private fun createNotificationChannels() {
        val translationChannel =
            NotificationChannel(
                TRANSLATION_NOTIFICATION_CHANNEL_ID,
                getString(R.string.translation_bubbles_notification_channel_title),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.translation_bubbles_notification_channel_description)
            }

        val downloadChannel =
            NotificationChannel(
                DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                getString(R.string.download_progress_notification_channel_title),
                NotificationManager.IMPORTANCE_LOW,
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
