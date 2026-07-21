# Graph Report - app  (2026-08-11)

## Corpus Check
- 281 files · ~111,926 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2568 nodes · 6195 edges · 141 communities (111 shown, 30 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 80 edges (avg confidence: 0.81)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- JNI Primitive Types
- Mesh Gradient Tests
- Language Memory Repos & ViewModel
- Mock Audio & TTS Infrastructure
- Whisper Native Engine
- LeanMT Native JNI
- StyleTTS2 Inference
- OCR Text Analyzer Native
- Scaffold Navigation Components
- Language Preference DataStore
- License Management
- Data Repos & Files
- Language Repository Port
- Divider & Badge UI
- Camera Translation
- Translation Inference Loading
- Whisper Speech Tests
- OCR Inference Bridge
- Language Database Repository
- External Voice Model Repos
- External Language Model Catalog
- Language Download & Database
- Data Repos & Hash Validation
- Vendored Native Libraries
- TTS Voice Loading
- App Navigation
- TTS ViewModel
- Translation ViewModel
- Download Status State
- OCR Post-Processing Pipeline
- TTS Preference DataStore
- Architecture Concepts
- Fake Recognizer Test Helper
- Speech Recognition External Repos
- Voice Models UI
- Download Worker Infrastructure
- Data Database Repository
- OCR Download Worker & Metadata
- Navigation Screens
- Speech Download & Database
- Speech Model Definition Tests
- OCR External Model Repos
- StyleTTS2 Tokenizer & ESpeak
- PaddleOCR Recognize ONNX
- Text Translation ViewModel
- Settings UI Components
- OCR Repository Memory
- HTTP Download Client
- TTS Synthesis State
- Voice Download Worker
- Speech Recognition ViewModel
- Settings & Download UI
- CLD2 Language Detection Native
- Speech Repo & Model Tests
- OCR Repository & Download
- Transliteration
- Database Container & Migrations
- Application Logs UI
- OCR Repository Database
- PaddleOCR Detect ONNX
- Theme & Color Scheme
- Speech Context Store Tests
- ESpeakNG Native
- Translation Preference DataStore
- Translation Preference Memory
- Translation Preference Port
- Download Manager
- Whisper Speech Kotlin
- Language Detection & Logging
- Archive Extraction
- Voice Database Repository
- OCR ViewModel & Tasks
- Whisper Native JNI Handles
- OCR Text Analyzer Kotlin
- Bubble Activity
- Microphone Capture
- Whisper VAD Probe
- Custom Theme & Nav
- Whisper Recognizer Kotlin
- Speech Recognition Files Tests
- File Saver
- PaddleOCR Kotlin Bridge
- Logging ViewModel
- Data Download Worker & Metadata
- Language Preference Port
- Speech Recognition Mock Inference
- Main Activity
- Swipe & Color UI
- File Picker
- OCR Camera Analyzer
- Speech Repo Tests
- Whisper Initial Prompts Tests
- Speech Recognition Metadata Tests
- Notifications
- Settings Headers & Speech UI
- Initial Prompts Tests
- License Repository
- StyleTTS2 Tokenizer
- Compose Design Concepts
- Bottom Sheet Scaffold
- Whisper Native Model Handle
- Camera Permission UI
- OCR Tokenizer & Vocabulary
- Speech Recognition Inference Port
- LeanMT Kotlin Bridge
- Language Pair Badge UI
- Translate Bubble Shortcut
- External Voice File Repos
- TextToSpeech Inference Port
- Whisper Native JNI Create
- Audio Player
- AudioTrack Player
- Speech Context Store
- File System Utilities
- Vocabulary Native
- Whisper VAD Trailing Probe
- External Language Model Tests
- Whisper Model Kotlin
- Tensor Utils Native
- Translate Notification Activity
- Moses Punctuation Normalizer
- ViewModel Extensions
- Writing Direction
- Versta Logo
- Text Annotation
- Fake Model Test Helper
- TTS Playback
- TTS Synthesis
- Content Color
- Logarithmic Slider
- Driving Adapters
- Native Header & Binding
- Opaque Handle Pattern
- Port Implementation Naming
- Timber Logging
- Utils Extensions
- Threading Contract
- Whisper Canonical Kotlin Shape

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
  src/main/java/app/versta/translate/adapter/inbound/DownloadWorker.kt → src/main/java/app/versta/translate/adapter/inbound/HttpDownloadClient.kt
- `WhisperSpeechRecognition` --calls--> `SpeechContextStore`  [INFERRED]
  src/main/java/app/versta/translate/adapter/outbound/WhisperSpeechRecognition.kt → src/main/java/app/versta/translate/adapter/outbound/SpeechContextStore.kt
- `LanguageModelPair` --calls--> `LanguagePair`  [INFERRED]
  src/main/java/app/versta/translate/core/entity/LanguageWithModelFiles.kt → src/main/java/app/versta/translate/core/entity/Language.kt
- `LanguageViewModel` --calls--> `DownloadManager`  [INFERRED]
  src/main/java/app/versta/translate/core/model/LanguageViewModel.kt → src/main/java/app/versta/translate/core/model/DownloadManager.kt
- `ObjectCharacterRecognitionViewModel` --calls--> `DownloadManager`  [INFERRED]
  src/main/java/app/versta/translate/core/model/ObjectCharacterRecognitionViewModel.kt → src/main/java/app/versta/translate/core/model/DownloadManager.kt

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Vendored Libraries Linked Into the Native Bridge** — app_native_jni_cmakelists_link_opencv, app_native_jni_cmakelists_link_ucd, app_native_jni_cmakelists_link_cld2, app_native_jni_cmakelists_link_ocr_clipper, app_native_jni_cmakelists_link_espeak_ng, app_native_jni_cmakelists_link_openjtalk, app_native_jni_cmakelists_link_leanmt, app_native_jni_cmakelists_link_whisper [EXTRACTED 1.00]
- **JNI Binding Files of app_versta_translate_bridge** — app_native_jni_src_cmakelists_tensor_utils, app_native_jni_src_cmakelists_espeak_ng, app_native_jni_src_cmakelists_open_jtalk, app_native_jni_src_cmakelists_paddle_ocr, app_native_jni_src_cmakelists_ocr_text_analyzer, app_native_jni_src_cmakelists_language_detect, app_native_jni_src_cmakelists_vocabulary, app_native_jni_src_cmakelists_leanmt, app_native_jni_src_cmakelists_whisper [EXTRACTED 1.00]
- **Native Inference Engines (JNI Bridge)** — app_agents_bridge, app_agents_leanmt, app_agents_whispercpp, app_agents_cld2, app_agents_espeakng, app_agents_openjtalk, app_agents_paddleocr [EXTRACTED 1.00]
- **Storage Stack (SQLDelight + DataStore + Manual Migrations)** — app_agents_sqldelight, app_agents_datastore, app_agents_databasecontainer, app_agents_migrations [EXTRACTED 1.00]
- **Compose Theming System** — app_agents_material3_expressive, app_agents_translatetheme, app_agents_obsidiantheme, app_agents_meshgradientbackground [EXTRACTED 1.00]

## Communities (141 total, 30 thin omitted)

### Community 0 - "JNI Primitive Types"
Cohesion: 0.06
Nodes (47): ForwardIterator, jlongArray, argmax(), jboolean, jfloat, jint, jintArray, jlong (+39 more)

### Community 1 - "Mesh Gradient Tests"
Cohesion: 0.08
Nodes (37): ButtonColors, Color, MeshGradientBackgroundTest, ButtonCard(), ButtonCardColors, ButtonCardDefaults, ButtonCardPreview(), Color (+29 more)

### Community 2 - "Language Memory Repos & ViewModel"
Cohesion: 0.08
Nodes (31): ExternalLanguageModelsMemoryRepository, LanguageMemoryRepository, LanguagePreferenceMemoryRepository, Job, LanguageType, Source, Target, LanguageViewModel (+23 more)

### Community 3 - "Mock Audio & TTS Infrastructure"
Cohesion: 0.09
Nodes (26): AudioMockPlayer, DataMemoryRepository, ExternalDataMemoryRepository, TextToSpeechMockInference, TextToSpeechMockTokenizer, Flow, TextToSpeechPreferenceMemoryRepository, TranslationMockInference (+18 more)

### Community 4 - "Whisper Native Engine"
Cohesion: 0.04
Nodes (50): jmethodID, Recognizer, abort_count, abort_deadline, callback_method, callback_obj, carried_prompt_ids, commit_compute_ms (+42 more)

### Community 5 - "LeanMT Native JNI"
Cohesion: 0.07
Nodes (42): jobjectArray, JPCommon, Mecab, jclass, jlong, JNIEnv, JNIEXPORT, jobject (+34 more)

### Community 6 - "StyleTTS2 Inference"
Cohesion: 0.08
Nodes (21): ByteBuffer, FloatArray, LongArray, OrtSession, Waveform, StyleTextToSpeechInference, Buffer, TensorUtils (+13 more)

### Community 7 - "OCR Text Analyzer Native"
Cohesion: 0.10
Nodes (28): jfloat, jint, jlong, JNIEnv, JNIEXPORT, jobject, Mat, Scalar (+20 more)

### Community 8 - "Scaffold Navigation Components"
Cohesion: 0.12
Nodes (33): ExternalObjectCharacterRecognitionModelsMemoryRepository, NavKey, ScaffoldComponent, ScaffoldRowScopeComponent, ViewModel, ScaffoldActionsComponent, ScaffoldBottomBarComponent, ScaffoldComponentMetadata (+25 more)

### Community 9 - "Language Preference DataStore"
Cohesion: 0.11
Nodes (13): Flow, LanguagePreferenceDataStoreRepository, Flow, AutoDetectLanguage, LanguageOption, LanguageOptionPair, Context, Modifier (+5 more)

### Community 10 - "License Management"
Cohesion: 0.12
Nodes (24): Flow, LicenseMemoryRepository, DialogState, Closed, Confirm, Open, StateFlow, ViewModel (+16 more)

### Community 11 - "Data Repos & Files"
Cohesion: 0.11
Nodes (13): Flow, ExternalDataFileRepository, ExternalDataDefinitions, Flow, ExternalDataDefinitions, Flow, ExternalDataDefinitions, Flow (+5 more)

### Community 12 - "Language Repository Port"
Cohesion: 0.08
Nodes (7): Flow, LanguageRepository, LanguagePair, PivotPairModelFiles, Flow, StateFlow, ViewModel

### Community 13 - "Divider & Badge UI"
Cohesion: 0.13
Nodes (27): app, Divider(), Dp, ListDivider(), Color, Dp, Modifier, LanguageBadge() (+19 more)

### Community 14 - "Camera Translation"
Cohesion: 0.12
Nodes (11): Camera, ProcessCameraProvider, Size, CameraTranslationResult, CameraTranslationViewModel, Context, Flow, LifecycleOwner (+3 more)

### Community 15 - "Translation Inference Loading"
Cohesion: 0.09
Nodes (9): BergamotTinyInference, Flow, AutoCloseable, LeanmtModel, LeanmtModelConfig, LeanmtPackage, LanguageModel, LanguageModelConfiguration (+1 more)

### Community 17 - "OCR Inference Bridge"
Cohesion: 0.13
Nodes (15): ImageProxy, ObjectCharacterRecognitionInference, AutoCloseable, ByteBuffer, ImageProxy, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognition (+7 more)

### Community 18 - "Language Database Repository"
Cohesion: 0.10
Nodes (7): LanguageModelDatabaseModel, Flow, LanguageDatabaseRepository, LongArray, Context, Language, PivotPair

### Community 19 - "External Voice Model Repos"
Cohesion: 0.12
Nodes (13): ExternalVoiceModelsMemoryRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoiceModelsRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoice, ExternalVoiceDownloadTask (+5 more)

### Community 20 - "External Language Model Catalog"
Cohesion: 0.14
Nodes (13): ExternalLanguageModelDefinitions, ExternalLanguageModelsFileRepository, Flow, Flow, ExternalLanguageModelsRepository, Flow, ExternalLanguageMetadata, ExternalLanguageModelDefinition (+5 more)

### Community 21 - "Language Download & Database"
Cohesion: 0.10
Nodes (11): LanguageDatabaseModel, DownloadLanguageWorker, LanguageBundleData, LanguageBundleMetadata, LanguageMetadata, LanguageModelArchitecture, BergamotTinyModel, MarianMTModel (+3 more)

### Community 22 - "Data Repos & Hash Validation"
Cohesion: 0.12
Nodes (15): Application, OpenJTalk, FileHashValidator, PrecomputedHashFileValidator, ExternalDataRepository, ExternalObjectCharacterRecognitionModelsRepository, SpeechRecognitionRepository, LongArray (+7 more)

### Community 23 - "Vendored Native Libraries"
Cohesion: 0.10
Nodes (28): C++17 Release Build Config, 16 KB ELF Page-Size Link Flag, jstr JNI String Helper, Vendored Git Submodules, whisper.cc Process-Loop Mutex, arm64 SIMD Flags (-march=armv8.2-a+dotprod+fp16), app_versta_translate_bridge Shared Library, cld2 Link Target (+20 more)

### Community 24 - "TTS Voice Loading"
Cohesion: 0.11
Nodes (9): ArrayList, Flow, Flow, VoiceRepository, VoiceModelInferenceFiles, VoiceModelTokenizerFiles, VoiceModelVoiceFiles, VoiceWithModelFiles (+1 more)

### Community 25 - "App Navigation"
Cohesion: 0.14
Nodes (12): NavBackStack, NavKey, ViewModel, NavigationViewModel, Modifier, ModalDrawerItem(), NavigationDrawer(), NavigationDrawerRailItem() (+4 more)

### Community 26 - "TTS ViewModel"
Cohesion: 0.13
Nodes (3): FloatArray, Job, TextToSpeechViewModel

### Community 27 - "Translation ViewModel"
Cohesion: 0.12
Nodes (13): Completed, Error, Idle, InProgress, Flow, Job, StateFlow, ViewModel (+5 more)

### Community 28 - "Download Status State"
Cohesion: 0.10
Nodes (12): Serializable, Cancelled, Completed, DownloadStatus, Error, Idle, Processing, Progress (+4 more)

### Community 29 - "OCR Post-Processing Pipeline"
Cohesion: 0.14
Nodes (7): OcrPostProcessor, OcrPostProcessorContext, OcrPostProcessorPipeline, Color, ParagraphGroupingPostProcessor, TextStyleAnalysisPostProcessor, ObjectCharacterRecogniserResult

### Community 30 - "TTS Preference DataStore"
Cohesion: 0.11
Nodes (7): Flow, TextToSpeechPreferenceDataStoreRepository, Flow, TextToSpeechPreferenceRepository, VoiceGender, Female, Male

### Community 31 - "Architecture Concepts"
Cohesion: 0.09
Nodes (26): adapter/outbound — Driven Adapters (Ports), androidTest Source Set, bridge — Kotlin JNI Wrappers, cld2 Language Detection, core/entity — Pure Domain Models, core/model — ViewModels, database — SQLDelight + Migrations, DatabaseContainer (+18 more)

### Community 32 - "Fake Recognizer Test Helper"
Cohesion: 0.09
Nodes (9): FakeRecognizer, FloatArray, IntArray, WhisperSegmentCallback, AutoCloseable, FloatArray, IntArray, WhisperRecognizerHandle (+1 more)

### Community 33 - "Speech Recognition External Repos"
Cohesion: 0.17
Nodes (9): ExternalSpeechRecognitionModelsFileRepository, Flow, ExternalSpeechRecognitionModelsMemoryRepository, Flow, ExternalSpeechRecognitionModelsRepository, Flow, ExternalSpeechRecognitionModelDefinition, ExternalSpeechRecognitionModels (+1 more)

### Community 34 - "Voice Models UI"
Cohesion: 0.17
Nodes (18): ExternalVoiceLanguageVoiceGenders, VoiceViewModel, VoiceDeletionConfirmationDialog(), VoiceDeletionConfirmationDialogPreview(), PaddingValues, VoiceAttributions(), VoiceAttributionsPreview(), Details() (+10 more)

### Community 35 - "Download Worker Infrastructure"
Cohesion: 0.15
Nodes (10): CoroutineWorker, ForegroundInfo, Result, DownloadExternalDataWorker, DownloadSpeechRecognitionWorker, DownloadQueue, DownloadWorker, DownloadListener (+2 more)

### Community 36 - "Data Database Repository"
Cohesion: 0.13
Nodes (11): DataDatabaseModel, DataDatabaseRepository, DataRepository, Flow, DataModel, DataType, TTS, Flow (+3 more)

### Community 37 - "OCR Download Worker & Metadata"
Cohesion: 0.11
Nodes (12): ObjectCharacterRecognitionBundleMetadata, ObjectCharacterRecognitionDetectorFilesMetadata, ObjectCharacterRecognitionDetectorInferenceFilesMetadata, ObjectCharacterRecognitionDetectorMetadata, ObjectCharacterRecognitionMetadataFile, ObjectCharacterRecognitionModule, Detector, Recognizer (+4 more)

### Community 38 - "Navigation Screens"
Cohesion: 0.08
Nodes (24): About, ApplicationLogs, NavKey, LanguageAttributions, LanguageDetails, LanguageSettings, ObjectCharacterRecognitionAttributions, ObjectCharacterRecognitionDetails (+16 more)

### Community 39 - "Speech Download & Database"
Cohesion: 0.13
Nodes (9): SpeechRecognitionDatabaseModel, SpeechRecognitionDatabaseRepository, SpeechRecognitionBundleMetadata, SpeechRecognitionFilesMetadata, SpeechRecognitionInferenceFilesMetadata, SpeechRecognitionMetadata, SpeechRecognitionModel, SpeechRecognitionModule (+1 more)

### Community 41 - "OCR External Model Repos"
Cohesion: 0.19
Nodes (8): ExternalObjectCharacterRecognitionModelsFileRepository, Flow, Flow, Flow, ExternalObjectCharacterRecognitionModelDefinition, ExternalObjectCharacterRecognitionModels, ExternalObjectCharacterRecognitionModelWithState, ObjectCharacterRecognitionDetectorWithFiles

### Community 42 - "StyleTTS2 Tokenizer & ESpeak"
Cohesion: 0.13
Nodes (6): ESpeakNG, AutoCloseable, ByteArray, Flow, MutableStateFlow, SynthReadyCallback

### Community 43 - "PaddleOCR Recognize ONNX"
Cohesion: 0.15
Nodes (8): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, TextRegionMetrics, PaddleObjectCharacterRecognitionRecognizeInput, PaddleObjectCharacterRecognitionRecognizeOutput

### Community 44 - "Text Translation ViewModel"
Cohesion: 0.15
Nodes (8): Context, Flow, ViewModel, TextTranslationViewModel, DictationButtonContent(), FloatingTextTranslationInputBar(), PaddingValues, TextTranslation()

### Community 45 - "Settings UI Components"
Cohesion: 0.18
Nodes (15): Color, Composable, Dp, ListItemColors, Modifier, PaddingValues, SettingsButtonItem(), SettingsButtonItemContent() (+7 more)

### Community 46 - "OCR Repository Memory"
Cohesion: 0.13
Nodes (7): Flow, ObjectCharacterRecognitionRepositoryMemoryRepository, ObjectCharacterRecognitionArchitecture, PaddleOCR, ObjectCharacterRecognitionDetectorInferenceFiles, ObjectCharacterRecognitionRecognizerInferenceFiles, ObjectCharacterRecognitionRecognizerTokenizerFiles

### Community 47 - "HTTP Download Client"
Cohesion: 0.14
Nodes (9): Call, Response, DownloadClient, DownloadListener, URI, HttpDownloadClient, Callback, DownloadListener (+1 more)

### Community 48 - "TTS Synthesis State"
Cohesion: 0.21
Nodes (17): SpanStyle, Idle, Preparing, Synthesizing, TextToSpeechSynthesisState, TextToSpeechButton(), Modifier, MinimalTextTranslation() (+9 more)

### Community 49 - "Voice Download Worker"
Cohesion: 0.11
Nodes (10): DownloadVoiceWorker, VoiceBundleMetadata, VoiceInferenceFilesMetadata, VoiceMetadataFile, VoiceModel, VoiceModelArchitecture, Kokoro, StyleTTS2 (+2 more)

### Community 50 - "Speech Recognition ViewModel"
Cohesion: 0.14
Nodes (10): CoroutineScope, Flow, StateFlow, ViewModel, LoadRequest, SpeechRecognitionViewModel, StartResult, MicrophoneUnavailable (+2 more)

### Community 51 - "Settings & Download UI"
Cohesion: 0.20
Nodes (16): DownloadButton(), Modifier, LanguageDownloadButtonPreview(), LanguageDeletionConfirmationDialog(), LanguageDeletionConfirmationDialogPreview(), Details(), ImageVector, PaddingValues (+8 more)

### Community 52 - "CLD2 Language Detection Native"
Cohesion: 0.15
Nodes (18): CLDHints, jlong, JNIEnv, JNIEXPORT, jobject, jstring, DetectionResult, confidence (+10 more)

### Community 53 - "Speech Repo & Model Tests"
Cohesion: 0.16
Nodes (7): Flow, SpeechRecognitionMemoryRepository, Flow, SpeechRecognitionArchitecture, Whisper, SpeechRecognitionInferenceFiles, SpeechRecognitionWithFiles

### Community 54 - "OCR Repository & Download"
Cohesion: 0.13
Nodes (5): DownloadObjectCharacterRecognitionWorker, Flow, ObjectCharacterRecognitionRepository, ObjectCharacterRecognitionDetectorModel, ObjectCharacterRecognitionRecognizerModel

### Community 55 - "Transliteration"
Cohesion: 0.15
Nodes (6): GenericTransliterator, JapaneseTransliterator, Transliteration, TransliterationAdapter, Token, Transliterator

### Community 56 - "Database Container & Migrations"
Cohesion: 0.19
Nodes (6): DatabaseContainer, Migration, Migration3, Context, Migration4, Migration6

### Community 57 - "Application Logs UI"
Cohesion: 0.21
Nodes (16): ScaffoldCompactBarBackNavigationIcon(), ScaffoldCompactBarTitle(), ApplicationLogs(), FileSaverCallback, ApplicationLogsPreview(), FileSaverCallback, PaddingValues, Uri (+8 more)

### Community 58 - "OCR Repository Database"
Cohesion: 0.19
Nodes (4): ObjectCharacterRecognitionDetectorDatabaseModel, ObjectCharacterRecognitionRecognizerDatabaseModel, ObjectCharacterRecognitionRepositoryDatabaseRepository, ObjectCharacterRecognitionRecognizerWithFiles

### Community 59 - "PaddleOCR Detect ONNX"
Cohesion: 0.19
Nodes (7): Buffer, ByteBuffer, OnnxTensor, OnnxTensorLike, OrtSession, PaddleObjectCharacterRecognitionDetectInput, PaddleObjectCharacterRecognitionDetectOutput

### Community 60 - "Theme & Color Scheme"
Cohesion: 0.25
Nodes (17): ColorScheme, FiniteAnimationSpec, animateColorScheme(), AnimatedBackgroundColors, AnimatedColors, animatedPrimaryColors(), AnimatedSurfaceColors, defaultColorTransitionSpec() (+9 more)

### Community 62 - "ESpeakNG Native"
Cohesion: 0.26
Nodes (16): espeak_EVENT, JavaVM, jclass, JNIEnv, JNIEXPORT, jobject, jstring, getJniEnv() (+8 more)

### Community 66 - "Download Manager"
Cohesion: 0.18
Nodes (4): DownloadManager, Context, T, WorkRequest

### Community 67 - "Whisper Speech Kotlin"
Cohesion: 0.17
Nodes (8): mutex, MicrophoneCaptureException, AutoCloseable, CoroutineScope, Job, WhisperSegmentCallback, WhisperSpeechRecognition, WhisperSegmentCallback

### Community 68 - "Language Detection & Logging"
Cohesion: 0.16
Nodes (7): SimpleDateFormat, AutoCloseable, LanguageDetect, LanguageDetectResult, FileLoggingTree, LocaleUtils, Timber

### Community 69 - "Archive Extraction"
Cohesion: 0.26
Nodes (5): CompressedFileExtractor, ExtractionProgressListener, Uri, Uri, TarballExtractor

### Community 70 - "Voice Database Repository"
Cohesion: 0.23
Nodes (4): VoiceDatabaseRepository, VoiceModelMetadata, VoiceDatabaseModel, VoiceModelDatabaseModel

### Community 71 - "OCR ViewModel & Tasks"
Cohesion: 0.17
Nodes (5): ExternalObjectCharacterRecognitionDownloadTask, Flow, StateFlow, ViewModel, ObjectCharacterRecognitionViewModel

### Community 72 - "Whisper Native JNI Handles"
Cohesion: 0.39
Nodes (15): jfloatArray, jintArray, jlong, JNIEnv, JNIEXPORT, jobject, findRecognizer(), Java_app_versta_translate_bridge_whisper_WhisperModel_destroy() (+7 more)

### Community 73 - "OCR Text Analyzer Kotlin"
Cohesion: 0.21
Nodes (8): AutoCloseable, Buffer, ByteBuffer, ImageProxy, IntArray, OcrTextAnalyzer, OcrTextMetrics, ObjectCharacterRecogniserColors

### Community 74 - "Bubble Activity"
Cohesion: 0.22
Nodes (8): BubbleActivity, Activity, Bundle, ComponentActivity, Intent, Easing, Spacing, TranslateTheme()

### Community 75 - "Microphone Capture"
Cohesion: 0.20
Nodes (6): AudioRecord, CaptureHandle, CoroutineScope, CoroutineScope, Job, MicrophoneCapture

### Community 76 - "Whisper VAD Probe"
Cohesion: 0.22
Nodes (12): deque, advance_front(), vector, Java_app_versta_translate_bridge_whisper_WhisperRecognizer_flush(), probe_and_update(), probe_speech(), ProbeUpdate, pause_mid_ms (+4 more)

### Community 77 - "Custom Theme & Nav"
Cohesion: 0.24
Nodes (10): NavEntryDecorator, CustomThemeViewModel, ViewModel, CustomTheme, Obsidian, CustomThemeScene, NavKey, ObsidianThemeMetadata (+2 more)

### Community 78 - "Whisper Recognizer Kotlin"
Cohesion: 0.18
Nodes (5): RuntimeException, AutoCloseable, FloatArray, WhisperSegmentCallback, WhisperRecognizer

### Community 80 - "File Saver"
Cohesion: 0.19
Nodes (8): FileSaver, FileSaverCallback, FileSaverCallback, Uri, ActivityResultLauncher, ComponentActivity, FileSaverCallback, LogFileSaver

### Community 81 - "PaddleOCR Kotlin Bridge"
Cohesion: 0.22
Nodes (7): AutoCloseable, Buffer, ByteBuffer, IntArray, LongArray, PaddleOCR, TextRegionMetrics

### Community 82 - "Logging ViewModel"
Cohesion: 0.25
Nodes (6): Context, StateFlow, Uri, ViewModel, LoggingViewModel, FileObserver

### Community 83 - "Data Download Worker & Metadata"
Cohesion: 0.21
Nodes (6): DataBundleMetadata, DataMetadata, DataMetadataInterface, TextToSpeechDataFilesMetadata, TextToSpeechDataMetadata, TextToSpeechDataMetadataFile

### Community 85 - "Speech Recognition Mock Inference"
Cohesion: 0.19
Nodes (5): CoroutineScope, Flow, SpeechRecognitionMockInference, IntArray, SpeechRecognitionSegment

### Community 86 - "Main Activity"
Cohesion: 0.27
Nodes (7): Bundle, ComponentActivity, Intent, MainActivity, Modifier, TrialLicenseDrawer(), setEdgeToEdgeConfig()

### Community 87 - "Swipe & Color UI"
Cohesion: 0.22
Nodes (6): DeleteBackground(), Modifier, SwipeDelete(), ButtonDefaults, Color, SwipeToDismissBoxState

### Community 88 - "File Picker"
Cohesion: 0.26
Nodes (6): FilePicker, FilePickerCallback, Uri, ActivityResultLauncher, ComponentActivity, ModelFilePicker

### Community 89 - "OCR Camera Analyzer"
Cohesion: 0.22
Nodes (6): ImageAnalysis, ImageProxy, ObjectCharacterRecognitionAnalyzer, FontWeight, BOLD, REGULAR

### Community 91 - "Whisper Initial Prompts Tests"
Cohesion: 0.18
Nodes (5): CoroutineScope, IntArray, WhisperSegmentCallback, SpeechRecognitionInitialPrompts, TestCoroutineScheduler

### Community 93 - "Notifications"
Cohesion: 0.29
Nodes (4): Context, TranslateBubbleNotification, Context, TranslateNotification

### Community 94 - "Settings Headers & Speech UI"
Cohesion: 0.35
Nodes (9): Dp, ListItemColors, Modifier, SettingsHeaderItem(), SettingsHeaderItemPreview(), PaddingValues, PreviewSpeechRecognitionSettings(), SpeechRecognitionModels() (+1 more)

### Community 96 - "License Repository"
Cohesion: 0.24
Nodes (4): Flow, LicenseDataStoreRepository, Flow, LicenseRepository

### Community 98 - "Compose Design Concepts"
Cohesion: 0.25
Nodes (9): Jetpack Compose, /DESIGN.md (Gradient Mesh Spec), Material 3 Expressive, MeshGradientBackground, Navigation3 ListDetailSceneStrategy, ObsidianTheme Color Scheme, Router.kt, TranslateTheme (+1 more)

### Community 99 - "Bottom Sheet Scaffold"
Cohesion: 0.47
Nodes (8): BottomSheetScaffoldState, Color, Composable, Dp, Modifier, Shape, ScaffoldModalBottomSheet(), Scrim()

### Community 100 - "Whisper Native Model Handle"
Cohesion: 0.31
Nodes (7): findModel(), WhisperModelHandle, ctx, n_threads, vctx, whisper_context, whisper_vad_context

### Community 101 - "Camera Permission UI"
Cohesion: 0.42
Nodes (8): PermissionState, CameraPermissionDenied(), CameraTranslation(), CameraViewFinder(), LifecycleOwner, Modifier, PaddingValues, ZoomSelector()

### Community 102 - "OCR Tokenizer & Vocabulary"
Cohesion: 0.25
Nodes (3): LongArray, PaddleObjectCharacterRecognitionTokenizer, Vocabulary

### Community 103 - "Speech Recognition Inference Port"
Cohesion: 0.28
Nodes (3): CoroutineScope, Flow, SpeechRecognitionInference

### Community 105 - "Language Pair Badge UI"
Cohesion: 0.44
Nodes (7): Color, ImageVector, Modifier, LanguagePairBadge(), LanguagePairBadgeColors, LanguagePairBadgeDefaults, LanguagePairBadgePreview()

### Community 106 - "Translate Bubble Shortcut"
Cohesion: 0.39
Nodes (5): ShortcutInfo, ShortcutManager, ComponentActivity, Context, TranslateBubbleShortcut

### Community 107 - "External Voice File Repos"
Cohesion: 0.43
Nodes (3): ExternalVoiceModelsFileRepository, ExternalVoiceModelDefinitions, Flow

### Community 108 - "TextToSpeech Inference Port"
Cohesion: 0.25
Nodes (3): FloatArray, LongArray, TextToSpeechInference

### Community 109 - "Whisper Native JNI Create"
Cohesion: 0.43
Nodes (7): jboolean, jfloat, jint, jstring, Java_app_versta_translate_bridge_whisper_WhisperModel_create(), Java_app_versta_translate_bridge_whisper_WhisperRecognizer_create(), jstr()

### Community 110 - "Audio Player"
Cohesion: 0.29
Nodes (3): AudioPlayer, ByteArray, FloatArray

### Community 111 - "AudioTrack Player"
Cohesion: 0.29
Nodes (3): AudioTrackPlayer, ByteArray, FloatArray

### Community 112 - "Speech Context Store"
Cohesion: 0.43
Nodes (3): Entry, IntArray, SpeechContextStore

### Community 113 - "File System Utilities"
Cohesion: 0.60
Nodes (3): BasicFileAttributes, FileVisitResult, SimpleFileVisitor

### Community 114 - "Vocabulary Native"
Cohesion: 0.33
Nodes (5): JNIEnv, JNIEXPORT, jobject, jstring, Java_app_versta_translate_bridge_tokenize_Vocabulary_load()

### Community 115 - "Whisper VAD Trailing Probe"
Cohesion: 0.33
Nodes (6): TrailingProbe, first_speech_start_ms, last_pause_mid_ms, last_speech_end_ms, last_voiced_end_ms, valid

### Community 118 - "Tensor Utils Native"
Cohesion: 0.40
Nodes (4): JNIEnv, JNIEXPORT, jobject, Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer()

### Community 119 - "Translate Notification Activity"
Cohesion: 0.60
Nodes (3): Activity, Bundle, TranslateNotificationActivity

### Community 121 - "ViewModel Extensions"
Cohesion: 0.60
Nodes (3): T, viewModelFactory(), ViewModelProvider

### Community 122 - "Writing Direction"
Cohesion: 0.50
Nodes (3): WritingDirection, LTR, RTL

### Community 123 - "Versta Logo"
Cohesion: 0.83
Nodes (3): Dp, Modifier, VerstaLogo()

## Ambiguous Edges - Review These
- `jstr JNI String Helper` → `language_detect.cc JNI Binding`  [AMBIGUOUS]
  native/AGENTS.md · relation: conceptually_related_to
- `tensor_utils.cc JNI Binding` → `vocabulary.cc JNI Binding`  [AMBIGUOUS]
  native/jni/src/CMakeLists.txt · relation: shares_data_with

## Knowledge Gaps
- **207 isolated node(s):** `language`, `isReliable`, `confidence`, `hints`, `languages` (+202 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **30 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `jstr JNI String Helper` and `language_detect.cc JNI Binding`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `tensor_utils.cc JNI Binding` and `vocabulary.cc JNI Binding`?**
  _Edge tagged AMBIGUOUS (relation: shares_data_with) - confidence is low._
- **Why does `string` connect `LeanMT Native JNI` to `JNI Primitive Types`, `Whisper Native Engine`, `Whisper VAD Probe`, `Whisper Native JNI Create`?**
  _High betweenness centrality (0.123) - this node is a cross-community bridge._
- **Why does `ApplicationModule` connect `Data Repos & Hash Validation` to `Language Memory Repos & ViewModel`, `StyleTTS2 Inference`, `Scaffold Navigation Components`, `Language Preference DataStore`, `Data Repos & Files`, `Language Repository Port`, `Camera Translation`, `Translation Inference Loading`, `OCR Inference Bridge`, `Language Database Repository`, `External Voice Model Repos`, `External Language Model Catalog`, `TTS Voice Loading`, `App Navigation`, `TTS ViewModel`, `Translation ViewModel`, `OCR Post-Processing Pipeline`, `TTS Preference DataStore`, `Speech Recognition External Repos`, `Voice Models UI`, `Data Database Repository`, `Speech Download & Database`, `OCR External Model Repos`, `StyleTTS2 Tokenizer & ESpeak`, `Text Translation ViewModel`, `Speech Recognition ViewModel`, `OCR Repository & Download`, `Database Container & Migrations`, `OCR Repository Database`, `Translation Preference DataStore`, `Translation Preference Port`, `Whisper Speech Kotlin`, `Archive Extraction`, `Voice Database Repository`, `OCR ViewModel & Tasks`, `Custom Theme & Nav`, `Logging ViewModel`, `Language Preference Port`, `License Repository`, `StyleTTS2 Tokenizer`, `OCR Tokenizer & Vocabulary`, `Speech Recognition Inference Port`, `LeanMT Kotlin Bridge`, `External Voice File Repos`, `TextToSpeech Inference Port`, `AudioTrack Player`?**
  _High betweenness centrality (0.107) - this node is a cross-community bridge._
- **Why does `Recognizer` connect `Whisper Native Engine` to `Whisper Speech Kotlin`, `Whisper Native Model Handle`, `LeanMT Native JNI`, `Whisper Native JNI Handles`, `Whisper VAD Probe`?**
  _High betweenness centrality (0.106) - this node is a cross-community bridge._
- **What connects `language`, `isReliable`, `confidence` to the rest of the system?**
  _207 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `JNI Primitive Types` be split into smaller, more focused modules?**
  _Cohesion score 0.06107594936708861 - nodes in this community are weakly interconnected._