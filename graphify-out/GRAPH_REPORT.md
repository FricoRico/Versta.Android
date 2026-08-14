# Graph Report - Versta.Android  (2026-08-14)

## Corpus Check
- 288 files · ~157,511 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3046 nodes · 6819 edges · 165 communities (147 shown, 18 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 84 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `1fdda4dd`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- PaddleOCR
- MeshGradientBackground.kt
- TextTranslation.kt
- StyleTextToSpeechInference
- Recognizer
- NodeFeature
- ScaffoldCompactBarTitle
- OcrTextAnalyzer
- DESIGN.md
- VoiceGender
- WhisperSpeechRecognitionTest
- PaddleObjectCharacterRecognitionInference.kt
- Code Review and Quality
- ScaffoldCompactBarBackNavigationIcon
- CameraTranslationViewModel
- Test-Driven Development
- LanguagePreferenceRepository
- ObjectCharacterRecognitionMetadata.kt
- LanguagePair
- ScaffoldViewModel
- ExternalLanguageModelsMemoryRepository
- BergamotTinyInference
- MainApplication.kt
- ExternalSpeechRecognitionModelDefinition
- TranslationPreferenceRepository
- SpeechContextStoreTest
- ObjectCharacterRecogniserResult
- .definition
- ObjectCharacterRecognitionRepositoryMemoryRepository
- SpeechRecognitionWithFiles
- Jetpack Compose Component Library
- Language
- PaddleObjectCharacterRecognitionRecognizeOutput
- Code Simplification
- SpeechRecognitionViewModel
- DownloadWorker
- DataWithFiles
- Android Navigation Patterns
- LanguageRepository
- TextTranslationViewModel
- NavigationViewModel
- DatabaseContainer
- Source First License 1.1
- Android Kotlin Development
- OcrTextAnalyzer.kt
- DownloadStatus
- Conversation.kt
- HttpDownloadClient
- LicenseViewModel
- PaddleObjectCharacterRecognitionDetectOutput
- DataMetadata.kt
- SettingsButtonItem
- Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage
- Material Design 3 Theming
- OpenJTalk
- JapaneseTransliterator
- LanguageMemoryRepository
- VoiceModelMetadata.kt
- ObjectCharacterRecognitionRepositoryDatabaseRepository
- ESpeakNG
- Theme.kt
- TranslationPreferenceDataStoreRepository
- espeak_ng.cc
- TranslationPreferenceMemoryRepository
- ExternalVoiceModelDefinition
- TextToSpeechViewModel
- DownloadManager
- TranslationViewModel
- ObjectCharacterRecognitionRecognizerWithFiles
- TarballExtractor
- VoiceWithModelFiles
- SpeechRecognitionInferenceFiles
- JNIEnv
- VoiceMemoryRepository.kt
- TextTranslationLegacy.kt
- MicrophoneCapture
- whisper.cc
- CustomThemeViewModel
- Whisper
- tasteskill: Anti-Slop Frontend Skill
- Appendix B - Canonical Sources (read these before reinventing)
- WhisperSpeechRecognition
- MainActivity.kt
- Clean Code - Pragmatic AI Coding Standards
- LogFileSaver
- ObjectCharacterRecognitionRepository
- LanguagePairBadge
- Screens
- PaddleOCR
- BubbleActivity.kt
- Timber
- SpeechRecognitionEngine
- LoggingViewModel
- App Layer Conventions
- SpeechRecognitionWithFilesTest
- ExternalObjectCharacterRecognitionModelDefinition
- Spacing.kt
- VoiceWaveform.kt
- 4. DESIGN ENGINEERING DIRECTIVES (Bias Correction)
- ScaffoldModalBottomSheet
- WhisperModelHandle
- Writing Plans
- ModelFilePicker
- screen/CameraTranslation.kt
- GDPR Data Handling
- ExternalSpeechRecognitionModelsFileRepositoryTest
- TranslateBubbleShortcut
- SpeechRecognitionMetadataTest
- TranslateBubbleNotification
- ExternalVoiceModelsFileRepository
- TextToSpeechViewModel.kt
- Versta.Android
- C++ Coding Standards
- Serializable
- 10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know)
- Verification Before Completion
- MockSpeechRecognitionEngine
- SpeechRecognitionInitialPromptsTest
- WhisperModel
- LanguageDetails.kt
- DataModel
- SpeechRecognitionDatabaseRepository
- Implementation Patterns
- PaddleObjectCharacterRecognitionTokenizer
- TranslationSettings.kt
- 9. AI TELLS (Forbidden Patterns)
- Core Concepts
- Mobile Touch Animation
- ExternalVoiceModelsRepository
- TextToSpeechInference
- Router.kt
- FontWeight
- Native Header & Binding
- APPENDICES - Real Source-Backed Reference Material
- 11. REDESIGN PROTOCOL
- 3. DEFAULT ARCHITECTURE & CONVENTIONS
- 6. PERFORMANCE & ACCESSIBILITY GUARDRAILS
- Android Mobile Design
- Java_app_versta_translate_bridge_whisper_Whisper_create
- LanguageDetect
- SpeechContextStore
- AudioExtensionsTest
- AudioCapture
- Java_app_versta_translate_bridge_tokenize_Vocabulary_load
- TrailingProbe
- ExternalLanguageModelTest
- 12. THE BLOCK LIBRARY (Contract - Implementations Land Here Iteratively)
- 5. CONTEXT-AWARE PROACTIVITY
- 8. DARK MODE PROTOCOL
- Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer
- TranslateNotificationActivity.kt
- MosesPunctuationNormalizer
- LanguageViewModel
- gradlew
- 7. DIAL DEFINITIONS (Technical Reference)
- ViewModelProvider
- MockModel
- StyleTextToSpeech2Tokenizer
- ContentColor.kt
- SliderLogarithmic.kt
- plan-document-reviewer-prompt.md

## God Nodes (most connected - your core abstractions)
1. `NavigationViewModel` - 99 edges
2. `Language` - 91 edges
3. `ScaffoldViewModel` - 80 edges
4. `LanguageViewModel` - 77 edges
5. `ApplicationModule` - 73 edges
6. `Recognizer` - 62 edges
7. `LanguagePair` - 60 edges
8. `TextToSpeechViewModel` - 57 edges
9. `ScaffoldComponentProvider()` - 52 edges
10. `PaddleOCR` - 51 edges

## Surprising Connections (you probably didn't know these)
- `DownloadWorker` --calls--> `HttpDownloadClient`  [INFERRED]
  app/src/main/java/app/versta/translate/adapter/inbound/DownloadWorker.kt → app/src/main/java/app/versta/translate/adapter/inbound/HttpDownloadClient.kt
- `WhisperSpeechRecognition` --calls--> `SpeechContextStore`  [INFERRED]
  app/src/main/java/app/versta/translate/adapter/outbound/WhisperSpeechRecognition.kt → app/src/main/java/app/versta/translate/adapter/outbound/SpeechContextStore.kt
- `LanguageModelPair` --calls--> `LanguagePair`  [INFERRED]
  app/src/main/java/app/versta/translate/core/entity/LanguageWithModelFiles.kt → app/src/main/java/app/versta/translate/core/entity/Language.kt
- `LanguageViewModel` --calls--> `DownloadManager`  [INFERRED]
  app/src/main/java/app/versta/translate/core/model/LanguageViewModel.kt → app/src/main/java/app/versta/translate/core/model/DownloadManager.kt
- `ObjectCharacterRecognitionViewModel` --calls--> `DownloadManager`  [INFERRED]
  app/src/main/java/app/versta/translate/core/model/ObjectCharacterRecognitionViewModel.kt → app/src/main/java/app/versta/translate/core/model/DownloadManager.kt

## Import Cycles
- None detected.

## Communities (165 total, 18 thin omitted)

### Community 0 - "PaddleOCR"
Cohesion: 0.06
Nodes (47): argmax(), jboolean, jfloat, jint, jintArray, jlong, JNIEnv, JNIEXPORT (+39 more)

### Community 1 - "MeshGradientBackground.kt"
Cohesion: 0.08
Nodes (37): Color, MeshGradientBackgroundTest, ButtonCard(), ButtonCardColors, ButtonCardDefaults, ButtonCardPreview(), Color, ImageVector (+29 more)

### Community 2 - "TextTranslation.kt"
Cohesion: 0.09
Nodes (24): AudioMockPlayer, ByteArray, FloatArray, DataMemoryRepository, ExternalDataMemoryRepository, FloatArray, LongArray, TextToSpeechMockInference (+16 more)

### Community 3 - "StyleTextToSpeechInference"
Cohesion: 0.07
Nodes (22): ByteBuffer, FloatArray, LongArray, OrtSession, Waveform, StyleTextToSpeechInference, Buffer, TensorUtils (+14 more)

### Community 4 - "Recognizer"
Cohesion: 0.04
Nodes (50): Recognizer, abort_count, abort_deadline, callback_method, callback_obj, carried_prompt_ids, commit_compute_ms, commit_rate_ema_ms_per_sec (+42 more)

### Community 5 - "NodeFeature"
Cohesion: 0.06
Nodes (54): jlong, JNIEnv, JNIEXPORT, jobject, jstring, findModel(), findService(), Java_app_versta_translate_bridge_leanmt_Leanmt_create() (+46 more)

### Community 6 - "ScaffoldCompactBarTitle"
Cohesion: 0.18
Nodes (19): ExternalSpeechRecognitionModels, Color, Dp, Modifier, LanguageBadge(), LanguageBadgeColors, LanguageBadgeDefaults, LanguageBadgePreview() (+11 more)

### Community 7 - "OcrTextAnalyzer"
Cohesion: 0.10
Nodes (28): jfloat, jint, jlong, JNIEnv, JNIEXPORT, jobject, Mat, Scalar (+20 more)

### Community 8 - "DESIGN.md"
Cohesion: 0.05
Nodes (37): Border Radius Scale, Brand & Accent, Breakpoints, Buttons, Cards & Containers, Collapsing Strategy, Colors, Components (+29 more)

### Community 9 - "VoiceGender"
Cohesion: 0.11
Nodes (7): Flow, TextToSpeechPreferenceDataStoreRepository, Flow, TextToSpeechPreferenceRepository, VoiceGender, Female, Male

### Community 10 - "WhisperSpeechRecognitionTest"
Cohesion: 0.17
Nodes (3): FloatArray, MockAudioCapture, WhisperSpeechRecognitionTest

### Community 11 - "PaddleObjectCharacterRecognitionInference.kt"
Cohesion: 0.13
Nodes (15): ImageProxy, ObjectCharacterRecognitionInference, AutoCloseable, ByteBuffer, ImageProxy, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognition (+7 more)

### Community 12 - "Code Review and Quality"
Cohesion: 0.07
Nodes (29): 1. Correctness, 2. Readability & Simplicity, 3. Architecture, 4. Security, 5. Performance, Change Descriptions, Change Sizing, Code Review and Quality (+21 more)

### Community 13 - "ScaffoldCompactBarBackNavigationIcon"
Cohesion: 0.26
Nodes (11): ScaffoldCompactBarBackNavigationIcon(), ApplicationLogs(), FileSaverCallback, ApplicationLogsPreview(), FileSaverCallback, PaddingValues, Uri, PaddingValues (+3 more)

### Community 14 - "CameraTranslationViewModel"
Cohesion: 0.12
Nodes (11): CameraTranslationResult, CameraTranslationViewModel, Context, Flow, LifecycleOwner, StateFlow, ViewModel, Camera (+3 more)

### Community 15 - "Test-Driven Development"
Cohesion: 0.07
Nodes (29): Browser Testing with DevTools, Common Rationalizations, DAMP Over DRY in Tests, Decision Guide, Discover the Stack First, Name Tests Descriptively, One Assertion Per Concept, Overview (+21 more)

### Community 16 - "LanguagePreferenceRepository"
Cohesion: 0.10
Nodes (5): Flow, LanguagePreferenceDataStoreRepository, Flow, LanguagePreferenceRepository, LanguageOptionPair

### Community 17 - "ObjectCharacterRecognitionMetadata.kt"
Cohesion: 0.11
Nodes (12): ObjectCharacterRecognitionBundleMetadata, ObjectCharacterRecognitionDetectorFilesMetadata, ObjectCharacterRecognitionDetectorInferenceFilesMetadata, ObjectCharacterRecognitionDetectorMetadata, ObjectCharacterRecognitionMetadataFile, ObjectCharacterRecognitionModule, Detector, Recognizer (+4 more)

### Community 18 - "LanguagePair"
Cohesion: 0.09
Nodes (7): Flow, LanguageDatabaseRepository, LanguagePair, PivotPair, PivotPairModelFiles, LanguageDatabaseModel, LanguageModelDatabaseModel

### Community 19 - "ScaffoldViewModel"
Cohesion: 0.12
Nodes (34): NavKey, ScaffoldComponent, ScaffoldRowScopeComponent, ViewModel, ScaffoldActionsComponent, ScaffoldBottomBarComponent, ScaffoldComponentMetadata, ScaffoldComponents (+26 more)

### Community 20 - "ExternalLanguageModelsMemoryRepository"
Cohesion: 0.13
Nodes (14): ExternalLanguageModelsFileRepository, Flow, ExternalLanguageModelsMemoryRepository, Flow, ExternalLanguageModelsRepository, Flow, Flow, ExternalLanguageMetadata (+6 more)

### Community 21 - "BergamotTinyInference"
Cohesion: 0.07
Nodes (14): BergamotTinyInferenceTest, MockTranslationEngine, LeanmtTest, BergamotTinyInference, TranslationInference, Leanmt, AutoCloseable, LeanmtModel (+6 more)

### Community 22 - "MainApplication.kt"
Cohesion: 0.11
Nodes (17): FileHashValidator, PrecomputedHashFileValidator, DataRepository, ExternalDataFileRepository, ExternalDataRepository, Flow, LicenseDataStoreRepository, Flow (+9 more)

### Community 23 - "ExternalSpeechRecognitionModelDefinition"
Cohesion: 0.16
Nodes (8): ExternalSpeechRecognitionModelsFileRepository, Flow, ExternalSpeechRecognitionModelsMemoryRepository, Flow, ExternalSpeechRecognitionModelsRepository, Flow, ExternalSpeechRecognitionModelDefinition, ExternalSpeechRecognitionModelWithState

### Community 26 - "ObjectCharacterRecogniserResult"
Cohesion: 0.16
Nodes (7): OcrPostProcessor, OcrPostProcessorContext, Color, ParagraphGroupingPostProcessor, TextStyleAnalysisPostProcessor, ObjectCharacterRecogniserColors, ObjectCharacterRecogniserResult

### Community 28 - "ObjectCharacterRecognitionRepositoryMemoryRepository"
Cohesion: 0.12
Nodes (7): Flow, ObjectCharacterRecognitionRepositoryMemoryRepository, ObjectCharacterRecognitionArchitecture, PaddleOCR, ObjectCharacterRecognitionDetectorInferenceFiles, ObjectCharacterRecognitionRecognizerInferenceFiles, ObjectCharacterRecognitionRecognizerTokenizerFiles

### Community 29 - "SpeechRecognitionWithFiles"
Cohesion: 0.09
Nodes (14): Flow, SpeechRecognitionMemoryRepository, Flow, SpeechRecognitionRepository, SpeechRecognitionArchitecture, Whisper, SpeechRecognitionBundleMetadata, SpeechRecognitionFilesMetadata (+6 more)

### Community 30 - "Jetpack Compose Component Library"
Cohesion: 0.09
Nodes (22): Alert Dialog, Animated Content, Animated Visibility, Animations, Basic LazyColumn, Content Loading Pattern, Date and Time Pickers, Dialogs and Bottom Sheets (+14 more)

### Community 31 - "Language"
Cohesion: 0.10
Nodes (11): Flow, LanguagePreferenceMemoryRepository, Context, Language, Context, CornerBasedShape, Modifier, PaddingValues (+3 more)

### Community 32 - "PaddleObjectCharacterRecognitionRecognizeOutput"
Cohesion: 0.15
Nodes (8): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognitionRecognizeInput, PaddleObjectCharacterRecognitionRecognizeOutput

### Community 33 - "Code Simplification"
Cohesion: 0.09
Nodes (21): 1. Preserve Behavior Exactly, 2. Follow Project Conventions, 3. Prefer Clarity Over Cleverness, 4. Maintain Balance, 5. Scope to What Changed, Code Simplification, Common Rationalizations, Language-Specific Guidance (+13 more)

### Community 34 - "SpeechRecognitionViewModel"
Cohesion: 0.12
Nodes (12): supportedLanguageIsoCodes(), CoroutineScope, FloatArray, Flow, StateFlow, ViewModel, LoadRequest, SpeechRecognitionViewModel (+4 more)

### Community 35 - "DownloadWorker"
Cohesion: 0.17
Nodes (9): DownloadSpeechRecognitionWorker, DownloadQueue, DownloadWorker, DownloadListener, DownloadListener, Intent, CoroutineWorker, ForegroundInfo (+1 more)

### Community 36 - "DataWithFiles"
Cohesion: 0.09
Nodes (15): Flow, Flow, ExternalDataDefinitions, Flow, ExternalDataDefinitions, Flow, ExternalDataDefinitions, Flow (+7 more)

### Community 37 - "Android Navigation Patterns"
Cohesion: 0.10
Nodes (20): Android Navigation Patterns, Back Handler, Basic Deep Link Setup, Basic Navigation, Bottom Nav with Badges, Bottom Navigation, Deep Linking, Handling Intent in Activity (+12 more)

### Community 38 - "LanguageRepository"
Cohesion: 0.09
Nodes (12): DownloadLanguageWorker, Flow, LanguageRepository, LanguageBundleData, LanguageBundleMetadata, LanguageMetadata, LanguageModelArchitecture, BergamotTinyModel (+4 more)

### Community 39 - "TextTranslationViewModel"
Cohesion: 0.13
Nodes (6): Transliteration, TransliterationAdapter, Context, Flow, ViewModel, TextTranslationViewModel

### Community 40 - "NavigationViewModel"
Cohesion: 0.14
Nodes (12): NavKey, ViewModel, NavigationViewModel, Modifier, ModalDrawerItem(), NavigationDrawer(), NavigationDrawerRailItem(), NavigationItem (+4 more)

### Community 41 - "DatabaseContainer"
Cohesion: 0.16
Nodes (9): VoiceModelArchitecture, Kokoro, StyleTTS2, DatabaseContainer, Migration, Migration3, Context, Migration4 (+1 more)

### Community 42 - "Source First License 1.1"
Cohesion: 0.10
Nodes (18): Acceptance, Copyright License, Definitions, Fair Use, Limitations, No Liability, No Other Rights, Notices (+10 more)

### Community 43 - "Android Kotlin Development"
Cohesion: 0.11
Nodes (15): Jetpack Compose UI, Jetpack Compose UI, Models & API Service, Models & API Service, MVVM ViewModels with Jetpack, MVVM ViewModels with Jetpack, Android Kotlin Development, Best Practices (+7 more)

### Community 44 - "OcrTextAnalyzer.kt"
Cohesion: 0.22
Nodes (7): AutoCloseable, Buffer, ByteBuffer, ImageProxy, IntArray, OcrTextAnalyzer, OcrTextMetrics

### Community 45 - "DownloadStatus"
Cohesion: 0.13
Nodes (10): Cancelled, Completed, DownloadStatus, Error, Idle, Processing, Progress, Queued (+2 more)

### Community 46 - "Conversation.kt"
Cohesion: 0.26
Nodes (15): GradientMicButton(), GradientMicButtonDarkPreview(), GradientMicButtonLightPreview(), Modifier, Conversation(), ConversationLanguageChip(), ConversationNoModel(), ConversationPermissionDenied() (+7 more)

### Community 47 - "HttpDownloadClient"
Cohesion: 0.14
Nodes (9): DownloadClient, DownloadListener, URI, HttpDownloadClient, Callback, DownloadListener, URI, Call (+1 more)

### Community 48 - "LicenseViewModel"
Cohesion: 0.11
Nodes (29): Flow, LicenseMemoryRepository, DialogState, Closed, Confirm, Open, StateFlow, ViewModel (+21 more)

### Community 49 - "PaddleObjectCharacterRecognitionDetectOutput"
Cohesion: 0.19
Nodes (7): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, PaddleObjectCharacterRecognitionDetectInput, PaddleObjectCharacterRecognitionDetectOutput

### Community 50 - "DataMetadata.kt"
Cohesion: 0.27
Nodes (5): DataMetadata, DataMetadataInterface, TextToSpeechDataFilesMetadata, TextToSpeechDataMetadata, TextToSpeechDataMetadataFile

### Community 51 - "SettingsButtonItem"
Cohesion: 0.29
Nodes (10): Color, Composable, Dp, ListItemColors, Modifier, PaddingValues, SettingsButtonItem(), SettingsButtonItemContent() (+2 more)

### Community 52 - "Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage"
Cohesion: 0.15
Nodes (18): jlong, JNIEnv, JNIEXPORT, jobject, jstring, DetectionResult, confidence, isReliable (+10 more)

### Community 53 - "Material Design 3 Theming"
Cohesion: 0.11
Nodes (17): Color Roles Usage, Color System, Custom Color Scheme, Custom Fonts, Custom Shape Usage, Dynamic Color (Material You), Elevation and Shadows, Extended Colors (+9 more)

### Community 54 - "OpenJTalk"
Cohesion: 0.23
Nodes (4): AutoCloseable, Flow, MutableStateFlow, OpenJTalk

### Community 55 - "JapaneseTransliterator"
Cohesion: 0.21
Nodes (4): GenericTransliterator, JapaneseTransliterator, Token, Transliterator

### Community 56 - "LanguageMemoryRepository"
Cohesion: 0.14
Nodes (7): LanguageMemoryRepository, ExternalLanguageDownloadTask, LanguageModel, PaddingValues, Languages(), LanguageSettings(), PreviewLanguageSettings()

### Community 57 - "VoiceModelMetadata.kt"
Cohesion: 0.13
Nodes (8): DownloadVoiceWorker, VoiceBundleMetadata, VoiceInferenceFilesMetadata, VoiceMetadataFile, VoiceModel, VoiceModelFilesMetadata, VoiceModelMetadata, VoiceTokenizerFilesMetadata

### Community 58 - "ObjectCharacterRecognitionRepositoryDatabaseRepository"
Cohesion: 0.21
Nodes (3): ObjectCharacterRecognitionRepositoryDatabaseRepository, ObjectCharacterRecognitionDetectorDatabaseModel, ObjectCharacterRecognitionRecognizerDatabaseModel

### Community 59 - "ESpeakNG"
Cohesion: 0.13
Nodes (6): ESpeakNG, AutoCloseable, ByteArray, Flow, MutableStateFlow, SynthReadyCallback

### Community 60 - "Theme.kt"
Cohesion: 0.25
Nodes (17): animateColorScheme(), AnimatedBackgroundColors, AnimatedColors, animatedPrimaryColors(), AnimatedSurfaceColors, defaultColorTransitionSpec(), isLAppearanceLight(), Color (+9 more)

### Community 62 - "espeak_ng.cc"
Cohesion: 0.26
Nodes (16): JNIEnv, JNIEXPORT, jobject, jstring, getJniEnv(), Java_app_versta_translate_bridge_speech_ESpeakNG_cancel(), Java_app_versta_translate_bridge_speech_ESpeakNG_construct(), Java_app_versta_translate_bridge_speech_ESpeakNG_initialize() (+8 more)

### Community 63 - "TranslationPreferenceMemoryRepository"
Cohesion: 0.12
Nodes (6): TranslationMockInference, Flow, TranslationPreferenceMemoryRepository, Modifier, TranslationTextField(), TranslationTextFieldMinimalPreview()

### Community 64 - "ExternalVoiceModelDefinition"
Cohesion: 0.18
Nodes (6): ExternalVoiceModelDefinitions, Flow, ExternalVoice, ExternalVoiceDownloadTask, ExternalVoiceModelDefinition, ExternalVoiceModels

### Community 65 - "TextToSpeechViewModel"
Cohesion: 0.15
Nodes (3): FloatArray, Job, TextToSpeechViewModel

### Community 66 - "DownloadManager"
Cohesion: 0.17
Nodes (4): DownloadManager, Context, T, WorkRequest

### Community 67 - "TranslationViewModel"
Cohesion: 0.12
Nodes (13): Completed, Error, Idle, InProgress, Flow, Job, StateFlow, ViewModel (+5 more)

### Community 68 - "ObjectCharacterRecognitionRecognizerWithFiles"
Cohesion: 0.31
Nodes (6): ExternalObjectCharacterRecognitionModelsFileRepository, Flow, ExternalObjectCharacterRecognitionModels, ExternalObjectCharacterRecognitionModelWithState, ObjectCharacterRecognitionDetectorWithFiles, ObjectCharacterRecognitionRecognizerWithFiles

### Community 69 - "TarballExtractor"
Cohesion: 0.26
Nodes (5): CompressedFileExtractor, ExtractionProgressListener, Uri, Uri, TarballExtractor

### Community 70 - "VoiceWithModelFiles"
Cohesion: 0.16
Nodes (6): VoiceDatabaseRepository, Flow, VoiceRepository, VoiceWithModelFiles, VoiceDatabaseModel, VoiceModelDatabaseModel

### Community 71 - "SpeechRecognitionInferenceFiles"
Cohesion: 0.13
Nodes (10): CoroutineScope, FloatArray, Flow, SpeechRecognitionInference, CoroutineScope, FloatArray, Flow, SpeechRecognitionMockInference (+2 more)

### Community 72 - "JNIEnv"
Cohesion: 0.39
Nodes (15): jintArray, jlong, JNIEnv, JNIEXPORT, jobject, findRecognizer(), Java_app_versta_translate_bridge_whisper_Whisper_destroy(), Java_app_versta_translate_bridge_whisper_Whisper_feed() (+7 more)

### Community 73 - "VoiceMemoryRepository.kt"
Cohesion: 0.21
Nodes (4): Flow, VoiceModelTokenizerFiles, VoiceModelVoiceFiles, ArrayList

### Community 74 - "TextTranslationLegacy.kt"
Cohesion: 0.13
Nodes (22): AnnotatedString, WritingDirection, LTR, RTL, Idle, Preparing, Synthesizing, TextToSpeechSynthesisState (+14 more)

### Community 75 - "MicrophoneCapture"
Cohesion: 0.24
Nodes (9): CoroutineScope, FloatArray, Job, StateFlow, MicrophoneCapture, fftMagnitudes(), FloatArray, spectrumBands() (+1 more)

### Community 76 - "whisper.cc"
Cohesion: 0.22
Nodes (12): advance_front(), vector, Java_app_versta_translate_bridge_whisper_Whisper_process(), probe_and_update(), probe_speech(), ProbeUpdate, pause_mid_ms, valid (+4 more)

### Community 77 - "CustomThemeViewModel"
Cohesion: 0.24
Nodes (10): CustomThemeViewModel, ViewModel, CustomTheme, Obsidian, CustomThemeScene, NavKey, ObsidianThemeMetadata, rememberCustomThemeEntryDecorator() (+2 more)

### Community 78 - "Whisper"
Cohesion: 0.10
Nodes (8): AutoCloseable, FloatArray, IntArray, WhisperSegmentCallback, Whisper, WhisperSegmentCallback, SpeechRecognitionInitialPrompts, RuntimeException

### Community 79 - "tasteskill: Anti-Slop Frontend Skill"
Cohesion: 0.13
Nodes (15): 0.A Read these signals first, 0.B Output a one-line "Design Read" before generating, 0. BRIEF INFERENCE (Read the Room Before Anything Else), 0.C If the brief is ambiguous, ask one question, do not guess, 0.D Anti-Default Discipline, 13. OUT OF SCOPE, 14. FINAL PRE-FLIGHT CHECK, 1.A Dial Inference (design read → dial values) (+7 more)

### Community 80 - "Appendix B - Canonical Sources (read these before reinventing)"
Cohesion: 0.13
Nodes (15): Appendix B - Canonical Sources (read these before reinventing), Apple Liquid Glass (Apple platforms only), Atlassian, Bootstrap, Carbon, Fluent UI, GOV.UK, Material Web (+7 more)

### Community 81 - "WhisperSpeechRecognition"
Cohesion: 0.14
Nodes (11): MicrophoneCaptureException, AutoCloseable, CoroutineScope, FloatArray, Flow, IntArray, Job, WhisperSegmentCallback (+3 more)

### Community 82 - "MainActivity.kt"
Cohesion: 0.21
Nodes (9): Bundle, ComponentActivity, Intent, MainActivity, ModelLoadingProgressDialog(), Easing, Spacing, TranslateTheme() (+1 more)

### Community 83 - "Clean Code - Pragmatic AI Coding Standards"
Cohesion: 0.14
Nodes (13): Agent → Script Mapping, AI Coding Style, Anti-Patterns (DON'T), 🔴 Before Editing ANY File (THINK FIRST!), Clean Code - Pragmatic AI Coding Standards, Code Structure, Core Principles, Function Rules (+5 more)

### Community 84 - "LogFileSaver"
Cohesion: 0.19
Nodes (8): FileSaver, FileSaverCallback, FileSaverCallback, Uri, ActivityResultLauncher, ComponentActivity, FileSaverCallback, LogFileSaver

### Community 85 - "ObjectCharacterRecognitionRepository"
Cohesion: 0.13
Nodes (5): DownloadObjectCharacterRecognitionWorker, Flow, ObjectCharacterRecognitionRepository, ObjectCharacterRecognitionDetectorModel, ObjectCharacterRecognitionRecognizerModel

### Community 86 - "LanguagePairBadge"
Cohesion: 0.25
Nodes (11): Color, ImageVector, Modifier, LanguagePairBadge(), LanguagePairBadgeColors, LanguagePairBadgeDefaults, LanguagePairBadgePreview(), Modifier (+3 more)

### Community 87 - "Screens"
Cohesion: 0.08
Nodes (25): About, ApplicationLogs, Conversation, NavKey, LanguageAttributions, LanguageDetails, LanguageSettings, ObjectCharacterRecognitionAttributions (+17 more)

### Community 88 - "PaddleOCR"
Cohesion: 0.22
Nodes (7): AutoCloseable, Buffer, ByteBuffer, IntArray, LongArray, PaddleOCR, TextRegionMetrics

### Community 89 - "BubbleActivity.kt"
Cohesion: 0.33
Nodes (5): BubbleActivity, Activity, Bundle, ComponentActivity, Intent

### Community 90 - "Timber"
Cohesion: 0.11
Nodes (10): DownloadExternalDataWorker, OcrPostProcessorPipeline, DataBundleMetadata, Context, MainApplication, FileLoggingTree, LocaleUtils, Application (+2 more)

### Community 91 - "SpeechRecognitionEngine"
Cohesion: 0.12
Nodes (7): CoroutineScope, AutoCloseable, FloatArray, IntArray, SpeechRecognitionEngine, SpeechRecognitionMetrics, TestCoroutineScheduler

### Community 92 - "LoggingViewModel"
Cohesion: 0.25
Nodes (6): Context, StateFlow, Uri, ViewModel, LoggingViewModel, FileObserver

### Community 93 - "App Layer Conventions"
Cohesion: 0.15
Nodes (12): App Layer Conventions, Don'ts, Graphify, Inference, Kotlin style, Logging, Naming conventions, Package layout (+4 more)

### Community 95 - "ExternalObjectCharacterRecognitionModelDefinition"
Cohesion: 0.13
Nodes (8): Flow, ExternalObjectCharacterRecognitionModelsRepository, Flow, ExternalObjectCharacterRecognitionDownloadTask, ExternalObjectCharacterRecognitionModelDefinition, Flow, StateFlow, ViewModel

### Community 96 - "Spacing.kt"
Cohesion: 0.12
Nodes (26): ExternalVoiceModelsMemoryRepository, VoiceMemoryRepository, ExternalVoiceLanguageVoiceGenders, VoiceViewModel, DownloadButton(), Modifier, LanguageDownloadButtonPreview(), Dp (+18 more)

### Community 97 - "VoiceWaveform.kt"
Cohesion: 0.40
Nodes (9): appendRidge(), catmullRom(), envelope(), FloatArray, Modifier, VoiceStratum, VoiceWaveform(), VoiceWaveformDarkPreview() (+1 more)

### Community 98 - "4. DESIGN ENGINEERING DIRECTIVES (Bias Correction)"
Cohesion: 0.17
Nodes (12): 4.10 Quotes & Testimonials, 4.11 Page Theme Lock (Light / Dark Mode Consistency), 4.1 Typography, 4.2 Color Calibration, 4.3 Layout Diversification, 4.4 Materiality, Shadows, Cards, 4.5 Interactive UI States, 4.6 Data & Form Patterns (+4 more)

### Community 99 - "ScaffoldModalBottomSheet"
Cohesion: 0.47
Nodes (8): Color, Composable, Dp, Modifier, Shape, ScaffoldModalBottomSheet(), Scrim(), BottomSheetScaffoldState

### Community 100 - "WhisperModelHandle"
Cohesion: 0.31
Nodes (7): findModel(), WhisperModelHandle, ctx, n_threads, vctx, whisper_context, whisper_vad_context

### Community 101 - "Writing Plans"
Cohesion: 0.17
Nodes (11): Bite-Sized Task Granularity, Execution Handoff, File Structure, No Placeholders, Overview, Plan Document Header, Scope Check, Self-Review (+3 more)

### Community 102 - "ModelFilePicker"
Cohesion: 0.26
Nodes (6): FilePicker, FilePickerCallback, Uri, ActivityResultLauncher, ComponentActivity, ModelFilePicker

### Community 103 - "screen/CameraTranslation.kt"
Cohesion: 0.16
Nodes (14): DeleteBackground(), Modifier, SwipeDelete(), CameraPermissionDenied(), CameraTranslation(), CameraViewFinder(), LifecycleOwner, Modifier (+6 more)

### Community 104 - "GDPR Data Handling"
Cohesion: 0.18
Nodes (10): 1. Personal Data Categories, 2. Legal Bases for Processing, 3. Data Subject Rights, Best Practices, Core Concepts, Detailed worked examples and patterns, Do's, Don'ts (+2 more)

### Community 106 - "TranslateBubbleShortcut"
Cohesion: 0.39
Nodes (5): ComponentActivity, Context, TranslateBubbleShortcut, ShortcutInfo, ShortcutManager

### Community 108 - "TranslateBubbleNotification"
Cohesion: 0.29
Nodes (4): Context, TranslateBubbleNotification, Context, TranslateNotification

### Community 109 - "ExternalVoiceModelsFileRepository"
Cohesion: 0.43
Nodes (3): ExternalVoiceModelsFileRepository, ExternalVoiceModelDefinitions, Flow

### Community 110 - "TextToSpeechViewModel.kt"
Cohesion: 0.11
Nodes (9): AudioPlayer, ByteArray, FloatArray, AudioTrackPlayer, ByteArray, FloatArray, Flow, StateFlow (+1 more)

### Community 111 - "Versta.Android"
Cohesion: 0.20
Nodes (9): Architecture, Build & verification, Dependency injection, Documentation, Git conventions, Project identity, Repo map, Testing (+1 more)

### Community 112 - "C++ Coding Standards"
Cohesion: 0.20
Nodes (9): C++ Coding Standards, Class/Type Naming, File Naming, Function/Method Naming, Header Guards / Pragma, Namespace Naming, Organization, Smart Pointers (+1 more)

### Community 113 - "Serializable"
Cohesion: 0.26
Nodes (5): directorySize(), SimpleFileVisitor, BasicFileAttributes, FileVisitResult, Serializable

### Community 114 - "10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know)"
Cohesion: 0.20
Nodes (10): 10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know), Animation Library Choice, Cards & Containers, Galleries & Media, Hero Paradigms, Layout & Grids, Micro-Interactions & Effects, Navigation & Menus (+2 more)

### Community 115 - "Verification Before Completion"
Cohesion: 0.20
Nodes (9): Common Failures, Key Patterns, Overview, Rationalization Prevention, Red Flags - STOP, The Gate Function, The Iron Law, Verification Before Completion (+1 more)

### Community 116 - "MockSpeechRecognitionEngine"
Cohesion: 0.25
Nodes (3): IntArray, WhisperSegmentCallback, MockSpeechRecognitionEngine

### Community 119 - "LanguageDetails.kt"
Cohesion: 0.36
Nodes (9): LanguageDeletionConfirmationDialog(), LanguageDeletionConfirmationDialogPreview(), Details(), ImageVector, PaddingValues, LanguageDetails(), LanguageDetailsData(), LanguageDetailsPreview() (+1 more)

### Community 120 - "DataModel"
Cohesion: 0.23
Nodes (4): DataDatabaseRepository, DataModel, executeAsListFlow(), DataDatabaseModel

### Community 122 - "Implementation Patterns"
Cohesion: 0.22
Nodes (8): Compliance Checklist, gdpr-data-handling — detailed worked examples, Implementation Patterns, Pattern 1: Consent Management, Pattern 2: Data Subject Access Request (DSAR), Pattern 3: Data Retention, Pattern 4: Privacy by Design, Pattern 5: Breach Notification

### Community 123 - "PaddleObjectCharacterRecognitionTokenizer"
Cohesion: 0.25
Nodes (3): LongArray, PaddleObjectCharacterRecognitionTokenizer, Vocabulary

### Community 124 - "TranslationSettings.kt"
Cohesion: 0.25
Nodes (11): Dp, ListItemColors, Modifier, SettingsHeaderItem(), SettingsHeaderItemPreview(), Modifier, T, SliderPredefinedValues() (+3 more)

### Community 125 - "9. AI TELLS (Forbidden Patterns)"
Cohesion: 0.25
Nodes (8): 9.A Visual & CSS, 9. AI TELLS (Forbidden Patterns), 9.B Typography, 9.C Layout & Spacing, 9.D Content & Data ("Jane Doe" Effect), 9.E External Resources & Components, 9.F Production-Test Tells (banned outright), 9.G EM-DASH BAN (the single most-violated Tell)

### Community 126 - "Core Concepts"
Cohesion: 0.25
Nodes (7): 1. Material Design 3 Principles, 2. Jetpack Compose Layout System, 3. Navigation Patterns, 4. Material 3 Theming, 5. Component Examples, Core Concepts, mobile-android-design — detailed sections

### Community 127 - "Mobile Touch Animation"
Cohesion: 0.25
Nodes (7): Android, Haptic Guidelines, iOS, Mobile Touch Animation, Platform Patterns, Principle Applications, Quick Reference

### Community 128 - "ExternalVoiceModelsRepository"
Cohesion: 0.23
Nodes (6): ExternalVoiceModelsRepository, ExternalVoiceModelDefinitions, Flow, Flow, StateFlow, ViewModel

### Community 129 - "TextToSpeechInference"
Cohesion: 0.25
Nodes (3): FloatArray, LongArray, TextToSpeechInference

### Community 130 - "Router.kt"
Cohesion: 0.19
Nodes (16): app, ExternalObjectCharacterRecognitionModelsMemoryRepository, ObjectCharacterRecognitionViewModel, PaddingValues, ObjectCharacterRecognitionAttributions(), ObjectCharacterRecognitionAttributionsPreview(), Details(), PaddingValues (+8 more)

### Community 131 - "FontWeight"
Cohesion: 0.22
Nodes (6): ImageProxy, ObjectCharacterRecognitionAnalyzer, FontWeight, BOLD, REGULAR, ImageAnalysis

### Community 132 - "Native Header & Binding"
Cohesion: 0.25
Nodes (7): C++ style & build, JNI binding shape, Layout, Native Bridge Conventions, Opaque handle pattern, Ownership & threading, Vendored libraries

### Community 133 - "APPENDICES - Real Source-Backed Reference Material"
Cohesion: 0.29
Nodes (6): APPENDICES - Real Source-Backed Reference Material, Appendix A - Install Commands per Design System, Appendix C - Apple Liquid Glass: Honest Web Approximation, Safer web approximation skeleton, What is NOT official, What is official

### Community 134 - "11. REDESIGN PROTOCOL"
Cohesion: 0.29
Nodes (7): 11.A Detect the Mode (first action), 11.B Audit Before Touching, 11.C Preservation Rules, 11.D Modernisation Levers (priority order), 11.E Decision Tree: Targeted Evolution vs Full Redesign, 11.F What Never Changes Silently, 11. REDESIGN PROTOCOL

### Community 135 - "3. DEFAULT ARCHITECTURE & CONVENTIONS"
Cohesion: 0.29
Nodes (7): 3.A Stack, 3.B State, 3.C Icons, 3.D Emoji Policy, 3. DEFAULT ARCHITECTURE & CONVENTIONS, 3.E Responsiveness & Layout Mechanics, 3.F Dependency Verification (mandatory)

### Community 136 - "6. PERFORMANCE & ACCESSIBILITY GUARDRAILS"
Cohesion: 0.29
Nodes (7): 6.A Hardware Acceleration, 6.B Reduced Motion (mandatory), 6.C Dark Mode (mandatory for any consumer-facing page), 6.D Core Web Vitals Targets, 6.E DOM Cost, 6.F Z-Index Restraint, 6. PERFORMANCE & ACCESSIBILITY GUARDRAILS

### Community 137 - "Android Mobile Design"
Cohesion: 0.29
Nodes (6): Android Mobile Design, Best Practices, Common Issues, Detailed section: Core Concepts, Quick Start Component, When to Use This Skill

### Community 138 - "Java_app_versta_translate_bridge_whisper_Whisper_create"
Cohesion: 0.43
Nodes (7): jboolean, jfloat, jint, jstring, Java_app_versta_translate_bridge_whisper_Whisper_create(), Java_app_versta_translate_bridge_whisper_WhisperModel_create(), jstr()

### Community 140 - "LanguageDetect"
Cohesion: 0.33
Nodes (3): AutoCloseable, LanguageDetect, LanguageDetectResult

### Community 141 - "SpeechContextStore"
Cohesion: 0.43
Nodes (3): Entry, IntArray, SpeechContextStore

### Community 143 - "AudioCapture"
Cohesion: 0.32
Nodes (4): AudioCapture, CoroutineScope, FloatArray, StateFlow

### Community 147 - "Java_app_versta_translate_bridge_tokenize_Vocabulary_load"
Cohesion: 0.33
Nodes (5): JNIEnv, JNIEXPORT, jobject, jstring, Java_app_versta_translate_bridge_tokenize_Vocabulary_load()

### Community 148 - "TrailingProbe"
Cohesion: 0.33
Nodes (6): TrailingProbe, first_speech_start_ms, last_pause_mid_ms, last_speech_end_ms, last_voiced_end_ms, valid

### Community 150 - "12. THE BLOCK LIBRARY (Contract - Implementations Land Here Iteratively)"
Cohesion: 0.40
Nodes (5): 12.A File Location, 12.B Required Frontmatter, 12.C Required Body Sections, 12.D Block-Library Discipline, 12. THE BLOCK LIBRARY (Contract - Implementations Land Here Iteratively)

### Community 151 - "5. CONTEXT-AWARE PROACTIVITY"
Cohesion: 0.40
Nodes (5): 5.A Sticky-Stack - Canonical Skeleton, 5.B Horizontal-Pan - Canonical Skeleton, 5.C Scroll-Reveal Stagger - Canonical Skeleton (lighter alternative), 5. CONTEXT-AWARE PROACTIVITY, 5.D Forbidden Animation Patterns

### Community 152 - "8. DARK MODE PROTOCOL"
Cohesion: 0.40
Nodes (5): 8.A Token Strategy (pick one, stick to it), 8.B Do Not Prescribe Specific Colors Here, 8.C Default Mode, 8.D Test in Both Modes Before Finishing, 8. DARK MODE PROTOCOL

### Community 153 - "Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer"
Cohesion: 0.40
Nodes (4): JNIEnv, JNIEXPORT, jobject, Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer()

### Community 154 - "TranslateNotificationActivity.kt"
Cohesion: 0.60
Nodes (3): Activity, Bundle, TranslateNotificationActivity

### Community 156 - "LanguageViewModel"
Cohesion: 0.10
Nodes (25): AutoDetectLanguage, LanguageOption, Flow, Job, StateFlow, ViewModel, LanguageType, Source (+17 more)

### Community 157 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 158 - "7. DIAL DEFINITIONS (Technical Reference)"
Cohesion: 0.50
Nodes (4): 7. DIAL DEFINITIONS (Technical Reference), DESIGN_VARIANCE (Level 1-10), MOTION_INTENSITY (Level 1-10), VISUAL_DENSITY (Level 1-10)

### Community 159 - "ViewModelProvider"
Cohesion: 0.60
Nodes (3): T, viewModelFactory(), ViewModelProvider

## Knowledge Gaps
- **527 isolated node(s):** `language`, `isReliable`, `confidence`, `hints`, `languages` (+522 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `string` connect `NodeFeature` to `PaddleOCR`, `Java_app_versta_translate_bridge_whisper_Whisper_create`, `whisper.cc`, `Recognizer`?**
  _High betweenness centrality (0.086) - this node is a cross-community bridge._
- **Why does `ApplicationModule` connect `MainApplication.kt` to `ExternalVoiceModelsRepository`, `TextToSpeechInference`, `Router.kt`, `StyleTextToSpeechInference`, `VoiceGender`, `PaddleObjectCharacterRecognitionInference.kt`, `CameraTranslationViewModel`, `LanguagePreferenceRepository`, `LanguagePair`, `ScaffoldViewModel`, `ExternalLanguageModelsMemoryRepository`, `BergamotTinyInference`, `ExternalSpeechRecognitionModelDefinition`, `TranslationPreferenceRepository`, `ObjectCharacterRecogniserResult`, `LanguageViewModel`, `SpeechRecognitionWithFiles`, `Language`, `SpeechRecognitionViewModel`, `StyleTextToSpeech2Tokenizer`, `LanguageRepository`, `TextTranslationViewModel`, `NavigationViewModel`, `DatabaseContainer`, `ObjectCharacterRecognitionRepositoryDatabaseRepository`, `ESpeakNG`, `TranslationPreferenceDataStoreRepository`, `TextToSpeechViewModel`, `TranslationViewModel`, `ObjectCharacterRecognitionRecognizerWithFiles`, `TarballExtractor`, `VoiceWithModelFiles`, `SpeechRecognitionInferenceFiles`, `CustomThemeViewModel`, `WhisperSpeechRecognition`, `ObjectCharacterRecognitionRepository`, `Timber`, `LoggingViewModel`, `ExternalObjectCharacterRecognitionModelDefinition`, `Spacing.kt`, `ExternalVoiceModelsFileRepository`, `TextToSpeechViewModel.kt`, `DataModel`, `SpeechRecognitionDatabaseRepository`, `PaddleObjectCharacterRecognitionTokenizer`?**
  _High betweenness centrality (0.072) - this node is a cross-community bridge._
- **Why does `WhisperSpeechRecognition` connect `WhisperSpeechRecognition` to `SpeechRecognitionViewModel`, `SpeechRecognitionInferenceFiles`, `WhisperSpeechRecognitionTest`, `SpeechContextStore`, `AudioCapture`, `MainApplication.kt`, `SpeechRecognitionEngine`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **What connects `language`, `isReliable`, `confidence` to the rest of the system?**
  _527 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `PaddleOCR` be split into smaller, more focused modules?**
  _Cohesion score 0.06107594936708861 - nodes in this community are weakly interconnected._
- **Should `MeshGradientBackground.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.0780399274047187 - nodes in this community are weakly interconnected._
- **Should `TextTranslation.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.09494949494949495 - nodes in this community are weakly interconnected._