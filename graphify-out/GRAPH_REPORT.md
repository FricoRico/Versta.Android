# Graph Report - .  (2026-08-08)

## Corpus Check
- 251 files · ~111,517 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2301 nodes · 4111 edges · 156 communities (138 shown, 18 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 374 edges (avg confidence: 0.81)
- Token cost: 42,000 input · 3,500 output

## Community Hubs (Navigation)
- Paddle OCR JNI Bridge
- Voice Model File Repository
- Project Docs & Issue Templates
- LeanMT JNI Bridge
- OCR Text Analyzer Native
- Camera Translation Flow
- Download Status State
- TTS Preference DataStore
- Frontend Design Skills
- Audio Mock Player
- Download Workers
- OCR Inference
- TextToSpeech ViewModel
- OCR Recognition Database
- Translation ViewModel State
- External OCR Model Repository
- Language ViewModel
- StyleTTS2 Inference
- Language Database Repository
- External Language Model Repository
- ESpeak NG Bridge
- PaddleOCR Tokenizer
- Scaffold Navigation
- Navigation Screens
- TextTranslation ViewModel
- SQLDelight Database Container
- Download Client
- Language Model Queries
- OCR Recognition Repository
- Tensor Utils
- Navigation ViewModel
- Language Badge UI
- Language Detect JNI
- Generic Transliterator
- Download Button UI
- GDPR Compliance Skill
- PaddleOCR Detector
- Android Models & API Skill
- Bergamot Tiny Inference
- Data Database Repository
- Language Preference DataStore
- Scaffold Compact Bar UI
- Theme Animation
- OCR ViewModel
- espeak-ng JNI Bridge
- External OCR Model Metadata
- Translation Preference DataStore
- Translation Preference Memory
- Translation Preference Repository
- Plan Reviewer Skill
- Compressed File Extractor
- Language Preference Memory
- OCR Analyzer
- Paragraph Grouping Postprocessor
- Voice Database Repository
- Code Review Skill
- Compose Component Reference
- PaddleOCR Bridge
- Mobile Design Reference
- Audio Player
- External Data & TTS Tokenizer
- File Saver
- Language Model Loading
- OCR Detector Repository
- OCR Text Analyzer Bridge
- Language Pair Utils
- Voice Model Metadata
- Custom Theme ViewModel
- License ViewModel
- Logging ViewModel
- Settings Button Item
- Language Memory Repository
- Language Preference Repository
- TTS Synthesis State
- Button Card UI
- TextField UI
- Android Navigation Reference
- Material 3 Theming Reference
- File Picker
- External Data Memory Repository
- Language Model Metadata Database
- Data Metadata
- Divider UI
- Navigation Drawer
- Scaffold Compact Bar
- Clean Code Skill
- TDD Skill
- Bubble Notification
- Data With Files
- Language Repository
- StyleTTS2 Tokenizer
- OpenJTalk Bridge
- Bubble Activity
- Main Activity
- External Data File Repository
- License DataStore Repository
- Voice Repository
- OCR Detector With Files
- File Walk Utils
- Main Application Setup
- Color Extensions
- License Memory Repository
- Voice Model Files
- Leanmt Service
- Language Selection Drawer
- OCR Deletion Dialog
- Modal Bottom Sheet
- Jetpack Compose UI Reference
- Code Simplification Skill
- Trial License Drawer
- File Hash Validator
- Translate Bubble Shortcut
- External Data Definitions
- TTS Inference
- TTS Mock Inference
- Language Selector
- Minimal Language Selector
- Slider Predefined Values
- Language Details Screen
- Legacy TextTranslation UI
- Button Color Defaults
- C++ Coding Standards Skill
- Language Detect Bridge
- External Language Model Metadata
- File Visitor Utils
- Voice Voice Files
- Settings Defaults Colors
- Verification Skill
- Vocabulary JNI
- PaddleOCR Tokenizer
- File Walk Utils
- File Walk Utils
- Privacy Policy Screen
- Tensor Utils JNI
- Notification Activity
- Moses Punctuation Normalizer
- Font Weight
- TTS Settings Screen
- Theme Animations & Spacing
- ViewModel Factory Utils
- Gradle Wrapper Script
- Voice Model Upsert
- Vocabulary Loading
- Content Color
- Logarithmic Slider
- Locale Utils
- Device Utils

## God Nodes (most connected - your core abstractions)
1. `NavigationViewModel` - 66 edges
2. `Language` - 53 edges
3. `PaddleOCR` - 51 edges
4. `LanguageViewModel` - 50 edges
5. `ScaffoldViewModel` - 49 edges
6. `TextToSpeechViewModel` - 47 edges
7. `LanguagePair` - 41 edges
8. `ApplicationModuleInterface` - 37 edges
9. `ApplicationModule` - 37 edges
10. `CameraTranslationViewModel` - 37 edges

## Surprising Connections (you probably didn't know these)
- `Readability & Simplicity Axis` --semantically_similar_to--> `Code Simplification Skill`  [INFERRED] [semantically similar]
  .agents/skills/code-review-and-quality/SKILL.md → .agents/skills/code-simplification/SKILL.md
- `Top-level CMakeLists` --conceptually_related_to--> `whisper.cpp`  [AMBIGUOUS]
  app/native/jni/CMakeLists.txt → AGENTS.md
- `Android Kotlin Development Skill` --semantically_similar_to--> `Android Mobile Design Skill`  [INFERRED] [semantically similar]
  .agents/skills/android-kotlin-development/SKILL.md → .agents/skills/mobile-android-design/SKILL.md
- `Jetpack Compose UI Reference` --semantically_similar_to--> `Compose Component Library Reference`  [INFERRED] [semantically similar]
  .agents/skills/android-kotlin-development/references/jetpack-compose-ui.md → .agents/skills/mobile-android-design/references/compose-components.md
- `Clean Code Skill` --semantically_similar_to--> `Code Simplification Skill`  [INFERRED] [semantically similar]
  .agents/skills/clean-code/SKILL.md → .agents/skills/code-simplification/SKILL.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Design Configuration Dials** — _agents_skills_design_taste_frontend_skill_design_variance, _agents_skills_design_taste_frontend_skill_motion_intensity, _agents_skills_design_taste_frontend_skill_visual_density [EXTRACTED 1.00]
- **Five-Axis Code Review Framework** — _agents_skills_code_review_and_quality_skill_correctness_axis, _agents_skills_code_review_and_quality_skill_readability_axis, _agents_skills_code_review_and_quality_skill_architecture_axis, _agents_skills_code_review_and_quality_skill_security_axis, _agents_skills_code_review_and_quality_skill_performance_axis [EXTRACTED 1.00]
- **MVVM Data Flow Across Reference Guides** — _agents_skills_android_kotlin_development_references_models_api_service_apiservice, _agents_skills_android_kotlin_development_references_mvvm_viewmodels_with_jetpack_userviewmodel, _agents_skills_android_kotlin_development_references_mvvm_viewmodels_with_jetpack_itemsviewmodel, _agents_skills_android_kotlin_development_references_jetpack_compose_ui_homescreen, _agents_skills_android_kotlin_development_references_jetpack_compose_ui_profilescreen [EXTRACTED 1.00]
- **GitHub Issue Template System** — _github_issue_template_1_bug_yaml, _github_issue_template_2_story_yaml, _github_issue_template_3_task_yaml, _github_issue_template_config_yaml [EXTRACTED 1.00]
- **On-Device Native Inference Stack** — agents_leanmt, agents_whisper_cpp, app_agents_paddle_ocr, agents_espeak_ng, agents_open_jtalk, agents_cld2, agents_styletts2, agents_onnxruntime [EXTRACTED 1.00]
- **JNI Bridge Pattern** — app_native_agents_jni_binding, app_native_agents_opaque_handle, app_native_agents_threading, app_agents_jni_inference [EXTRACTED 1.00]

## Communities (156 total, 18 thin omitted)

### Community 0 - "Paddle OCR JNI Bridge"
Cohesion: 0.06
Nodes (47): argmax(), jfloat, jint, jlong, JNIEnv, JNIEXPORT, jobject, Mat (+39 more)

### Community 1 - "Voice Model File Repository"
Cohesion: 0.06
Nodes (27): ExternalVoiceModelsFileRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoiceModelsMemoryRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoiceModelsRepository, ExternalVoiceModelDefinitions (+19 more)

### Community 2 - "Project Docs & Issue Templates"
Cohesion: 0.08
Nodes (50): Bug Issue Template, Story Issue Template, Task Issue Template, GitHub Issue Templates, Issue Template Config, app_versta_translate_bridge Native Library, cld2, espeak-ng (+42 more)

### Community 3 - "LeanMT JNI Bridge"
Cohesion: 0.07
Nodes (42): jclass, jlong, JNIEnv, JNIEXPORT, jobject, jstring, jobjectArray LEANMT_JNI(), jstr() (+34 more)

### Community 4 - "OCR Text Analyzer Native"
Cohesion: 0.10
Nodes (28): jfloat, jint, jlong, JNIEnv, JNIEXPORT, jobject, Mat, Scalar (+20 more)

### Community 5 - "Camera Translation Flow"
Cohesion: 0.08
Nodes (19): CameraTranslationResult, CameraTranslationViewModel, Context, Flow, LifecycleOwner, StateFlow, ViewModel, CameraPermissionDenied() (+11 more)

### Community 6 - "Download Status State"
Cohesion: 0.07
Nodes (16): Cancelled, Completed, DownloadStatus, Error, Idle, Processing, Progress, Queued (+8 more)

### Community 7 - "TTS Preference DataStore"
Cohesion: 0.08
Nodes (9): Flow, TextToSpeechPreferenceDataStoreRepository, Flow, TextToSpeechPreferenceMemoryRepository, Flow, TextToSpeechPreferenceRepository, VoiceGender, Female (+1 more)

### Community 8 - "Frontend Design Skills"
Cohesion: 0.07
Nodes (36): Anti-Slop Frontend Design Skill, AI Tells (Forbidden Patterns), Anti-Center Bias, Anti-Default Discipline, Block Library, Brief Inference, Color Consistency Lock, Copy Self-Audit (+28 more)

### Community 9 - "Audio Mock Player"
Cohesion: 0.08
Nodes (18): AudioMockPlayer, ByteArray, FloatArray, DataMemoryRepository, TextToSpeechMockTokenizer, TranslationMockInference, VoiceMemoryRepository, ErrorAlertDialog() (+10 more)

### Community 10 - "Download Workers"
Cohesion: 0.09
Nodes (12): DownloadExternalDataWorker, DownloadLanguageWorker, DownloadObjectCharacterRecognitionWorker, DownloadVoiceWorker, DownloadQueue, DownloadWorker, DownloadListener, DownloadListener (+4 more)

### Community 11 - "OCR Inference"
Cohesion: 0.11
Nodes (14): ImageProxy, ObjectCharacterRecognitionInference, AutoCloseable, ByteBuffer, ImageProxy, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognition (+6 more)

### Community 12 - "TextToSpeech ViewModel"
Cohesion: 0.11
Nodes (6): FloatArray, Flow, Job, StateFlow, ViewModel, TextToSpeechViewModel

### Community 13 - "OCR Recognition Database"
Cohesion: 0.08
Nodes (15): ObjectCharacterRecognitionArchitecture, PaddleOCR, ObjectCharacterRecognitionBundleMetadata, ObjectCharacterRecognitionDetectorFilesMetadata, ObjectCharacterRecognitionDetectorInferenceFilesMetadata, ObjectCharacterRecognitionDetectorMetadata, ObjectCharacterRecognitionMetadataFile, ObjectCharacterRecognitionModule (+7 more)

### Community 14 - "Translation ViewModel State"
Cohesion: 0.11
Nodes (13): Completed, Error, Idle, InProgress, Flow, Job, StateFlow, ViewModel (+5 more)

### Community 15 - "External OCR Model Repository"
Cohesion: 0.11
Nodes (9): ExternalObjectCharacterRecognitionModelsMemoryRepository, Flow, ExternalObjectCharacterRecognitionModelsRepository, Flow, ExternalObjectCharacterRecognitionDownloadTask, ExternalObjectCharacterRecognitionModelDefinition, ExternalObjectCharacterRecognitionModels, ExternalObjectCharacterRecognitionModelWithState (+1 more)

### Community 16 - "Language ViewModel"
Cohesion: 0.12
Nodes (11): Job, StateFlow, ViewModel, LanguageType, Source, Target, LanguageViewModel, Modifier (+3 more)

### Community 17 - "StyleTTS2 Inference"
Cohesion: 0.11
Nodes (11): ByteBuffer, FloatArray, LongArray, OrtSession, Waveform, StyleTextToSpeechInference, OnnxTensorLike, OrtSession (+3 more)

### Community 18 - "Language Database Repository"
Cohesion: 0.14
Nodes (6): Flow, LanguageDatabaseRepository, PivotPair, LanguageModelMetadata, LanguageDatabaseModel, LanguageModelDatabaseModel

### Community 19 - "External Language Model Repository"
Cohesion: 0.16
Nodes (10): ExternalLanguageModelsFileRepository, Flow, ExternalLanguageModelsMemoryRepository, Flow, ExternalLanguageModelsRepository, Flow, ExternalLanguageMetadata, ExternalLanguageModels (+2 more)

### Community 20 - "ESpeak NG Bridge"
Cohesion: 0.13
Nodes (6): ESpeakNG, AutoCloseable, ByteArray, Flow, MutableStateFlow, SynthReadyCallback

### Community 21 - "PaddleOCR Tokenizer"
Cohesion: 0.13
Nodes (8): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognitionRecognizeInput, PaddleObjectCharacterRecognitionRecognizeOutput

### Community 22 - "Scaffold Navigation"
Cohesion: 0.17
Nodes (19): NavKey, ScaffoldComponent, ScaffoldRowScopeComponent, ViewModel, ScaffoldActionsComponent, ScaffoldBottomBarComponent, ScaffoldComponentMetadata, ScaffoldComponents (+11 more)

### Community 23 - "Navigation Screens"
Cohesion: 0.09
Nodes (22): About, ApplicationLogs, NavKey, LanguageAttributions, LanguageDetails, LanguageSettings, ObjectCharacterRecognitionAttributions, ObjectCharacterRecognitionDetails (+14 more)

### Community 24 - "TextTranslation ViewModel"
Cohesion: 0.13
Nodes (7): Context, Flow, ViewModel, TextTranslationViewModel, Modifier, TranslationTextField(), TranslationTextFieldMinimalPreview()

### Community 25 - "SQLDelight Database Container"
Cohesion: 0.13
Nodes (6): DatabaseContainer, Migration, Migration3, Context, Migration4, Migration6

### Community 26 - "Download Client"
Cohesion: 0.12
Nodes (9): DownloadClient, DownloadListener, URI, HttpDownloadClient, Callback, DownloadListener, URI, Call (+1 more)

### Community 27 - "Language Model Queries"
Cohesion: 0.13
Nodes (12): LongArray, LongArray, AutoDetectLanguage, fromId(), fromIsoCode(), fromIsoCodes(), fromLocale(), Context (+4 more)

### Community 28 - "OCR Recognition Repository"
Cohesion: 0.16
Nodes (4): ObjectCharacterRecognitionRepositoryDatabaseRepository, ObjectCharacterRecognitionRecognizerWithFiles, ObjectCharacterRecognitionDetectorDatabaseModel, ObjectCharacterRecognitionRecognizerDatabaseModel

### Community 29 - "Tensor Utils"
Cohesion: 0.15
Nodes (17): Buffer, TensorUtils, closeTensor(), closeTensorBuffer(), createFloatTensor(), createIntTensor(), createLongTensor(), determineShape() (+9 more)

### Community 30 - "Navigation ViewModel"
Cohesion: 0.16
Nodes (7): NavKey, ViewModel, NavigationViewModel, PaddingValues, VoiceAttributions(), VoiceAttributionsPreview(), NavBackStack

### Community 31 - "Language Badge UI"
Cohesion: 0.14
Nodes (16): Color, Dp, Modifier, LanguageBadge(), LanguageBadgeColors, LanguageBadgeDefaults, LanguageBadgePreview(), LanguageDeletionConfirmationDialog() (+8 more)

### Community 32 - "Language Detect JNI"
Cohesion: 0.15
Nodes (18): jlong, JNIEnv, JNIEXPORT, jobject, jstring, DetectionResult, confidence, isReliable (+10 more)

### Community 33 - "Generic Transliterator"
Cohesion: 0.13
Nodes (6): GenericTransliterator, JapaneseTransliterator, Transliteration, TransliterationAdapter, Token, Transliterator

### Community 34 - "Download Button UI"
Cohesion: 0.15
Nodes (16): DownloadButton(), Modifier, LanguageDownloadButtonPreview(), Dp, ListItemColors, Modifier, SettingsHeaderItem(), SettingsHeaderItemPreview() (+8 more)

### Community 35 - "GDPR Compliance Skill"
Cohesion: 0.15
Nodes (19): GDPR Implementation Patterns Reference, BreachNotificationHandler Class, GDPR Compliance Checklist, Consent Data Model Schema, ConsentManager Class, DataMinimization Class, DataRetentionPolicy Class, DSARHandler Class (+11 more)

### Community 36 - "PaddleOCR Detector"
Cohesion: 0.16
Nodes (7): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, PaddleObjectCharacterRecognitionDetectInput, PaddleObjectCharacterRecognitionDetectOutput

### Community 37 - "Android Models & API Skill"
Cohesion: 0.20
Nodes (18): Models & API Service Reference, ApiService Retrofit Interface, Auth Interceptor Pattern, Item Data Class, NetworkModule DI Provider, PreferencesManager, User Data Class, MVVM ViewModels Reference (+10 more)

### Community 38 - "Bergamot Tiny Inference"
Cohesion: 0.15
Nodes (8): BergamotTinyInference, create(), AutoCloseable, LeanmtModel, ncreate(), ndestroy(), LeanmtModelConfig, LeanmtPackage

### Community 39 - "Data Database Repository"
Cohesion: 0.17
Nodes (7): DataDatabaseRepository, DataRepository, Flow, DataModel, DataType, TTS, DataDatabaseModel

### Community 40 - "Language Preference DataStore"
Cohesion: 0.18
Nodes (3): Flow, LanguagePreferenceDataStoreRepository, LanguageOptionPair

### Community 41 - "Scaffold Compact Bar UI"
Cohesion: 0.16
Nodes (14): ScaffoldCompactBarBackNavigationIcon(), ScaffoldCompactBarTitle(), ApplicationLogs(), FileSaverCallback, ApplicationLogsPreview(), FileSaverCallback, PaddingValues, Uri (+6 more)

### Community 42 - "Theme Animation"
Cohesion: 0.23
Nodes (17): animateColorScheme(), AnimatedBackgroundColors, AnimatedColors, animatedPrimaryColors(), AnimatedSurfaceColors, defaultColorTransitionSpec(), isLAppearanceLight(), Color (+9 more)

### Community 43 - "OCR ViewModel"
Cohesion: 0.17
Nodes (11): app, StateFlow, ViewModel, ObjectCharacterRecognitionViewModel, PaddingValues, ObjectCharacterRecognitionAttributions(), ObjectCharacterRecognitionAttributionsPreview(), PaddingValues (+3 more)

### Community 44 - "espeak-ng JNI Bridge"
Cohesion: 0.26
Nodes (16): jclass, JNIEnv, JNIEXPORT, jobject, jstring, getJniEnv(), Java_app_versta_translate_bridge_speech_ESpeakNG_cancel(), Java_app_versta_translate_bridge_speech_ESpeakNG_construct() (+8 more)

### Community 45 - "External OCR Model Metadata"
Cohesion: 0.17
Nodes (5): ExternalObjectCharacterRecognitionModelsFileRepository, Flow, Flow, ObjectCharacterRecognitionRepository, ObjectCharacterRecognitionDetectorWithFiles

### Community 49 - "Plan Reviewer Skill"
Cohesion: 0.14
Nodes (16): Plan Document Reviewer Prompt Template, Plan Review Calibration, Plan Review Categories (Completeness/Spec/Tasks/Buildability), Plan Document Reviewer Subagent, Writing Plans Skill, Bite-Sized Task Granularity, Executing-Plans Skill, Execution Handoff (+8 more)

### Community 50 - "Compressed File Extractor"
Cohesion: 0.24
Nodes (5): CompressedFileExtractor, ExtractionProgressListener, Uri, Uri, TarballExtractor

### Community 51 - "Language Preference Memory"
Cohesion: 0.17
Nodes (3): Flow, LanguagePreferenceMemoryRepository, LanguageOption

### Community 52 - "OCR Analyzer"
Cohesion: 0.14
Nodes (7): ImageProxy, ObjectCharacterRecognitionAnalyzer, OcrPostProcessor, OcrPostProcessorContext, OcrPostProcessorPipeline, TextStyleAnalysisPostProcessor, ImageAnalysis

### Community 53 - "Paragraph Grouping Postprocessor"
Cohesion: 0.26
Nodes (3): Color, ParagraphGroupingPostProcessor, ObjectCharacterRecogniserResult

### Community 54 - "Voice Database Repository"
Cohesion: 0.22
Nodes (4): VoiceDatabaseRepository, VoiceModelMetadata, VoiceDatabaseModel, VoiceModelDatabaseModel

### Community 55 - "Code Review Skill"
Cohesion: 0.14
Nodes (15): Code Review and Quality Skill, Architecture Review Axis, Change Sizing Guidelines, Correctness Review Axis, Dead Code Hygiene, Dependency Discipline, Five-Axis Review, Honesty in Review (+7 more)

### Community 56 - "Compose Component Reference"
Cohesion: 0.14
Nodes (15): Compose Component Library Reference, AsyncState Sealed Class, DateTimePickerExample, DeleteConfirmationDialog, ExpandableCard AnimatedVisibility, Sticky Header GroupedList, ItemList LazyColumn, LoginForm (+7 more)

### Community 57 - "PaddleOCR Bridge"
Cohesion: 0.18
Nodes (8): AutoCloseable, Buffer, ByteBuffer, IntArray, LongArray, PaddleOCR, TextRegionMetrics, ObjectCharacterRecogniserColors

### Community 58 - "Mobile Design Reference"
Cohesion: 0.18
Nodes (14): Mobile Design Core Concepts Reference, AppTypography (Details), Component Examples Section, Compose Layout System, FeatureCard Composable, Material Design 3 Principles, Material 3 Theming Section, Navigation Patterns (+6 more)

### Community 59 - "Audio Player"
Cohesion: 0.14
Nodes (6): AudioPlayer, ByteArray, FloatArray, AudioTrackPlayer, ByteArray, FloatArray

### Community 60 - "External Data & TTS Tokenizer"
Cohesion: 0.21
Nodes (7): ExternalDataRepository, TextToSpeechTokenizer, TranslationInference, ApplicationModule, ApplicationModuleInterface, OrtEnvironment, OpenJTalk

### Community 61 - "File Saver"
Cohesion: 0.15
Nodes (8): FileSaver, FileSaverCallback, FileSaverCallback, Uri, ActivityResultLauncher, ComponentActivity, FileSaverCallback, LogFileSaver

### Community 62 - "Language Model Loading"
Cohesion: 0.22
Nodes (5): LanguageModel, LanguageModelConfiguration, LanguageModelFiles, load(), PivotPairModelFiles

### Community 63 - "OCR Detector Repository"
Cohesion: 0.15
Nodes (3): Flow, ObjectCharacterRecognitionRepositoryMemoryRepository, ObjectCharacterRecognitionDetectorModel

### Community 64 - "OCR Text Analyzer Bridge"
Cohesion: 0.19
Nodes (7): AutoCloseable, Buffer, ByteBuffer, ImageProxy, IntArray, OcrTextAnalyzer, OcrTextMetrics

### Community 65 - "Language Pair Utils"
Cohesion: 0.19
Nodes (3): LanguagePair, LanguageBundleMetadata, Flow

### Community 66 - "Voice Model Metadata"
Cohesion: 0.15
Nodes (8): VoiceBundleMetadata, VoiceInferenceFilesMetadata, VoiceMetadataFile, VoiceModelArchitecture, Kokoro, StyleTTS2, VoiceModelFilesMetadata, VoiceTokenizerFilesMetadata

### Community 67 - "Custom Theme ViewModel"
Cohesion: 0.19
Nodes (11): CustomThemeViewModel, ViewModel, CustomTheme, Obsidian, CustomThemeScene, NavKey, obsidian(), ObsidianThemeMetadata (+3 more)

### Community 68 - "License ViewModel"
Cohesion: 0.21
Nodes (9): DialogState, Closed, Confirm, Open, StateFlow, ViewModel, LicenseViewModel, TrialLicenseConfirmationDialog() (+1 more)

### Community 69 - "Logging ViewModel"
Cohesion: 0.20
Nodes (6): Context, StateFlow, Uri, ViewModel, LoggingViewModel, FileObserver

### Community 70 - "Settings Button Item"
Cohesion: 0.22
Nodes (12): Composable, Dp, ListItemColors, Modifier, PaddingValues, SettingsButtonItem(), SettingsButtonItemContent(), SettingsButtonItemPreview() (+4 more)

### Community 73 - "TTS Synthesis State"
Cohesion: 0.22
Nodes (10): Idle, Preparing, Synthesizing, TextToSpeechSynthesisState, TextToSpeechButton(), Modifier, MinimalTextTranslation(), MinimalTextTranslationOutput() (+2 more)

### Community 74 - "Button Card UI"
Cohesion: 0.24
Nodes (8): ButtonCard(), ButtonCardColors, ButtonCardDefaults, ButtonCardPreview(), Color, ImageVector, Modifier, ButtonColors

### Community 75 - "TextField UI"
Cohesion: 0.23
Nodes (9): Color, Composable, Modifier, Shape, TextField(), TextFieldDefaults, TextFieldPreview(), TextFieldColors (+1 more)

### Community 76 - "Android Navigation Reference"
Cohesion: 0.26
Nodes (12): Android Navigation Reference, AnimatedNavigation Composable, AppNavigation Composable, BottomNavDestination Enum, DeepLinkNavigation Composable, MainActivity Deep Link Handling, ModalDrawerNavigation Composable, NavigationEvent Sealed Class (+4 more)

### Community 77 - "Material 3 Theming Reference"
Cohesion: 0.27
Nodes (12): Material 3 Theming Reference, Adaptive Layout Window Size Classes, AppShapes Shape System, AppTheme Composable, Custom Font Loading, DarkColorScheme, ExtendedColors CompositionLocal, FoldableAwareLayout (+4 more)

### Community 78 - "File Picker"
Cohesion: 0.21
Nodes (6): FilePicker, FilePickerCallback, Uri, ActivityResultLauncher, ComponentActivity, ModelFilePicker

### Community 79 - "External Data Memory Repository"
Cohesion: 0.24
Nodes (4): ExternalDataMemoryRepository, ExternalDataDefinitions, Flow, ExternalDataDefinition

### Community 80 - "Language Model Metadata Database"
Cohesion: 0.17
Nodes (6): LanguageBundleData, LanguageMetadata, LanguageModelArchitecture, MarianMTModel, LanguageModelConfigurationMetadata, LanguageModelFilesMetadata

### Community 81 - "Data Metadata"
Cohesion: 0.21
Nodes (6): DataBundleMetadata, DataMetadata, DataMetadataInterface, TextToSpeechDataFilesMetadata, TextToSpeechDataMetadata, TextToSpeechDataMetadataFile

### Community 82 - "Divider UI"
Cohesion: 0.26
Nodes (9): Divider(), Dp, ListDivider(), About(), AboutPreview(), PaddingValues, PaddingValues, Troubleshooting() (+1 more)

### Community 83 - "Navigation Drawer"
Cohesion: 0.26
Nodes (9): Modifier, ModalDrawerItem(), NavigationDrawer(), NavigationDrawerRailItem(), NavigationItem, Router(), Dp, Modifier (+1 more)

### Community 84 - "Scaffold Compact Bar"
Cohesion: 0.26
Nodes (9): Color, Modifier, PaddingValues, ScaffoldCompactBar(), ScaffoldCompactBarDefaults, ScaffoldCompactBarEmptyActions(), ScaffoldCompactBarMenuNavigationIcon(), ScaffoldCompactBarSettingsActions() (+1 more)

### Community 85 - "Clean Code Skill"
Cohesion: 0.18
Nodes (11): Clean Code Skill, Boy Scout Rule, Don't Repeat Yourself (DRY), Small Function Rules, Guard Clauses Pattern, Keep It Simple (KISS), Revealing Intent Naming Rules, Self-Check Before Completing (+3 more)

### Community 86 - "TDD Skill"
Cohesion: 0.22
Nodes (11): Test-Driven Development Skill, Arrange-Act-Assert Pattern, Beyonce Rule, Browser Testing with DevTools MCP, DAMP Over DRY in Tests, Discover the Stack First, Prove-It Pattern (Bug Reproduction), RED-GREEN-REFACTOR Cycle (+3 more)

### Community 87 - "Bubble Notification"
Cohesion: 0.25
Nodes (4): Context, TranslateBubbleNotification, Context, TranslateNotification

### Community 88 - "Data With Files"
Cohesion: 0.31
Nodes (6): Flow, DataFilesInterface, DataWithFiles, load(), size(), TextToSpeechDataFiles

### Community 89 - "Language Repository"
Cohesion: 0.24
Nodes (3): Flow, LanguageRepository, LanguageModelPair

### Community 91 - "OpenJTalk Bridge"
Cohesion: 0.22
Nodes (4): AutoCloseable, Flow, MutableStateFlow, OpenJTalk

### Community 92 - "Bubble Activity"
Cohesion: 0.25
Nodes (5): BubbleActivity, Activity, Bundle, ComponentActivity, Intent

### Community 93 - "Main Activity"
Cohesion: 0.25
Nodes (5): Bundle, ComponentActivity, Intent, MainActivity, setEdgeToEdgeConfig()

### Community 94 - "External Data File Repository"
Cohesion: 0.29
Nodes (3): ExternalDataFileRepository, ExternalDataDefinitions, Flow

### Community 95 - "License DataStore Repository"
Cohesion: 0.20
Nodes (4): Flow, LicenseDataStoreRepository, Flow, LicenseRepository

### Community 96 - "Voice Repository"
Cohesion: 0.24
Nodes (4): Flow, Flow, VoiceRepository, VoiceWithModelFiles

### Community 97 - "OCR Detector With Files"
Cohesion: 0.29
Nodes (4): load(), ObjectCharacterRecognitionDetectorInferenceFiles, ObjectCharacterRecognitionRecognizerInferenceFiles, ObjectCharacterRecognitionRecognizerTokenizerFiles

### Community 98 - "File Walk Utils"
Cohesion: 0.36
Nodes (6): BasicFileAttributes, FileVisitResult, SimpleFileVisitor, size(), SimpleFileVisitor, SimpleFileVisitor

### Community 99 - "Main Application Setup"
Cohesion: 0.27
Nodes (4): MainApplication, FileLoggingTree, Application, Timber

### Community 100 - "Color Extensions"
Cohesion: 0.62
Nodes (9): complementary(), darken(), hslToColor(), Color, FloatArray, lighten(), lightness(), rgbToHsl() (+1 more)

### Community 101 - "License Memory Repository"
Cohesion: 0.25
Nodes (5): Flow, LicenseMemoryRepository, Modifier, TrialLicenseCard(), TrialLicenseCardPreview()

### Community 102 - "Voice Model Files"
Cohesion: 0.31
Nodes (4): load(), size(), VoiceModelInferenceFiles, VoiceModelTokenizerFiles

### Community 103 - "Leanmt Service"
Cohesion: 0.33
Nodes (6): create(), AutoCloseable, LeanmtService, ncreate(), ndestroy(), ntranslate()

### Community 104 - "Language Selection Drawer"
Cohesion: 0.50
Nodes (8): Context, Modifier, LanguageSelectionDrawer(), LanguageSelectionDrawerPreview(), LanguageSelectionListItem(), LanguageSelectionNoItems(), LanguageSelectionSourceLanguage(), LanguageSelectionTargetLanguage()

### Community 105 - "OCR Deletion Dialog"
Cohesion: 0.36
Nodes (7): OcrDeletionConfirmationDialog(), Details(), PaddingValues, Languages(), ObjectCharacterRecognitionDetails(), ObjectCharacterRecognitionDetailsPreview(), OcrDetailsData()

### Community 106 - "Modal Bottom Sheet"
Cohesion: 0.31
Nodes (8): Color, Composable, Dp, Modifier, Shape, ScaffoldModalBottomSheet(), Scrim(), BottomSheetScaffoldState

### Community 107 - "Jetpack Compose UI Reference"
Cohesion: 0.50
Nodes (8): Jetpack Compose UI Reference, collectAsState Pattern, DetailsScreen Composable, HomeScreen Composable, ItemCard Composable, MainScreen Composable, NavHost Navigation Pattern, ProfileScreen Composable

### Community 108 - "Code Simplification Skill"
Cohesion: 0.25
Nodes (8): Code Simplification Skill, Chesterton's Fence Principle, Clarity Over Cleverness Principle, Claude Code Simplifier Plugin, Maintain Balance Principle, Preserve Behavior Exactly Principle, Rule of 500, Scope to What Changed

### Community 109 - "Trial License Drawer"
Cohesion: 0.32
Nodes (6): AnnotatedString, Modifier, TrialLicenseDrawer(), TrialLicenseDrawerPreview(), annotateSentence(), SpanStyle

### Community 111 - "Translate Bubble Shortcut"
Cohesion: 0.25
Nodes (5): ComponentActivity, Context, TranslateBubbleShortcut, ShortcutInfo, ShortcutManager

### Community 112 - "External Data Definitions"
Cohesion: 0.25
Nodes (3): ExternalDataDefinitions, Flow, ExternalData

### Community 113 - "TTS Inference"
Cohesion: 0.25
Nodes (3): FloatArray, LongArray, TextToSpeechInference

### Community 114 - "TTS Mock Inference"
Cohesion: 0.25
Nodes (3): FloatArray, LongArray, TextToSpeechMockInference

### Community 115 - "Language Selector"
Cohesion: 0.39
Nodes (7): Context, CornerBasedShape, Modifier, PaddingValues, LanguageSelector(), LanguageSelectorButton(), LanguageSelectorPreview()

### Community 116 - "Minimal Language Selector"
Cohesion: 0.39
Nodes (7): Context, CornerBasedShape, Modifier, PaddingValues, MinimalLanguageSelector(), MinimalLanguageSelectorButton(), MinimalLanguageSelectorPreview()

### Community 117 - "Slider Predefined Values"
Cohesion: 0.32
Nodes (6): Modifier, T, SliderPredefinedValues(), PaddingValues, TranslationSettings(), TranslationSettingsPreview()

### Community 118 - "Language Details Screen"
Cohesion: 0.46
Nodes (7): Details(), ImageVector, PaddingValues, LanguageDetails(), LanguageDetailsData(), LanguageDetailsPreview(), Metadata()

### Community 119 - "Legacy TextTranslation UI"
Cohesion: 0.54
Nodes (7): Modifier, TextTranslationInputButtonRow(), TextTranslationInputField(), TextTranslationLegacy(), TextTranslationLegacyPreview(), TextTranslationOutput(), TextTranslationOutputButtonRow()

### Community 120 - "Button Color Defaults"
Cohesion: 0.36
Nodes (3): ButtonDefaults, FilledIconButtonDefaults, Color

### Community 121 - "C++ Coding Standards Skill"
Cohesion: 0.29
Nodes (7): C++ Coding Standards Skill, Class Naming PascalCase, C++ File Naming Rules, Header Guards / Pragma Once, Namespace Naming Rules, Smart Pointer Rules, C++ Variable Naming Conventions

### Community 122 - "Language Detect Bridge"
Cohesion: 0.33
Nodes (3): AutoCloseable, LanguageDetect, LanguageDetectResult

### Community 124 - "File Visitor Utils"
Cohesion: 0.38
Nodes (5): BasicFileAttributes, FileVisitResult, SimpleFileVisitor, size(), SimpleFileVisitor

### Community 127 - "Verification Skill"
Cohesion: 0.40
Nodes (6): Verify the Verification Step, Verification Before Completion Skill, Evidence Before Claims Principle, The Gate Function, The Iron Law, Rationalization Prevention

### Community 128 - "Vocabulary JNI"
Cohesion: 0.33
Nodes (5): JNIEnv, JNIEXPORT, jobject, jstring, Java_app_versta_translate_bridge_tokenize_Vocabulary_load()

### Community 130 - "File Walk Utils"
Cohesion: 0.40
Nodes (4): BasicFileAttributes, FileVisitResult, SimpleFileVisitor, SimpleFileVisitor

### Community 131 - "File Walk Utils"
Cohesion: 0.40
Nodes (4): BasicFileAttributes, FileVisitResult, SimpleFileVisitor, SimpleFileVisitor

### Community 132 - "Privacy Policy Screen"
Cohesion: 0.67
Nodes (5): PaddingValues, PrivacyPolicy(), PrivacyPolicyParagraph, PrivacyPolicyPreview(), PrivacyPolicyTextParagraph()

### Community 133 - "Tensor Utils JNI"
Cohesion: 0.40
Nodes (4): JNIEnv, JNIEXPORT, jobject, Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer()

### Community 134 - "Notification Activity"
Cohesion: 0.40
Nodes (3): Activity, Bundle, TranslateNotificationActivity

### Community 136 - "Font Weight"
Cohesion: 0.50
Nodes (4): FontWeight, BOLD, REGULAR, fromInt()

### Community 137 - "TTS Settings Screen"
Cohesion: 0.80
Nodes (4): PaddingValues, TextToSpeechDataDownloadProgress(), TextToSpeechSettings(), TextToSpeechSettingsPreview()

### Community 138 - "Theme Animations & Spacing"
Cohesion: 0.40
Nodes (3): Easing, Spacing, TranslateTheme()

### Community 139 - "ViewModel Factory Utils"
Cohesion: 0.50
Nodes (3): T, viewModelFactory(), ViewModelProvider

### Community 140 - "Gradle Wrapper Script"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

## Ambiguous Edges - Review These
- `whisper.cpp` → `Top-level CMakeLists`  [AMBIGUOUS]
  app/native/jni/CMakeLists.txt · relation: conceptually_related_to

## Knowledge Gaps
- **169 isolated node(s):** `language`, `isReliable`, `confidence`, `hints`, `languages` (+164 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **18 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `whisper.cpp` and `Top-level CMakeLists`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `ApplicationModuleInterface` connect `External Data & TTS Tokenizer` to `Voice Model File Repository`, `PaddleOCR Tokenizer`, `Camera Translation Flow`, `TTS Preference DataStore`, `OCR Inference`, `TextToSpeech ViewModel`, `Translation ViewModel State`, `External OCR Model Repository`, `Language ViewModel`, `External Language Model Repository`, `ESpeak NG Bridge`, `Scaffold Navigation`, `TextTranslation ViewModel`, `SQLDelight Database Container`, `Navigation ViewModel`, `Data Database Repository`, `OCR ViewModel`, `External OCR Model Metadata`, `Translation Preference Repository`, `Compressed File Extractor`, `Custom Theme ViewModel`, `Logging ViewModel`, `Language Preference Repository`, `Language Repository`, `License DataStore Repository`, `Voice Repository`, `Leanmt Service`, `File Hash Validator`, `TTS Inference`?**
  _High betweenness centrality (0.125) - this node is a cross-community bridge._
- **Why does `ApplicationModule` connect `External Data & TTS Tokenizer` to `Voice Model File Repository`, `PaddleOCR Tokenizer`, `Camera Translation Flow`, `TTS Preference DataStore`, `OCR Inference`, `TextToSpeech ViewModel`, `Translation ViewModel State`, `External OCR Model Repository`, `Language ViewModel`, `External Language Model Repository`, `ESpeak NG Bridge`, `Scaffold Navigation`, `TextTranslation ViewModel`, `Navigation ViewModel`, `Data Database Repository`, `OCR ViewModel`, `External OCR Model Metadata`, `Translation Preference Repository`, `Compressed File Extractor`, `Custom Theme ViewModel`, `Logging ViewModel`, `Language Preference Repository`, `Language Repository`, `License DataStore Repository`, `Voice Repository`, `Main Application Setup`, `Leanmt Service`, `File Hash Validator`, `TTS Inference`?**
  _High betweenness centrality (0.121) - this node is a cross-community bridge._
- **Why does `ObjectCharacterRecognitionInference` connect `OCR Inference` to `External Data & TTS Tokenizer`?**
  _High betweenness centrality (0.066) - this node is a cross-community bridge._
- **Are the 4 inferred relationships involving `LanguageViewModel` (e.g. with `ErrorAlertDialogPreview()` and `ModelLoadingProgressDialogPreview()`) actually correct?**
  _`LanguageViewModel` has 4 INFERRED edges - model-reasoned connections that need verification._
- **What connects `language`, `isReliable`, `confidence` to the rest of the system?**
  _169 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Paddle OCR JNI Bridge` be split into smaller, more focused modules?**
  _Cohesion score 0.06107594936708861 - nodes in this community are weakly interconnected._