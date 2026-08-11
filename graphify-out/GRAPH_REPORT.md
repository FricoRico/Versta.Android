# Graph Report - Versta.Android  (2026-08-11)

## Corpus Check
- 283 files · ~152,779 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2990 nodes · 6670 edges · 174 communities (148 shown, 26 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 84 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `2d7d6c9a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- PaddleOCR
- MeshGradientBackground.kt
- LanguageMemoryRepository
- StyleTextToSpeechInference
- Recognizer
- NodeFeature
- SpeechRecognitionViewModel
- OcrTextAnalyzer
- DESIGN.md
- VoiceGender
- WhisperSpeechRecognitionTest
- PaddleObjectCharacterRecognitionInference.kt
- Code Review and Quality
- NavigationViewModel
- CameraTranslationViewModel
- Test-Driven Development
- LanguageOption
- ObjectCharacterRecognitionMetadata.kt
- LanguageDatabaseRepository
- LanguagePair
- ExternalLanguagePairDefinition
- LanguageModelConfiguration
- MainApplication.kt
- ExternalSpeechRecognitionModelDefinition
- ScaffoldCompactBar.kt
- SpeechContextStoreTest
- ObjectCharacterRecogniserResult
- .definition
- ObjectCharacterRecognitionDetectorWithFiles
- ExternalObjectCharacterRecognitionModelDefinition
- Jetpack Compose Component Library
- Language
- PaddleObjectCharacterRecognitionRecognizeOutput
- Code Simplification
- SpeechRecognitionMockInference
- DownloadWorker
- DataType
- Android Navigation Patterns
- SpeechRecognitionWithFiles
- SpeechRecognitionDatabaseRepository
- BergamotTinyInference
- DatabaseContainer
- Source First License 1.1
- Android Kotlin Development
- OcrTextAnalyzer.kt
- ExternalVoiceModelDefinition
- VoiceSettings.kt
- HttpDownloadClient
- LicenseViewModel
- PaddleObjectCharacterRecognitionDetectOutput
- LanguageViewModel
- Spacing.kt
- Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage
- Material Design 3 Theming
- Leanmt
- JapaneseTransliterator
- SpeechRecognitionMetadata.kt
- VoiceDatabaseRepository.kt
- ObjectCharacterRecognitionRecognizerWithFiles
- TextToSpeechViewModel.kt
- Theme.kt
- TranslationPreferenceDataStoreRepository
- espeak_ng.cc
- TranslationPreferenceMemoryRepository
- TranslationPreferenceRepository
- TextToSpeechViewModel
- DownloadManager
- TranslationViewModel.kt
- Timber
- TarballExtractor
- VoiceDatabaseRepository
- ExternalObjectCharacterRecognitionDownloadTask
- JNIEnv
- ExternalVoiceModelsRepository
- TextTranslationLegacy
- AudioCapture
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
- VoiceWithModelFiles
- Screens
- PaddleOCR
- BubbleActivity.kt
- DownloadTask
- SpeechRecognitionEngine
- LoggingViewModel
- App Layer Conventions
- SpeechRecognitionWithFilesTest
- DataMetadata.kt
- LanguagePreferenceRepository
- SpeechRecognitionViewModel.kt
- 4. DESIGN ENGINEERING DIRECTIVES (Bias Correction)
- ScaffoldModalBottomSheet
- WhisperModelHandle
- Writing Plans
- ModelFilePicker
- LanguageViewModel.kt
- GDPR Data Handling
- ExternalSpeechRecognitionModelsFileRepositoryTest
- TranslateBubbleShortcut
- SpeechRecognitionMetadataTest
- TranslateBubbleNotification
- DataWithFiles
- ExternalDataDefinition
- Versta.Android
- C++ Coding Standards
- VoiceModelVoiceFiles
- 10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know)
- Verification Before Completion
- FakeRecognizer
- SpeechRecognitionInitialPromptsTest
- ExternalDataFileRepository.kt
- StyleTextToSpeech2Tokenizer
- DownloadStatus
- LanguageModelMetadata.kt
- Implementation Patterns
- PaddleObjectCharacterRecognitionTokenizer
- LanguagePairBadge
- 9. AI TELLS (Forbidden Patterns)
- Core Concepts
- Mobile Touch Animation
- ExternalDataRepository
- ExternalVoiceModelsMemoryRepository
- LanguageBadge.kt
- LanguageSelectionDrawer.kt
- Native Header & Binding
- APPENDICES - Real Source-Backed Reference Material
- 11. REDESIGN PROTOCOL
- 3. DEFAULT ARCHITECTURE & CONVENTIONS
- 6. PERFORMANCE & ACCESSIBILITY GUARDRAILS
- Android Mobile Design
- Java_app_versta_translate_bridge_whisper_Whisper_create
- AudioPlayer
- AudioTrackPlayer
- LanguageDetect
- WhisperModel
- ExternalLanguageModel.kt
- LanguageSelector.kt
- MinimalLanguageSelector.kt
- Java_app_versta_translate_bridge_tokenize_Vocabulary_load
- TrailingProbe
- ExternalLanguageModelTest
- 12. THE BLOCK LIBRARY (Contract - Implementations Land Here Iteratively)
- 5. CONTEXT-AWARE PROACTIVITY
- 8. DARK MODE PROTOCOL
- Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer
- TranslateNotificationActivity.kt
- MosesPunctuationNormalizer
- ViewModelProvider
- gradlew
- 7. DIAL DEFINITIONS (Technical Reference)
- WritingDirection
- SliderPredefinedValues
- FileSaverCallback
- FakeModel
- DownloadLanguageWorker
- DownloadSpeechRecognitionWorker
- DownloadVoiceWorker
- .play
- .synthesize
- ContentColor.kt
- SliderLogarithmic.kt
- plan-document-reviewer-prompt.md

## God Nodes (most connected - your core abstractions)
1. `NavigationViewModel` - 96 edges
2. `Language` - 89 edges
3. `ScaffoldViewModel` - 77 edges
4. `LanguageViewModel` - 74 edges
5. `ApplicationModule` - 73 edges
6. `Recognizer` - 62 edges
7. `LanguagePair` - 60 edges
8. `TextToSpeechViewModel` - 57 edges
9. `PaddleOCR` - 51 edges
10. `ScaffoldComponentProvider()` - 50 edges

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

## Communities (174 total, 26 thin omitted)

### Community 0 - "PaddleOCR"
Cohesion: 0.06
Nodes (47): argmax(), jboolean, jfloat, jint, jintArray, jlong, JNIEnv, JNIEXPORT (+39 more)

### Community 1 - "MeshGradientBackground.kt"
Cohesion: 0.06
Nodes (46): Color, MeshGradientBackgroundTest, ButtonCard(), ButtonCardColors, ButtonCardDefaults, ButtonCardPreview(), Color, ImageVector (+38 more)

### Community 2 - "LanguageMemoryRepository"
Cohesion: 0.06
Nodes (42): AudioMockPlayer, DataMemoryRepository, ExternalDataMemoryRepository, ExternalLanguageModelsMemoryRepository, LanguageMemoryRepository, LanguagePreferenceMemoryRepository, TextToSpeechMockInference, TextToSpeechMockTokenizer (+34 more)

### Community 3 - "StyleTextToSpeechInference"
Cohesion: 0.05
Nodes (25): ByteBuffer, FloatArray, LongArray, OrtSession, Waveform, StyleTextToSpeechInference, FloatArray, LongArray (+17 more)

### Community 4 - "Recognizer"
Cohesion: 0.04
Nodes (50): Recognizer, abort_count, abort_deadline, callback_method, callback_obj, carried_prompt_ids, commit_compute_ms, commit_rate_ema_ms_per_sec (+42 more)

### Community 5 - "NodeFeature"
Cohesion: 0.06
Nodes (54): jlong, JNIEnv, JNIEXPORT, jobject, jstring, findModel(), findService(), Java_app_versta_translate_bridge_leanmt_Leanmt_create() (+46 more)

### Community 6 - "SpeechRecognitionViewModel"
Cohesion: 0.09
Nodes (30): ExternalSpeechRecognitionModels, LoadRequest, SpeechRecognitionViewModel, Completed, Error, Idle, InProgress, LoadingProgress (+22 more)

### Community 7 - "OcrTextAnalyzer"
Cohesion: 0.10
Nodes (28): jfloat, jint, jlong, JNIEnv, JNIEXPORT, jobject, Mat, Scalar (+20 more)

### Community 8 - "DESIGN.md"
Cohesion: 0.05
Nodes (37): Border Radius Scale, Brand & Accent, Breakpoints, Buttons, Cards & Containers, Collapsing Strategy, Colors, Components (+29 more)

### Community 9 - "VoiceGender"
Cohesion: 0.08
Nodes (9): Flow, TextToSpeechPreferenceDataStoreRepository, Flow, Flow, TextToSpeechPreferenceRepository, VoiceGender, Female, Male (+1 more)

### Community 11 - "PaddleObjectCharacterRecognitionInference.kt"
Cohesion: 0.13
Nodes (15): ImageProxy, ObjectCharacterRecognitionInference, AutoCloseable, ByteBuffer, ImageProxy, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognition (+7 more)

### Community 12 - "Code Review and Quality"
Cohesion: 0.07
Nodes (29): 1. Correctness, 2. Readability & Simplicity, 3. Architecture, 4. Security, 5. Performance, Change Descriptions, Change Sizing, Code Review and Quality (+21 more)

### Community 13 - "NavigationViewModel"
Cohesion: 0.10
Nodes (52): app, NavKey, ViewModel, NavigationViewModel, ObjectCharacterRecognitionViewModel, ScaffoldViewModel, Router(), ScaffoldCompactBarBackNavigationIcon() (+44 more)

### Community 14 - "CameraTranslationViewModel"
Cohesion: 0.08
Nodes (24): ObjectCharacterRecognitionAnalyzer, CameraTranslationResult, FontWeight, BOLD, REGULAR, CameraTranslationViewModel, Context, Flow (+16 more)

### Community 15 - "Test-Driven Development"
Cohesion: 0.07
Nodes (29): Browser Testing with DevTools, Common Rationalizations, DAMP Over DRY in Tests, Decision Guide, Discover the Stack First, Name Tests Descriptively, One Assertion Per Concept, Overview (+21 more)

### Community 16 - "LanguageOption"
Cohesion: 0.13
Nodes (6): Flow, LanguagePreferenceDataStoreRepository, Flow, AutoDetectLanguage, LanguageOption, LanguageOptionPair

### Community 17 - "ObjectCharacterRecognitionMetadata.kt"
Cohesion: 0.10
Nodes (13): ObjectCharacterRecognitionBundleMetadata, ObjectCharacterRecognitionDetectorFilesMetadata, ObjectCharacterRecognitionDetectorInferenceFilesMetadata, ObjectCharacterRecognitionDetectorMetadata, ObjectCharacterRecognitionMetadataFile, ObjectCharacterRecognitionModule, Detector, Recognizer (+5 more)

### Community 18 - "LanguageDatabaseRepository"
Cohesion: 0.11
Nodes (8): Flow, LanguageDatabaseRepository, PivotPair, LanguageBundleData, LanguageBundleMetadata, LanguageModelMetadata, LanguageDatabaseModel, LanguageModelDatabaseModel

### Community 19 - "LanguagePair"
Cohesion: 0.11
Nodes (4): Flow, LanguageRepository, LanguagePair, Flow

### Community 20 - "ExternalLanguagePairDefinition"
Cohesion: 0.18
Nodes (11): ExternalLanguageModelsFileRepository, Flow, Flow, ExternalLanguageModelsRepository, Flow, Flow, ExternalLanguageMetadata, ExternalLanguageModels (+3 more)

### Community 21 - "LanguageModelConfiguration"
Cohesion: 0.13
Nodes (7): TranslationInference, LeanmtModelConfig, LeanmtPackage, AutoCloseable, TranslationEngine, LanguageModelConfiguration, LanguageModelFiles

### Community 22 - "MainApplication.kt"
Cohesion: 0.11
Nodes (17): FileHashValidator, PrecomputedHashFileValidator, Flow, LicenseDataStoreRepository, Flow, LicenseRepository, SpeechRecognitionRepository, TextToSpeechTokenizer (+9 more)

### Community 23 - "ExternalSpeechRecognitionModelDefinition"
Cohesion: 0.15
Nodes (8): ExternalSpeechRecognitionModelsFileRepository, Flow, ExternalSpeechRecognitionModelsMemoryRepository, Flow, ExternalSpeechRecognitionModelsRepository, Flow, ExternalSpeechRecognitionModelDefinition, ExternalSpeechRecognitionModelWithState

### Community 24 - "ScaffoldCompactBar.kt"
Cohesion: 0.14
Nodes (20): NavKey, ScaffoldComponent, ScaffoldRowScopeComponent, ViewModel, ScaffoldActionsComponent, ScaffoldBottomBarComponent, ScaffoldComponentMetadata, ScaffoldComponents (+12 more)

### Community 25 - "SpeechContextStoreTest"
Cohesion: 0.17
Nodes (4): SpeechContextStoreTest, Entry, IntArray, SpeechContextStore

### Community 26 - "ObjectCharacterRecogniserResult"
Cohesion: 0.15
Nodes (7): ImageProxy, OcrPostProcessor, OcrPostProcessorContext, OcrPostProcessorPipeline, ParagraphGroupingPostProcessor, TextStyleAnalysisPostProcessor, ObjectCharacterRecogniserResult

### Community 28 - "ObjectCharacterRecognitionDetectorWithFiles"
Cohesion: 0.12
Nodes (9): Flow, ObjectCharacterRecognitionRepositoryMemoryRepository, ObjectCharacterRecognitionArchitecture, PaddleOCR, ObjectCharacterRecognitionDetectorInferenceFiles, ObjectCharacterRecognitionDetectorWithFiles, ObjectCharacterRecognitionRecognizerInferenceFiles, ObjectCharacterRecognitionRecognizerTokenizerFiles (+1 more)

### Community 29 - "ExternalObjectCharacterRecognitionModelDefinition"
Cohesion: 0.19
Nodes (9): ExternalObjectCharacterRecognitionModelsFileRepository, Flow, ExternalObjectCharacterRecognitionModelsMemoryRepository, Flow, ExternalObjectCharacterRecognitionModelsRepository, Flow, ExternalObjectCharacterRecognitionModelDefinition, ExternalObjectCharacterRecognitionModels (+1 more)

### Community 30 - "Jetpack Compose Component Library"
Cohesion: 0.09
Nodes (22): Alert Dialog, Animated Content, Animated Visibility, Animations, Basic LazyColumn, Content Loading Pattern, Date and Time Pickers, Dialogs and Bottom Sheets (+14 more)

### Community 31 - "Language"
Cohesion: 0.10
Nodes (5): LongArray, LongArray, Context, Language, FloatArray

### Community 32 - "PaddleObjectCharacterRecognitionRecognizeOutput"
Cohesion: 0.15
Nodes (8): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognitionRecognizeInput, PaddleObjectCharacterRecognitionRecognizeOutput

### Community 33 - "Code Simplification"
Cohesion: 0.09
Nodes (21): 1. Preserve Behavior Exactly, 2. Follow Project Conventions, 3. Prefer Clarity Over Cleverness, 4. Maintain Balance, 5. Scope to What Changed, Code Simplification, Common Rationalizations, Language-Specific Guidance (+13 more)

### Community 34 - "SpeechRecognitionMockInference"
Cohesion: 0.12
Nodes (8): CoroutineScope, Flow, SpeechRecognitionInference, CoroutineScope, Flow, SpeechRecognitionMockInference, IntArray, SpeechRecognitionSegment

### Community 35 - "DownloadWorker"
Cohesion: 0.20
Nodes (8): DownloadQueue, DownloadWorker, DownloadListener, DownloadListener, Intent, CoroutineWorker, ForegroundInfo, Result

### Community 36 - "DataType"
Cohesion: 0.19
Nodes (7): DataDatabaseRepository, DataRepository, Flow, DataModel, DataType, TTS, DataDatabaseModel

### Community 37 - "Android Navigation Patterns"
Cohesion: 0.10
Nodes (20): Android Navigation Patterns, Back Handler, Basic Deep Link Setup, Basic Navigation, Bottom Nav with Badges, Bottom Navigation, Deep Linking, Handling Intent in Activity (+12 more)

### Community 38 - "SpeechRecognitionWithFiles"
Cohesion: 0.15
Nodes (7): Flow, SpeechRecognitionMemoryRepository, Flow, SpeechRecognitionArchitecture, Whisper, SpeechRecognitionInferenceFiles, SpeechRecognitionWithFiles

### Community 40 - "BergamotTinyInference"
Cohesion: 0.29
Nodes (3): BergamotTinyInferenceTest, FakeLeanmt, BergamotTinyInference

### Community 41 - "DatabaseContainer"
Cohesion: 0.19
Nodes (6): DatabaseContainer, Migration, Migration3, Context, Migration4, Migration6

### Community 42 - "Source First License 1.1"
Cohesion: 0.10
Nodes (18): Acceptance, Copyright License, Definitions, Fair Use, Limitations, No Liability, No Other Rights, Notices (+10 more)

### Community 43 - "Android Kotlin Development"
Cohesion: 0.11
Nodes (15): Jetpack Compose UI, Jetpack Compose UI, Models & API Service, Models & API Service, MVVM ViewModels with Jetpack, MVVM ViewModels with Jetpack, Android Kotlin Development, Best Practices (+7 more)

### Community 44 - "OcrTextAnalyzer.kt"
Cohesion: 0.16
Nodes (9): Color, AutoCloseable, Buffer, ByteBuffer, ImageProxy, IntArray, OcrTextAnalyzer, OcrTextMetrics (+1 more)

### Community 45 - "ExternalVoiceModelDefinition"
Cohesion: 0.15
Nodes (7): ExternalVoiceDownloadTask, ExternalVoiceModelDefinition, Flow, StateFlow, ViewModel, VoiceDeletionConfirmationDialog(), VoiceDeletionConfirmationDialogPreview()

### Community 46 - "VoiceSettings.kt"
Cohesion: 0.22
Nodes (14): ExternalVoiceLanguageVoiceGenders, VoiceViewModel, LanguageBadge(), Details(), ImageVector, PaddingValues, VoiceDetails(), VoiceDetailsData() (+6 more)

### Community 47 - "HttpDownloadClient"
Cohesion: 0.14
Nodes (9): DownloadClient, DownloadListener, URI, HttpDownloadClient, Callback, DownloadListener, URI, Call (+1 more)

### Community 48 - "LicenseViewModel"
Cohesion: 0.13
Nodes (20): AnnotatedString, Flow, LicenseMemoryRepository, DialogState, Closed, Confirm, Open, StateFlow (+12 more)

### Community 49 - "PaddleObjectCharacterRecognitionDetectOutput"
Cohesion: 0.19
Nodes (7): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, PaddleObjectCharacterRecognitionDetectInput, PaddleObjectCharacterRecognitionDetectOutput

### Community 50 - "LanguageViewModel"
Cohesion: 0.18
Nodes (9): LanguageViewModel, LanguageSelectionDrawerPreview(), Details(), ImageVector, PaddingValues, LanguageDetails(), LanguageDetailsData(), LanguageDetailsPreview() (+1 more)

### Community 51 - "Spacing.kt"
Cohesion: 0.20
Nodes (14): DownloadButton(), Modifier, LanguageDownloadButtonPreview(), LanguageDeletionConfirmationDialog(), LanguageDeletionConfirmationDialogPreview(), Dp, ListItemColors, Modifier (+6 more)

### Community 52 - "Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage"
Cohesion: 0.15
Nodes (18): jlong, JNIEnv, JNIEXPORT, jobject, jstring, DetectionResult, confidence, isReliable (+10 more)

### Community 53 - "Material Design 3 Theming"
Cohesion: 0.11
Nodes (17): Color Roles Usage, Color System, Custom Color Scheme, Custom Fonts, Custom Shape Usage, Dynamic Color (Material You), Elevation and Shadows, Extended Colors (+9 more)

### Community 54 - "Leanmt"
Cohesion: 0.16
Nodes (4): LeanmtTest, Leanmt, AutoCloseable, LeanmtModel

### Community 55 - "JapaneseTransliterator"
Cohesion: 0.10
Nodes (9): GenericTransliterator, JapaneseTransliterator, Transliteration, TransliterationAdapter, Context, Flow, ViewModel, Token (+1 more)

### Community 56 - "SpeechRecognitionMetadata.kt"
Cohesion: 0.14
Nodes (8): SpeechRecognitionBundleMetadata, SpeechRecognitionFilesMetadata, SpeechRecognitionInferenceFilesMetadata, SpeechRecognitionMetadata, SpeechRecognitionModel, SpeechRecognitionModule, Recognition, executeAsListFlow()

### Community 57 - "VoiceDatabaseRepository.kt"
Cohesion: 0.13
Nodes (9): VoiceBundleMetadata, VoiceInferenceFilesMetadata, VoiceMetadataFile, VoiceModelArchitecture, Kokoro, StyleTTS2, VoiceModelFilesMetadata, VoiceModelMetadata (+1 more)

### Community 58 - "ObjectCharacterRecognitionRecognizerWithFiles"
Cohesion: 0.20
Nodes (4): ObjectCharacterRecognitionRepositoryDatabaseRepository, ObjectCharacterRecognitionRecognizerWithFiles, ObjectCharacterRecognitionDetectorDatabaseModel, ObjectCharacterRecognitionRecognizerDatabaseModel

### Community 59 - "TextToSpeechViewModel.kt"
Cohesion: 0.14
Nodes (8): DownloadExternalDataWorker, ByteArray, Flow, MutableStateFlow, SynthReadyCallback, Flow, StateFlow, ViewModel

### Community 60 - "Theme.kt"
Cohesion: 0.25
Nodes (17): animateColorScheme(), AnimatedBackgroundColors, AnimatedColors, animatedPrimaryColors(), AnimatedSurfaceColors, defaultColorTransitionSpec(), isLAppearanceLight(), Color (+9 more)

### Community 62 - "espeak_ng.cc"
Cohesion: 0.26
Nodes (16): JNIEnv, JNIEXPORT, jobject, jstring, getJniEnv(), Java_app_versta_translate_bridge_speech_ESpeakNG_cancel(), Java_app_versta_translate_bridge_speech_ESpeakNG_construct(), Java_app_versta_translate_bridge_speech_ESpeakNG_initialize() (+8 more)

### Community 66 - "DownloadManager"
Cohesion: 0.17
Nodes (4): DownloadManager, Context, T, WorkRequest

### Community 67 - "TranslationViewModel.kt"
Cohesion: 0.10
Nodes (7): LanguageModel, PivotPairModelFiles, Flow, Job, StateFlow, ViewModel, mutex

### Community 68 - "Timber"
Cohesion: 0.19
Nodes (6): Flow, MutableStateFlow, FileLoggingTree, LocaleUtils, SimpleDateFormat, Timber

### Community 69 - "TarballExtractor"
Cohesion: 0.26
Nodes (5): CompressedFileExtractor, ExtractionProgressListener, Uri, Uri, TarballExtractor

### Community 70 - "VoiceDatabaseRepository"
Cohesion: 0.28
Nodes (3): VoiceDatabaseRepository, VoiceDatabaseModel, VoiceModelDatabaseModel

### Community 71 - "ExternalObjectCharacterRecognitionDownloadTask"
Cohesion: 0.13
Nodes (5): DownloadObjectCharacterRecognitionWorker, ExternalObjectCharacterRecognitionDownloadTask, Flow, StateFlow, ViewModel

### Community 72 - "JNIEnv"
Cohesion: 0.39
Nodes (15): jintArray, jlong, JNIEnv, JNIEXPORT, jobject, findRecognizer(), Java_app_versta_translate_bridge_whisper_Whisper_destroy(), Java_app_versta_translate_bridge_whisper_Whisper_feed() (+7 more)

### Community 73 - "ExternalVoiceModelsRepository"
Cohesion: 0.23
Nodes (7): ExternalVoiceModelsFileRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoiceModelsRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoiceModels

### Community 74 - "TextTranslationLegacy"
Cohesion: 0.17
Nodes (12): Idle, Preparing, Synthesizing, TextToSpeechSynthesisState, TextToSpeechButton(), MinimalTextTranslationOutputButtonRow(), Modifier, TextTranslationInputButtonRow() (+4 more)

### Community 75 - "AudioCapture"
Cohesion: 0.20
Nodes (6): AudioCapture, CoroutineScope, CoroutineScope, Job, MicrophoneCapture, AudioRecord

### Community 76 - "whisper.cc"
Cohesion: 0.22
Nodes (12): advance_front(), vector, Java_app_versta_translate_bridge_whisper_Whisper_process(), probe_and_update(), probe_speech(), ProbeUpdate, pause_mid_ms, valid (+4 more)

### Community 77 - "CustomThemeViewModel"
Cohesion: 0.24
Nodes (10): CustomThemeViewModel, ViewModel, CustomTheme, Obsidian, CustomThemeScene, NavKey, ObsidianThemeMetadata, rememberCustomThemeEntryDecorator() (+2 more)

### Community 78 - "Whisper"
Cohesion: 0.13
Nodes (6): AutoCloseable, FloatArray, IntArray, WhisperSegmentCallback, Whisper, RuntimeException

### Community 79 - "tasteskill: Anti-Slop Frontend Skill"
Cohesion: 0.13
Nodes (15): 0.A Read these signals first, 0.B Output a one-line "Design Read" before generating, 0. BRIEF INFERENCE (Read the Room Before Anything Else), 0.C If the brief is ambiguous, ask one question, do not guess, 0.D Anti-Default Discipline, 13. OUT OF SCOPE, 14. FINAL PRE-FLIGHT CHECK, 1.A Dial Inference (design read → dial values) (+7 more)

### Community 80 - "Appendix B - Canonical Sources (read these before reinventing)"
Cohesion: 0.13
Nodes (15): Appendix B - Canonical Sources (read these before reinventing), Apple Liquid Glass (Apple platforms only), Atlassian, Bootstrap, Carbon, Fluent UI, GOV.UK, Material Web (+7 more)

### Community 81 - "WhisperSpeechRecognition"
Cohesion: 0.19
Nodes (7): MicrophoneCaptureException, AutoCloseable, CoroutineScope, Job, WhisperSegmentCallback, WhisperSpeechRecognition, WhisperSegmentCallback

### Community 82 - "MainActivity.kt"
Cohesion: 0.22
Nodes (8): Bundle, ComponentActivity, Intent, MainActivity, Easing, Spacing, TranslateTheme(), setEdgeToEdgeConfig()

### Community 83 - "Clean Code - Pragmatic AI Coding Standards"
Cohesion: 0.14
Nodes (13): Agent → Script Mapping, AI Coding Style, Anti-Patterns (DON'T), 🔴 Before Editing ANY File (THINK FIRST!), Clean Code - Pragmatic AI Coding Standards, Code Structure, Core Principles, Function Rules (+5 more)

### Community 84 - "LogFileSaver"
Cohesion: 0.19
Nodes (8): FileSaver, FileSaverCallback, FileSaverCallback, Uri, ActivityResultLauncher, ComponentActivity, FileSaverCallback, LogFileSaver

### Community 85 - "ObjectCharacterRecognitionRepository"
Cohesion: 0.18
Nodes (3): Flow, ObjectCharacterRecognitionRepository, ObjectCharacterRecognitionDetectorModel

### Community 86 - "VoiceWithModelFiles"
Cohesion: 0.22
Nodes (5): Flow, Flow, VoiceRepository, VoiceModel, VoiceWithModelFiles

### Community 87 - "Screens"
Cohesion: 0.05
Nodes (40): Modifier, ModalDrawerItem(), NavigationDrawer(), NavigationDrawerRailItem(), NavigationItem, DeleteBackground(), Modifier, SwipeDelete() (+32 more)

### Community 88 - "PaddleOCR"
Cohesion: 0.22
Nodes (7): AutoCloseable, Buffer, ByteBuffer, IntArray, LongArray, PaddleOCR, TextRegionMetrics

### Community 89 - "BubbleActivity.kt"
Cohesion: 0.25
Nodes (8): BubbleActivity, Activity, Bundle, ComponentActivity, Intent, Modifier, LanguageSuggestionDownloadButton(), LanguageSuggestionDrawer()

### Community 90 - "DownloadTask"
Cohesion: 0.18
Nodes (3): ExternalDataDownloadTask, ExternalLanguageDownloadTask, DownloadTask

### Community 91 - "SpeechRecognitionEngine"
Cohesion: 0.10
Nodes (9): CoroutineScope, AutoCloseable, FloatArray, IntArray, SpeechRecognitionEngine, WhisperSegmentCallback, SpeechRecognitionInitialPrompts, SpeechRecognitionMetrics (+1 more)

### Community 92 - "LoggingViewModel"
Cohesion: 0.25
Nodes (6): Context, StateFlow, Uri, ViewModel, LoggingViewModel, FileObserver

### Community 93 - "App Layer Conventions"
Cohesion: 0.15
Nodes (12): App Layer Conventions, Don'ts, Graphify, Inference, Kotlin style, Logging, Naming conventions, Package layout (+4 more)

### Community 95 - "DataMetadata.kt"
Cohesion: 0.21
Nodes (6): DataBundleMetadata, DataMetadata, DataMetadataInterface, TextToSpeechDataFilesMetadata, TextToSpeechDataMetadata, TextToSpeechDataMetadataFile

### Community 97 - "SpeechRecognitionViewModel.kt"
Cohesion: 0.17
Nodes (9): supportedLanguageIsoCodes(), CoroutineScope, Flow, StateFlow, ViewModel, StartResult, MicrophoneUnavailable, NotLoaded (+1 more)

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

### Community 103 - "LanguageViewModel.kt"
Cohesion: 0.20
Nodes (6): Job, StateFlow, ViewModel, LanguageType, Source, Target

### Community 104 - "GDPR Data Handling"
Cohesion: 0.18
Nodes (10): 1. Personal Data Categories, 2. Legal Bases for Processing, 3. Data Subject Rights, Best Practices, Core Concepts, Detailed worked examples and patterns, Do's, Don'ts (+2 more)

### Community 106 - "TranslateBubbleShortcut"
Cohesion: 0.39
Nodes (5): ComponentActivity, Context, TranslateBubbleShortcut, ShortcutInfo, ShortcutManager

### Community 108 - "TranslateBubbleNotification"
Cohesion: 0.29
Nodes (4): Context, TranslateBubbleNotification, Context, TranslateNotification

### Community 109 - "DataWithFiles"
Cohesion: 0.31
Nodes (4): Flow, DataFilesInterface, DataWithFiles, TextToSpeechDataFiles

### Community 110 - "ExternalDataDefinition"
Cohesion: 0.27
Nodes (3): ExternalDataDefinitions, Flow, ExternalDataDefinition

### Community 111 - "Versta.Android"
Cohesion: 0.20
Nodes (9): Architecture, Build & verification, Dependency injection, Documentation, Git conventions, Project identity, Repo map, Testing (+1 more)

### Community 112 - "C++ Coding Standards"
Cohesion: 0.20
Nodes (9): C++ Coding Standards, Class/Type Naming, File Naming, Function/Method Naming, Header Guards / Pragma, Namespace Naming, Organization, Smart Pointers (+1 more)

### Community 113 - "VoiceModelVoiceFiles"
Cohesion: 0.17
Nodes (7): VoiceModelTokenizerFiles, VoiceModelVoiceFiles, directorySize(), SimpleFileVisitor, ArrayList, BasicFileAttributes, FileVisitResult

### Community 114 - "10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know)"
Cohesion: 0.20
Nodes (10): 10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know), Animation Library Choice, Cards & Containers, Galleries & Media, Hero Paradigms, Layout & Grids, Micro-Interactions & Effects, Navigation & Menus (+2 more)

### Community 115 - "Verification Before Completion"
Cohesion: 0.20
Nodes (9): Common Failures, Key Patterns, Overview, Rationalization Prevention, Red Flags - STOP, The Gate Function, The Iron Law, Verification Before Completion (+1 more)

### Community 116 - "FakeRecognizer"
Cohesion: 0.22
Nodes (4): FakeRecognizer, FloatArray, IntArray, WhisperSegmentCallback

### Community 118 - "ExternalDataFileRepository.kt"
Cohesion: 0.33
Nodes (3): ExternalDataFileRepository, ExternalDataDefinitions, Flow

### Community 120 - "DownloadStatus"
Cohesion: 0.20
Nodes (8): Cancelled, Completed, DownloadStatus, Error, Idle, Processing, Progress, Queued

### Community 121 - "LanguageModelMetadata.kt"
Cohesion: 0.20
Nodes (6): LanguageMetadata, LanguageModelArchitecture, BergamotTinyModel, MarianMTModel, LanguageModelConfigurationMetadata, LanguageModelFilesMetadata

### Community 122 - "Implementation Patterns"
Cohesion: 0.22
Nodes (8): Compliance Checklist, gdpr-data-handling — detailed worked examples, Implementation Patterns, Pattern 1: Consent Management, Pattern 2: Data Subject Access Request (DSAR), Pattern 3: Data Retention, Pattern 4: Privacy by Design, Pattern 5: Breach Notification

### Community 123 - "PaddleObjectCharacterRecognitionTokenizer"
Cohesion: 0.25
Nodes (3): LongArray, PaddleObjectCharacterRecognitionTokenizer, Vocabulary

### Community 124 - "LanguagePairBadge"
Cohesion: 0.44
Nodes (7): Color, ImageVector, Modifier, LanguagePairBadge(), LanguagePairBadgeColors, LanguagePairBadgeDefaults, LanguagePairBadgePreview()

### Community 125 - "9. AI TELLS (Forbidden Patterns)"
Cohesion: 0.25
Nodes (8): 9.A Visual & CSS, 9. AI TELLS (Forbidden Patterns), 9.B Typography, 9.C Layout & Spacing, 9.D Content & Data ("Jane Doe" Effect), 9.E External Resources & Components, 9.F Production-Test Tells (banned outright), 9.G EM-DASH BAN (the single most-violated Tell)

### Community 126 - "Core Concepts"
Cohesion: 0.25
Nodes (7): 1. Material Design 3 Principles, 2. Jetpack Compose Layout System, 3. Navigation Patterns, 4. Material 3 Theming, 5. Component Examples, Core Concepts, mobile-android-design — detailed sections

### Community 127 - "Mobile Touch Animation"
Cohesion: 0.25
Nodes (7): Android, Haptic Guidelines, iOS, Mobile Touch Animation, Platform Patterns, Principle Applications, Quick Reference

### Community 128 - "ExternalDataRepository"
Cohesion: 0.43
Nodes (4): ExternalDataRepository, ExternalDataDefinitions, Flow, ExternalData

### Community 129 - "ExternalVoiceModelsMemoryRepository"
Cohesion: 0.50
Nodes (4): ExternalVoiceModelsMemoryRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoice

### Community 130 - "LanguageBadge.kt"
Cohesion: 0.36
Nodes (6): Color, Dp, Modifier, LanguageBadgeColors, LanguageBadgeDefaults, LanguageBadgePreview()

### Community 131 - "LanguageSelectionDrawer.kt"
Cohesion: 0.64
Nodes (7): Context, Modifier, LanguageSelectionDrawer(), LanguageSelectionListItem(), LanguageSelectionNoItems(), LanguageSelectionSourceLanguage(), LanguageSelectionTargetLanguage()

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

### Community 140 - "AudioPlayer"
Cohesion: 0.29
Nodes (3): AudioPlayer, ByteArray, FloatArray

### Community 141 - "AudioTrackPlayer"
Cohesion: 0.29
Nodes (3): AudioTrackPlayer, ByteArray, FloatArray

### Community 142 - "LanguageDetect"
Cohesion: 0.33
Nodes (3): AutoCloseable, LanguageDetect, LanguageDetectResult

### Community 145 - "LanguageSelector.kt"
Cohesion: 0.62
Nodes (6): Context, CornerBasedShape, Modifier, PaddingValues, LanguageSelector(), LanguageSelectorButton()

### Community 146 - "MinimalLanguageSelector.kt"
Cohesion: 0.62
Nodes (6): Context, CornerBasedShape, Modifier, PaddingValues, MinimalLanguageSelector(), MinimalLanguageSelectorButton()

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

### Community 156 - "ViewModelProvider"
Cohesion: 0.60
Nodes (3): T, viewModelFactory(), ViewModelProvider

### Community 157 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 158 - "7. DIAL DEFINITIONS (Technical Reference)"
Cohesion: 0.50
Nodes (4): 7. DIAL DEFINITIONS (Technical Reference), DESIGN_VARIANCE (Level 1-10), MOTION_INTENSITY (Level 1-10), VISUAL_DENSITY (Level 1-10)

### Community 159 - "WritingDirection"
Cohesion: 0.50
Nodes (3): WritingDirection, LTR, RTL

### Community 160 - "SliderPredefinedValues"
Cohesion: 0.67
Nodes (3): Modifier, T, SliderPredefinedValues()

### Community 161 - "FileSaverCallback"
Cohesion: 0.50
Nodes (3): FileSaverCallback, FileSaverCallback, Uri

## Knowledge Gaps
- **525 isolated node(s):** `language`, `isReliable`, `confidence`, `hints`, `languages` (+520 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `string` connect `NodeFeature` to `PaddleOCR`, `Java_app_versta_translate_bridge_whisper_Whisper_create`, `whisper.cc`, `Recognizer`?**
  _High betweenness centrality (0.087) - this node is a cross-community bridge._
- **Why does `WhisperSpeechRecognition` connect `WhisperSpeechRecognition` to `SpeechRecognitionViewModel.kt`, `SpeechRecognitionMockInference`, `WhisperSpeechRecognitionTest`, `AudioCapture`, `MainApplication.kt`, `SpeechContextStoreTest`, `SpeechRecognitionEngine`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Why does `ApplicationModule` connect `MainApplication.kt` to `ExternalDataRepository`, `LanguageMemoryRepository`, `StyleTextToSpeechInference`, `SpeechRecognitionViewModel`, `VoiceGender`, `PaddleObjectCharacterRecognitionInference.kt`, `AudioTrackPlayer`, `CameraTranslationViewModel`, `NavigationViewModel`, `LanguageOption`, `LanguageSelector.kt`, `LanguageDatabaseRepository`, `LanguagePair`, `ExternalLanguagePairDefinition`, `LanguageModelConfiguration`, `ExternalSpeechRecognitionModelDefinition`, `ScaffoldCompactBar.kt`, `ObjectCharacterRecogniserResult`, `ExternalObjectCharacterRecognitionModelDefinition`, `SpeechRecognitionMockInference`, `DataType`, `SpeechRecognitionDatabaseRepository`, `BergamotTinyInference`, `DatabaseContainer`, `VoiceSettings.kt`, `LanguageViewModel`, `Leanmt`, `ObjectCharacterRecognitionRecognizerWithFiles`, `TranslationPreferenceDataStoreRepository`, `TranslationPreferenceRepository`, `TextToSpeechViewModel`, `TarballExtractor`, `VoiceDatabaseRepository`, `ExternalVoiceModelsRepository`, `CustomThemeViewModel`, `WhisperSpeechRecognition`, `ObjectCharacterRecognitionRepository`, `VoiceWithModelFiles`, `LoggingViewModel`, `LanguagePreferenceRepository`, `ExternalDataFileRepository.kt`, `StyleTextToSpeech2Tokenizer`, `PaddleObjectCharacterRecognitionTokenizer`?**
  _High betweenness centrality (0.082) - this node is a cross-community bridge._
- **What connects `language`, `isReliable`, `confidence` to the rest of the system?**
  _525 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `PaddleOCR` be split into smaller, more focused modules?**
  _Cohesion score 0.06107594936708861 - nodes in this community are weakly interconnected._
- **Should `MeshGradientBackground.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.0593607305936073 - nodes in this community are weakly interconnected._
- **Should `LanguageMemoryRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.06262626262626263 - nodes in this community are weakly interconnected._