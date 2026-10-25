# Graph Report - Versta.Android  (2026-09-05)

## Corpus Check
- 319 files · ~194,545 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3566 nodes · 7970 edges · 184 communities (159 shown, 25 thin omitted)
- Extraction: 98% EXTRACTED · 2% INFERRED · 0% AMBIGUOUS · INFERRED: 195 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `80f42cd0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- TextLine
- Theme.kt
- LanguageMemoryRepository
- StyleTextToSpeechInference
- Recognizer
- OpenJTalk
- VerstaGlSurfaceView
- VoiceViewModel
- Versta — DESIGN.md
- OcrBlockLayoutCache
- WhisperSpeechRecognitionTest
- MnnObjectCharacterRecognition
- Code Review and Quality
- TextToSpeechViewModel.kt
- CameraTranslationViewModel
- Test-Driven Development
- ocr_anchor.cc
- ObjectCharacterRecognitionRepositoryDatabaseRepository
- Language
- MainApplication.kt
- ExternalLanguagePairDefinition
- BergamotTinyInference
- LicenseRepository
- ExternalSpeechRecognitionModelDefinition
- TranslationPreferenceRepository
- SpeechContextStoreTest
- LanguageRepository
- .definition
- ObjectCharacterRecognitionBundleWithFiles
- SpeechRecognitionWithFiles
- Jetpack Compose Component Library
- DataModel
- Engine
- Code Simplification
- SpeechRecognitionViewModel.kt
- OcrBlockLayout
- CameraTranslationResult
- Android Navigation Patterns
- LanguagePair
- TextTranslationViewModel.kt
- Timber
- Timber
- Source First License 1.1
- Android Kotlin Development
- TextBox
- DownloadStatus
- Conversation.kt
- HttpDownloadClient
- Spacing.kt
- leanmt.cc
- JNIEnv
- ObjectCharacterRecognitionModuleWithFiles
- Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage
- Material Design 3 Theming
- OcrEngine
- JapaneseTransliterator
- OcrStripStore
- VoiceModelMetadata.kt
- ocr_dewarp.cc
- screen/CameraTranslation.kt
- BubbleActivity.kt
- TranslationPreferenceDataStoreRepository
- espeak_ng.cc
- TranslationPreferenceMemoryRepository
- Tasks
- TextToSpeechViewModel.kt
- DownloadManager
- ocr_track.cc
- ExternalObjectCharacterRecognitionModelDefinition
- TarballExtractor
- VoiceDatabaseRepository
- SpeechRecognitionInference
- JNIEnv
- VoiceWithModelFiles
- Engine::detect
- MicrophoneCapture
- whisper.cc
- CustomThemeViewModel
- Whisper
- tasteskill: Anti-Slop Frontend Skill
- Appendix B - Canonical Sources (read these before reinventing)
- WhisperSpeechRecognition
- Engine::runErase
- Clean Code - Pragmatic AI Coding Standards
- FileSaver.kt
- DownloadWorker
- LanguageViewModel
- Screens
- engine.cc
- MarshalledStrip
- NodeFeature
- SpeechRecognitionEngine
- NavigationViewModel
- App Layer Conventions
- SpeechRecognitionWithFilesTest
- LanguageModelPair
- LoggingViewModel
- M3: GL Overlay Composite Implementation Plan
- 4. DESIGN ENGINEERING DIRECTIVES (Bias Correction)
- ScaffoldModalBottomSheet
- WhisperModelHandle
- Writing Plans
- ModelFilePicker
- SwipeDelete.kt
- GDPR Data Handling
- ExternalSpeechRecognitionModelsFileRepositoryTest
- MinimalLanguageSelector.kt
- ObjectCharacterRecognitionAnalyzerAsyncTest
- TranslateBubbleNotification
- OcrBlockLayoutCache
- Preview
- Versta.Android
- C++ Coding Standards
- directorySize
- 10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know)
- Verification Before Completion
- AnchorWarmupGate
- SpeechRecognitionInitialPromptsTest
- MinimalLanguageSelector.kt
- ocr_homography.cc
- MnnObjectCharacterRecognition
- SpeechRecognitionDatabaseRepository
- Implementation Patterns
- Vocabulary
- Surface
- 9. AI TELLS (Forbidden Patterns)
- Core Concepts
- Mobile Touch Animation
- ExternalVoiceModelDefinition
- TextToSpeechInference
- SpeechRecognitionMockInference
- Color.kt
- Native Bridge Conventions
- APPENDICES - Real Source-Backed Reference Material
- 11. REDESIGN PROTOCOL
- 3. DEFAULT ARCHITECTURE & CONVENTIONS
- 6. PERFORMANCE & ACCESSIBILITY GUARDRAILS
- Android Mobile Design
- LanguageDatabaseRepository
- OcrBlockLayout
- ObjectCharacterRecognitionModule
- AudioExtensionsTest
- AudioCapture
- LanguagePairBadge
- Engine::applyGlyphMatte
- ObjectCharacterRecognitionBundleWithFilesTest
- ocr_pipeline.h
- TrailingProbe
- ExternalVoiceModelsFileRepository
- 12. THE BLOCK LIBRARY (Contract - Implementations Land Here Iteratively)
- 5. CONTEXT-AWARE PROACTIVITY
- 8. DARK MODE PROTOCOL
- Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer
- TranslateNotificationActivity.kt
- MosesPunctuationNormalizer
- LanguageOption
- gradlew
- 7. DIAL DEFINITIONS (Technical Reference)
- ObjectCharacterRecognitionViewModel.kt
- TextToSpeechViewModel
- LanguageSelectionDrawer.kt
- SpeechContextStore
- StyleTextToSpeech2Tokenizer
- LanguageDetect
- WhisperModel
- SettingsDefaults
- KltParams
- ContentColor.kt
- SliderLogarithmic.kt
- plan-document-reviewer-prompt.md
- Tasks
- VoiceWaveform.kt
- MockModel
- VoiceGender
- DeviceUtils.kt
- ESpeakNG
- .play
- .synthesize
- 2026-08-28-native-live-tick.md
- ObjectCharacterRecognitionAnalyzer

## God Nodes (most connected - your core abstractions)
1. `Engine` - 102 edges
2. `NavigationViewModel` - 97 edges
3. `Language` - 89 edges
4. `ScaffoldViewModel` - 80 edges
5. `LanguageViewModel` - 77 edges
6. `ApplicationModule` - 70 edges
7. `Recognizer` - 62 edges
8. `LanguagePair` - 62 edges
9. `TextToSpeechViewModel` - 57 edges
10. `ScaffoldComponentProvider()` - 52 edges

## Surprising Connections (you probably didn't know these)
- `Engine::detect()` --references--> `Point`  [INFERRED]
  app/native/jni/src/ocr/detect.cc → app/native/jni/src/include/ocr_pipeline.h
- `backgroundField()` --references--> `Point`  [INFERRED]
  app/native/jni/src/ocr/erase.cc → app/native/jni/src/include/ocr_pipeline.h
- `loadCharset()` --references--> `Decoded`  [INFERRED]
  app/native/jni/src/ocr/recognize.cc → app/native/jni/src/include/ocr_pipeline.h
- `recognizeBox()` --calls--> `strip`  [INFERRED]
  app/native/jni/src/ocr/recognize.cc → app/native/jni/src/include/ocr_pipeline.h
- `loadCharset()` --references--> `TextLine`  [INFERRED]
  app/native/jni/src/ocr/recognize.cc → app/native/jni/src/include/ocr_pipeline.h

## Import Cycles
- None detected.

## Communities (184 total, 25 thin omitted)

### Community 0 - "TextLine"
Cohesion: 0.09
Nodes (31): shared_ptr, TextLine, bgColor, blockId, bold, box, erase, eraseCorners (+23 more)

### Community 1 - "Theme.kt"
Cohesion: 0.12
Nodes (26): ButtonCard(), ButtonCardColors, ButtonCardDefaults, ButtonCardPreview(), Color, ImageVector, Modifier, Color (+18 more)

### Community 2 - "LanguageMemoryRepository"
Cohesion: 0.06
Nodes (40): AudioMockPlayer, DataMemoryRepository, ExternalDataMemoryRepository, ExternalLanguageModelsMemoryRepository, LanguageMemoryRepository, LanguagePreferenceMemoryRepository, TextToSpeechMockInference, TextToSpeechMockTokenizer (+32 more)

### Community 3 - "StyleTextToSpeechInference"
Cohesion: 0.08
Nodes (21): ByteBuffer, FloatArray, LongArray, OrtSession, Waveform, StyleTextToSpeechInference, Buffer, TensorUtils (+13 more)

### Community 4 - "Recognizer"
Cohesion: 0.04
Nodes (51): deque, time_point, Recognizer, abort_count, abort_deadline, callback_method, callback_obj, carried_prompt_ids (+43 more)

### Community 5 - "OpenJTalk"
Cohesion: 0.15
Nodes (17): jlong, JNIEnv, JNIEXPORT, jobject, jstring, vector, Java_app_versta_translate_bridge_speech_OpenJTalk_close(), Java_app_versta_translate_bridge_speech_OpenJTalk_construct() (+9 more)

### Community 6 - "VerstaGlSurfaceView"
Cohesion: 0.08
Nodes (16): AutoCloseable, ByteArray, ByteBuffer, FloatArray, LiveGlRenderer, GlThread, buffer, ByteBuffer (+8 more)

### Community 7 - "VoiceViewModel"
Cohesion: 0.22
Nodes (5): ExternalVoiceDownloadTask, Flow, StateFlow, ViewModel, VoiceViewModel

### Community 8 - "Versta — DESIGN.md"
Cohesion: 0.18
Nodes (10): 1. The five rules, 2. Colour, 3. Type, 4. Layout, 5. Motion, 6. Icons, 7. Copy, 8. Choosing a component (+2 more)

### Community 9 - "OcrBlockLayoutCache"
Cohesion: 0.09
Nodes (44): GlRendererHandle, camera, fbo, fboH, fboTex, fboW, overlay, overlayH (+36 more)

### Community 10 - "WhisperSpeechRecognitionTest"
Cohesion: 0.17
Nodes (3): FloatArray, MockAudioCapture, WhisperSpeechRecognitionTest

### Community 11 - "MnnObjectCharacterRecognition"
Cohesion: 0.17
Nodes (6): FakeOcr, ByteBuffer, ByteBuffer, LiveTick, ObjectCharacterRecognitionInference, OcrAnalysisResult

### Community 12 - "Code Review and Quality"
Cohesion: 0.07
Nodes (29): 1. Correctness, 2. Readability & Simplicity, 3. Architecture, 4. Security, 5. Performance, Change Descriptions, Change Sizing, Code Review and Quality (+21 more)

### Community 13 - "TextToSpeechViewModel.kt"
Cohesion: 0.08
Nodes (21): Flow, DataRepository, Flow, ExternalDataFileRepository, ExternalDataDefinitions, Flow, ExternalDataDefinitions, Flow (+13 more)

### Community 14 - "CameraTranslationViewModel"
Cohesion: 0.10
Nodes (15): LiveOverlayTick, CameraTranslationViewModel, ImageCapture, ByteBuffer, Context, LifecycleOwner, StateFlow, SurfaceRequest (+7 more)

### Community 15 - "Test-Driven Development"
Cohesion: 0.07
Nodes (29): Browser Testing with DevTools, Common Rationalizations, DAMP Over DRY in Tests, Decision Guide, Discover the Stack First, Name Tests Descriptively, One Assertion Per Concept, Overview (+21 more)

### Community 16 - "ocr_anchor.cc"
Cohesion: 0.05
Nodes (57): FeatureSet, descs, kps, Descriptor, Point2f, reset, adjustedInlierScore(), anchorAabbCorners() (+49 more)

### Community 17 - "ObjectCharacterRecognitionRepositoryDatabaseRepository"
Cohesion: 0.17
Nodes (6): ObjectCharacterRecognitionRepositoryDatabaseRepository, ObjectCharacterRecognitionArchitecture, PaddleOCR, ObjectCharacterRecognitionMetadataFile, ObjectCharacterRecognitionModuleModel, OcrModuleDatabaseModel

### Community 18 - "Language"
Cohesion: 0.08
Nodes (8): LongArray, Context, Language, WritingDirection, LTR, RTL, Job, FloatArray

### Community 19 - "MainApplication.kt"
Cohesion: 0.08
Nodes (32): FileHashValidator, PrecomputedHashFileValidator, ExternalVoiceModelsRepository, SpeechRecognitionRepository, LongArray, TextToSpeechTokenizer, NavKey, ScaffoldComponent (+24 more)

### Community 20 - "ExternalLanguagePairDefinition"
Cohesion: 0.11
Nodes (12): ExternalLanguageModelTest, ExternalLanguageModelsFileRepository, Flow, Flow, ExternalLanguageModelsRepository, Flow, ExternalLanguageMetadata, ExternalLanguageModelDefinition (+4 more)

### Community 21 - "BergamotTinyInference"
Cohesion: 0.05
Nodes (18): BergamotTinyInferenceTest, MockTranslationEngine, LeanmtTest, BergamotTinyInference, TranslationInference, Leanmt, AutoCloseable, LeanmtModel (+10 more)

### Community 22 - "LicenseRepository"
Cohesion: 0.24
Nodes (4): Flow, LicenseDataStoreRepository, Flow, LicenseRepository

### Community 23 - "ExternalSpeechRecognitionModelDefinition"
Cohesion: 0.16
Nodes (9): ExternalSpeechRecognitionModelsFileRepository, Flow, ExternalSpeechRecognitionModelsMemoryRepository, Flow, ExternalSpeechRecognitionModelsRepository, Flow, ExternalSpeechRecognitionModelDefinition, ExternalSpeechRecognitionModels (+1 more)

### Community 26 - "LanguageRepository"
Cohesion: 0.08
Nodes (12): DownloadLanguageWorker, Flow, LanguageRepository, LanguageBundleData, LanguageBundleMetadata, LanguageMetadata, LanguageModelArchitecture, BergamotTinyModel (+4 more)

### Community 28 - "ObjectCharacterRecognitionBundleWithFiles"
Cohesion: 0.25
Nodes (3): preferredFile(), scriptRoutes(), ObjectCharacterRecognitionBundleWithFiles

### Community 29 - "SpeechRecognitionWithFiles"
Cohesion: 0.20
Nodes (6): Flow, Flow, SpeechRecognitionArchitecture, Whisper, SpeechRecognitionInferenceFiles, SpeechRecognitionWithFiles

### Community 30 - "Jetpack Compose Component Library"
Cohesion: 0.09
Nodes (22): Alert Dialog, Animated Content, Animated Visibility, Animations, Basic LazyColumn, Content Loading Pattern, Date and Time Pickers, Dialogs and Bottom Sheets (+14 more)

### Community 31 - "DataModel"
Cohesion: 0.13
Nodes (9): DataDatabaseRepository, DataBundleMetadata, DataMetadata, DataMetadataInterface, DataModel, TextToSpeechDataFilesMetadata, TextToSpeechDataMetadata, TextToSpeechDataMetadataFile (+1 more)

### Community 32 - "Engine"
Cohesion: 0.03
Nodes (75): AnchorState, Engine, addRecognizer, alignDocument, _aligner, _alignerSession, analyze, analyzeLive (+67 more)

### Community 33 - "Code Simplification"
Cohesion: 0.09
Nodes (21): 1. Preserve Behavior Exactly, 2. Follow Project Conventions, 3. Prefer Clarity Over Cleverness, 4. Maintain Balance, 5. Scope to What Changed, Code Simplification, Common Rationalizations, Language-Specific Guidance (+13 more)

### Community 34 - "SpeechRecognitionViewModel.kt"
Cohesion: 0.11
Nodes (15): supportedLanguageIsoCodes(), CoroutineScope, FloatArray, Flow, StateFlow, ViewModel, LoadRequest, SpeechRecognitionViewModel (+7 more)

### Community 35 - "OcrBlockLayout"
Cohesion: 0.14
Nodes (7): OcrTextMeasureTest, BreakOpp, OcrBlockLayout, Result, FloatArray, IntArray, OcrTextMeasure

### Community 36 - "CameraTranslationResult"
Cohesion: 0.20
Nodes (10): ByteArray, IntArray, OcrOverlayBakerTest, BakedOverlay, CameraTranslationBlockLine, CameraTranslationResult, OcrRenderStrip, FloatArray (+2 more)

### Community 37 - "Android Navigation Patterns"
Cohesion: 0.10
Nodes (20): Android Navigation Patterns, Back Handler, Basic Deep Link Setup, Basic Navigation, Bottom Nav with Badges, Bottom Navigation, Deep Linking, Handling Intent in Activity (+12 more)

### Community 38 - "LanguagePair"
Cohesion: 0.13
Nodes (4): LanguagePair, Flow, StateFlow, ViewModel

### Community 39 - "TextTranslationViewModel.kt"
Cohesion: 0.33
Nodes (3): Context, Flow, ViewModel

### Community 40 - "Timber"
Cohesion: 0.12
Nodes (9): ObjectCharacterRecognitionBundleMetadata, Context, MainApplication, FileLoggingTree, LocaleUtils, executeAsListFlow(), Application, SimpleDateFormat (+1 more)

### Community 41 - "Timber"
Cohesion: 0.16
Nodes (7): DatabaseContainer, Migration, Migration3, Context, Migration4, Migration6, Migration8

### Community 42 - "Source First License 1.1"
Cohesion: 0.10
Nodes (18): Acceptance, Copyright License, Definitions, Fair Use, Limitations, No Liability, No Other Rights, Notices (+10 more)

### Community 43 - "Android Kotlin Development"
Cohesion: 0.11
Nodes (15): Jetpack Compose UI, Jetpack Compose UI, Models & API Service, Models & API Service, MVVM ViewModels with Jetpack, MVVM ViewModels with Jetpack, Android Kotlin Development, Best Practices (+7 more)

### Community 44 - "TextBox"
Cohesion: 0.12
Nodes (38): Decoded, accepted, firings, score, text, DewarpedStrip, region, strip (+30 more)

### Community 45 - "DownloadStatus"
Cohesion: 0.09
Nodes (13): Cancelled, Completed, DownloadStatus, Error, Idle, Processing, Progress, Queued (+5 more)

### Community 46 - "Conversation.kt"
Cohesion: 0.17
Nodes (18): IntArray, WhisperSegmentCallback, WhisperSegmentCallback, SpeechRecognitionSegment, GradientMicButton(), GradientMicButtonDarkPreview(), GradientMicButtonLightPreview(), Modifier (+10 more)

### Community 47 - "HttpDownloadClient"
Cohesion: 0.14
Nodes (9): DownloadClient, DownloadListener, URI, HttpDownloadClient, Callback, DownloadListener, URI, Call (+1 more)

### Community 48 - "Spacing.kt"
Cohesion: 0.11
Nodes (23): AnnotatedString, Flow, LicenseMemoryRepository, DialogState, Closed, Confirm, Open, StateFlow (+15 more)

### Community 49 - "leanmt.cc"
Cohesion: 0.20
Nodes (21): jlong, JNIEnv, JNIEXPORT, jobject, jobjectArray, jstring, shared_ptr, unique_ptr (+13 more)

### Community 50 - "JNIEnv"
Cohesion: 0.41
Nodes (18): jint, jlong, JNIEnv, JNIEXPORT, jobject, jstring, engineFor(), Java_app_versta_translate_bridge_inference_OcrEngine_addRecognizer() (+10 more)

### Community 51 - "ObjectCharacterRecognitionModuleWithFiles"
Cohesion: 0.12
Nodes (7): Flow, ObjectCharacterRecognitionRepository, Flow, ObjectCharacterRecognitionRepositoryMemoryRepository, ObjectCharacterRecognitionModuleFile, ObjectCharacterRecognitionModuleMetadata, ObjectCharacterRecognitionModuleWithFiles

### Community 52 - "Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage"
Cohesion: 0.15
Nodes (18): jlong, JNIEnv, JNIEXPORT, jobject, jstring, DetectionResult, confidence, isReliable (+10 more)

### Community 53 - "Material Design 3 Theming"
Cohesion: 0.11
Nodes (17): Color Roles Usage, Color System, Custom Color Scheme, Custom Fonts, Custom Shape Usage, Dynamic Color (Material You), Elevation and Shadows, Extended Colors (+9 more)

### Community 54 - "OcrEngine"
Cohesion: 0.16
Nodes (4): AutoCloseable, Buffer, FloatArray, OcrEngine

### Community 55 - "JapaneseTransliterator"
Cohesion: 0.15
Nodes (6): GenericTransliterator, JapaneseTransliterator, Transliteration, TransliterationAdapter, Token, Transliterator

### Community 56 - "OcrStripStore"
Cohesion: 0.27
Nodes (6): ByteArray, OcrStripStoreTest, OcrErasedStrip, Entry, OcrStripStore, ArrayList

### Community 57 - "VoiceModelMetadata.kt"
Cohesion: 0.11
Nodes (11): DownloadVoiceWorker, VoiceBundleMetadata, VoiceInferenceFilesMetadata, VoiceMetadataFile, VoiceModel, VoiceModelArchitecture, Kokoro, StyleTTS2 (+3 more)

### Community 58 - "ocr_dewarp.cc"
Cohesion: 0.07
Nodes (48): Rect, OrientedRect, angle, cx, cy, height, width, Point (+40 more)

### Community 59 - "screen/CameraTranslation.kt"
Cohesion: 0.13
Nodes (19): FloatArray, OcrBlockRender, PointF, lineQuadOf(), OcrLineQuad, CameraPermissionDenied(), CameraTranslation(), CameraViewFinder() (+11 more)

### Community 60 - "BubbleActivity.kt"
Cohesion: 0.10
Nodes (20): BubbleActivity, Activity, Bundle, ComponentActivity, Intent, Idle, Preparing, Synthesizing (+12 more)

### Community 62 - "espeak_ng.cc"
Cohesion: 0.26
Nodes (16): JNIEnv, JNIEXPORT, jobject, jstring, getJniEnv(), Java_app_versta_translate_bridge_speech_ESpeakNG_cancel(), Java_app_versta_translate_bridge_speech_ESpeakNG_construct(), Java_app_versta_translate_bridge_speech_ESpeakNG_initialize() (+8 more)

### Community 63 - "TranslationPreferenceMemoryRepository"
Cohesion: 0.12
Nodes (8): Flow, TranslationPreferenceMemoryRepository, Modifier, T, SliderPredefinedValues(), PaddingValues, TranslationSettings(), TranslationSettingsPreview()

### Community 64 - "Tasks"
Cohesion: 0.12
Nodes (15): A. Fonts too small, B. Tracking laggy/jittery, C. Matte blocks too small, Decisions (user-approved), Milestones, Overlay Parity Implementation Plan, Root causes (from research), Task 1 — Matte vertical coverage (+7 more)

### Community 65 - "TextToSpeechViewModel.kt"
Cohesion: 0.14
Nodes (6): AudioPlayer, ByteArray, FloatArray, AudioTrackPlayer, ByteArray, FloatArray

### Community 66 - "DownloadManager"
Cohesion: 0.17
Nodes (4): DownloadManager, Context, T, WorkRequest

### Community 67 - "ocr_track.cc"
Cohesion: 0.09
Nodes (42): DescMatch, anchorIdx, distance, frameIdx, coarseHold, dropAnchor, lockedTick, projectOverlays (+34 more)

### Community 68 - "ExternalObjectCharacterRecognitionModelDefinition"
Cohesion: 0.17
Nodes (8): ExternalObjectCharacterRecognitionModelsFileRepository, Flow, Flow, ExternalObjectCharacterRecognitionModelsRepository, Flow, ExternalObjectCharacterRecognitionModelDefinition, ExternalObjectCharacterRecognitionModels, ExternalObjectCharacterRecognitionModelWithState

### Community 69 - "TarballExtractor"
Cohesion: 0.26
Nodes (5): CompressedFileExtractor, ExtractionProgressListener, Uri, Uri, TarballExtractor

### Community 70 - "VoiceDatabaseRepository"
Cohesion: 0.28
Nodes (3): VoiceDatabaseRepository, VoiceDatabaseModel, VoiceModelDatabaseModel

### Community 71 - "SpeechRecognitionInference"
Cohesion: 0.24
Nodes (4): CoroutineScope, FloatArray, Flow, SpeechRecognitionInference

### Community 72 - "JNIEnv"
Cohesion: 0.26
Nodes (21): jfloatArray, jint, jlong, JNIEnv, JNIEXPORT, jobject, jstring, findRecognizer() (+13 more)

### Community 73 - "VoiceWithModelFiles"
Cohesion: 0.12
Nodes (7): Flow, Flow, VoiceRepository, VoiceModelInferenceFiles, VoiceModelTokenizerFiles, VoiceModelVoiceFiles, VoiceWithModelFiles

### Community 74 - "Engine::detect"
Cohesion: 0.27
Nodes (11): orientBoxes, Mat, Profile, vector, DetThresholds, boxMinScore, minArea, Engine::detect() (+3 more)

### Community 75 - "MicrophoneCapture"
Cohesion: 0.24
Nodes (9): CoroutineScope, FloatArray, Job, StateFlow, MicrophoneCapture, fftMagnitudes(), FloatArray, spectrumBands() (+1 more)

### Community 76 - "whisper.cc"
Cohesion: 0.24
Nodes (12): advance_front(), vector, Java_app_versta_translate_bridge_whisper_Whisper_flush(), Java_app_versta_translate_bridge_whisper_Whisper_process(), probe_and_update(), probe_speech(), ProbeUpdate, pause_mid_ms (+4 more)

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
Cohesion: 0.20
Nodes (7): MicrophoneCaptureException, AutoCloseable, CoroutineScope, FloatArray, Flow, Job, WhisperSpeechRecognition

### Community 82 - "Engine::runErase"
Cohesion: 0.25
Nodes (13): thread, backgroundField(), Mat, Rect, Size, vector, channelMedian(), edgeFeather() (+5 more)

### Community 83 - "Clean Code - Pragmatic AI Coding Standards"
Cohesion: 0.14
Nodes (13): Agent → Script Mapping, AI Coding Style, Anti-Patterns (DON'T), 🔴 Before Editing ANY File (THINK FIRST!), Clean Code - Pragmatic AI Coding Standards, Code Structure, Core Principles, Function Rules (+5 more)

### Community 84 - "FileSaver.kt"
Cohesion: 0.19
Nodes (8): FileSaver, FileSaverCallback, FileSaverCallback, Uri, ActivityResultLauncher, ComponentActivity, FileSaverCallback, LogFileSaver

### Community 85 - "DownloadWorker"
Cohesion: 0.18
Nodes (8): DownloadExternalDataWorker, DownloadQueue, DownloadWorker, DownloadListener, DownloadListener, Intent, CoroutineWorker, ForegroundInfo

### Community 86 - "LanguageViewModel"
Cohesion: 0.16
Nodes (13): LanguageViewModel, LanguageSelectionDrawerPreview(), Modifier, LanguageSuggestionDownloadButton(), LanguageSuggestionDrawer(), LanguageSuggestionDrawerPreview(), Details(), ImageVector (+5 more)

### Community 87 - "Screens"
Cohesion: 0.05
Nodes (55): Modifier, ModalDrawerItem(), NavigationDrawer(), NavigationDrawerRailItem(), NavigationItem, Dp, Modifier, VerstaLogo() (+47 more)

### Community 88 - "engine.cc"
Cohesion: 0.15
Nodes (22): load, Interpreter, Recognizer, Session, u32string, unique_ptr, vector, Engine::addRecognizer() (+14 more)

### Community 89 - "MarshalledStrip"
Cohesion: 0.15
Nodes (15): jbyteArray, jfloatArray, jobjectArray, Mat, Point2f, decodeAlignCorners(), Engine::alignDocument(), MarshalledStrip (+7 more)

### Community 90 - "NodeFeature"
Cohesion: 0.14
Nodes (14): NodeFeature, acc, cform, chain_flag, chain_rule, ctype, mora_size, orig (+6 more)

### Community 91 - "SpeechRecognitionEngine"
Cohesion: 0.09
Nodes (10): CoroutineScope, IntArray, WhisperSegmentCallback, MockSpeechRecognitionEngine, AutoCloseable, FloatArray, IntArray, SpeechRecognitionEngine (+2 more)

### Community 92 - "NavigationViewModel"
Cohesion: 0.10
Nodes (45): NavKey, ViewModel, NavigationViewModel, ScaffoldViewModel, ScaffoldCompactBarBackNavigationIcon(), ScaffoldCompactBarEmptyActions(), ScaffoldCompactBarTitle(), ScaffoldComponentProvider() (+37 more)

### Community 93 - "App Layer Conventions"
Cohesion: 0.15
Nodes (12): App Layer Conventions, Don'ts, Graphify, Inference, Kotlin style, Logging, Naming conventions, Package layout (+4 more)

### Community 95 - "LanguageModelPair"
Cohesion: 0.20
Nodes (4): Flow, LanguageModel, LanguageModelPair, PivotPairModelFiles

### Community 96 - "LoggingViewModel"
Cohesion: 0.25
Nodes (6): Context, StateFlow, Uri, ViewModel, LoggingViewModel, FileObserver

### Community 97 - "M3: GL Overlay Composite Implementation Plan"
Cohesion: 0.14
Nodes (13): Execution note (2026-08-28), Global Constraints, Key facts established during recon, M3: GL Overlay Composite Implementation Plan, Out of scope (M4), Task 1 — Native + bridge: surface the tracker homography, Task 2 — Entity/port plumbing: homography on live results, Task 3 — UV matrix math + tests (+5 more)

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

### Community 103 - "SwipeDelete.kt"
Cohesion: 0.70
Nodes (4): DeleteBackground(), Modifier, SwipeDelete(), SwipeToDismissBoxState

### Community 104 - "GDPR Data Handling"
Cohesion: 0.18
Nodes (10): 1. Personal Data Categories, 2. Legal Bases for Processing, 3. Data Subject Rights, Best Practices, Core Concepts, Detailed worked examples and patterns, Do's, Don'ts (+2 more)

### Community 106 - "MinimalLanguageSelector.kt"
Cohesion: 0.24
Nodes (11): ComponentActivity, Context, TranslateBubbleShortcut, Context, CornerBasedShape, Modifier, PaddingValues, LanguageSelector() (+3 more)

### Community 108 - "TranslateBubbleNotification"
Cohesion: 0.14
Nodes (10): Context, TranslateBubbleNotification, Context, TranslateNotification, ComponentActivity, Intent, MainActivity, T (+2 more)

### Community 109 - "OcrBlockLayoutCache"
Cohesion: 0.27
Nodes (3): OcrBlockLayoutCacheTest, OcrBlockLayoutCache, Stored

### Community 110 - "Preview"
Cohesion: 0.12
Nodes (29): Size, SpeechRecognitionMemoryRepository, DownloadButton(), Modifier, LanguageDownloadButtonPreview(), LanguageDeletionConfirmationDialog(), LanguageDeletionConfirmationDialogPreview(), Composable (+21 more)

### Community 111 - "Versta.Android"
Cohesion: 0.20
Nodes (9): Architecture, Build & verification, Dependency injection, Documentation, Git conventions, Project identity, Repo map, Testing (+1 more)

### Community 112 - "C++ Coding Standards"
Cohesion: 0.20
Nodes (9): C++ Coding Standards, Class/Type Naming, File Naming, Function/Method Naming, Header Guards / Pragma, Namespace Naming, Organization, Smart Pointers (+1 more)

### Community 113 - "directorySize"
Cohesion: 0.36
Nodes (4): directorySize(), SimpleFileVisitor, BasicFileAttributes, FileVisitResult

### Community 114 - "10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know)"
Cohesion: 0.20
Nodes (10): 10. REFERENCE VOCABULARY (Pattern Names the Agent Should Know), Animation Library Choice, Cards & Containers, Galleries & Media, Hero Paradigms, Layout & Grids, Micro-Interactions & Effects, Navigation & Menus (+2 more)

### Community 115 - "Verification Before Completion"
Cohesion: 0.20
Nodes (9): Common Failures, Key Patterns, Overview, Rationalization Prevention, Red Flags - STOP, The Gate Function, The Iron Law, Verification Before Completion (+1 more)

### Community 118 - "MinimalLanguageSelector.kt"
Cohesion: 0.36
Nodes (9): LanguageType, Source, Target, Context, CornerBasedShape, Modifier, PaddingValues, MinimalLanguageSelector() (+1 more)

### Community 119 - "ocr_homography.cc"
Cohesion: 0.24
Nodes (14): array, H9, vector, fitAffine(), fitHomography(), fitSimilarity(), invert(), matMul() (+6 more)

### Community 120 - "MnnObjectCharacterRecognition"
Cohesion: 0.27
Nodes (3): AutoCloseable, ByteBuffer, MnnObjectCharacterRecognition

### Community 121 - "SpeechRecognitionDatabaseRepository"
Cohesion: 0.08
Nodes (12): SpeechRecognitionMetadataTest, DownloadSpeechRecognitionWorker, SpeechRecognitionDatabaseRepository, SpeechRecognitionBundleMetadata, SpeechRecognitionFilesMetadata, SpeechRecognitionInferenceFilesMetadata, SpeechRecognitionMetadata, SpeechRecognitionMetadataFile (+4 more)

### Community 122 - "Implementation Patterns"
Cohesion: 0.22
Nodes (8): Compliance Checklist, gdpr-data-handling — detailed worked examples, Implementation Patterns, Pattern 1: Consent Management, Pattern 2: Data Subject Access Request (DSAR), Pattern 3: Data Retention, Pattern 4: Privacy by Design, Pattern 5: Breach Notification

### Community 124 - "Surface"
Cohesion: 0.09
Nodes (41): app, ExternalObjectCharacterRecognitionModelsMemoryRepository, ExternalVoiceLanguageVoiceGenders, ObjectCharacterRecognitionViewModel, Divider(), Dp, ListDivider(), Color (+33 more)

### Community 125 - "9. AI TELLS (Forbidden Patterns)"
Cohesion: 0.25
Nodes (8): 9.A Visual & CSS, 9. AI TELLS (Forbidden Patterns), 9.B Typography, 9.C Layout & Spacing, 9.D Content & Data ("Jane Doe" Effect), 9.E External Resources & Components, 9.F Production-Test Tells (banned outright), 9.G EM-DASH BAN (the single most-violated Tell)

### Community 126 - "Core Concepts"
Cohesion: 0.25
Nodes (7): 1. Material Design 3 Principles, 2. Jetpack Compose Layout System, 3. Navigation Patterns, 4. Material 3 Theming, 5. Component Examples, Core Concepts, mobile-android-design — detailed sections

### Community 127 - "Mobile Touch Animation"
Cohesion: 0.25
Nodes (7): Android, Haptic Guidelines, iOS, Mobile Touch Animation, Platform Patterns, Principle Applications, Quick Reference

### Community 128 - "ExternalVoiceModelDefinition"
Cohesion: 0.15
Nodes (10): ExternalVoiceModelsMemoryRepository, ExternalVoiceModelDefinitions, Flow, ExternalVoiceModelDefinitions, Flow, ExternalVoice, ExternalVoiceModelDefinition, ExternalVoiceModels (+2 more)

### Community 129 - "TextToSpeechInference"
Cohesion: 0.25
Nodes (3): FloatArray, LongArray, TextToSpeechInference

### Community 130 - "SpeechRecognitionMockInference"
Cohesion: 0.24
Nodes (4): CoroutineScope, FloatArray, Flow, SpeechRecognitionMockInference

### Community 131 - "Color.kt"
Cohesion: 0.16
Nodes (9): PointF, ObjectCharacterRecognitionAnalyzerMappingTest, mapOcrLineResult(), ObjectCharacterRecogniserColors, OcrLineResult, OcrTextBox, OcrDetectedLine, ButtonDefaults (+1 more)

### Community 132 - "Native Bridge Conventions"
Cohesion: 0.22
Nodes (8): C++ style & build, JNI binding shape, Layout, Native Bridge Conventions, OCR pipeline (MNN), Opaque handle pattern, Ownership & threading, Vendored libraries

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

### Community 138 - "LanguageDatabaseRepository"
Cohesion: 0.15
Nodes (5): Flow, LanguageDatabaseRepository, PivotPair, LanguageDatabaseModel, LanguageModelDatabaseModel

### Community 141 - "ObjectCharacterRecognitionModule"
Cohesion: 0.22
Nodes (7): ObjectCharacterRecognitionModule, Aligner, Detector, GlyphMatte, Recognizer, ScriptClassifier, TextlineOrientation

### Community 143 - "AudioCapture"
Cohesion: 0.32
Nodes (4): AudioCapture, CoroutineScope, FloatArray, StateFlow

### Community 144 - "LanguagePairBadge"
Cohesion: 0.44
Nodes (7): Color, ImageVector, Modifier, LanguagePairBadge(), LanguagePairBadgeColors, LanguagePairBadgeDefaults, LanguagePairBadgePreview()

### Community 145 - "Engine::applyGlyphMatte"
Cohesion: 0.23
Nodes (19): Mat, Session, vector, channelByte(), Engine::applyGlyphMatte(), luma8Of(), MatteOutputs, background (+11 more)

### Community 147 - "ocr_pipeline.h"
Cohesion: 0.05
Nodes (50): CharFiring, at, ch, score, detUnclipDistance(), _mutex, ErasedStrip, epoch (+42 more)

### Community 148 - "TrailingProbe"
Cohesion: 0.33
Nodes (6): TrailingProbe, first_speech_start_ms, last_pause_mid_ms, last_speech_end_ms, last_voiced_end_ms, valid

### Community 149 - "ExternalVoiceModelsFileRepository"
Cohesion: 0.43
Nodes (3): ExternalVoiceModelsFileRepository, ExternalVoiceModelDefinitions, Flow

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

### Community 156 - "LanguageOption"
Cohesion: 0.09
Nodes (8): Flow, LanguagePreferenceDataStoreRepository, Flow, Flow, LanguagePreferenceRepository, AutoDetectLanguage, LanguageOption, LanguageOptionPair

### Community 157 - "gradlew"
Cohesion: 0.60
Nodes (3): gradlew script, die(), warn()

### Community 158 - "7. DIAL DEFINITIONS (Technical Reference)"
Cohesion: 0.50
Nodes (4): 7. DIAL DEFINITIONS (Technical Reference), DESIGN_VARIANCE (Level 1-10), MOTION_INTENSITY (Level 1-10), VISUAL_DENSITY (Level 1-10)

### Community 159 - "ObjectCharacterRecognitionViewModel.kt"
Cohesion: 0.29
Nodes (4): DownloadObjectCharacterRecognitionWorker, Flow, StateFlow, ViewModel

### Community 160 - "TextToSpeechViewModel"
Cohesion: 0.16
Nodes (6): TextToSpeechViewModel, Completed, Error, Idle, InProgress, LoadingProgress

### Community 161 - "LanguageSelectionDrawer.kt"
Cohesion: 0.64
Nodes (7): Context, Modifier, LanguageSelectionDrawer(), LanguageSelectionListItem(), LanguageSelectionNoItems(), LanguageSelectionSourceLanguage(), LanguageSelectionTargetLanguage()

### Community 162 - "SpeechContextStore"
Cohesion: 0.43
Nodes (3): Entry, IntArray, SpeechContextStore

### Community 164 - "LanguageDetect"
Cohesion: 0.33
Nodes (3): AutoCloseable, LanguageDetect, LanguageDetectResult

### Community 167 - "KltParams"
Cohesion: 0.33
Nodes (6): Size, KltParams, criteria, maxLevel, win, TermCriteria

### Community 174 - "Tasks"
Cohesion: 0.33
Nodes (5): Async Live Acquire Implementation Plan, Task 1 — Analyzer rework (TDD), Task 2 — VM wiring, Task 3 — Verify, Tasks

### Community 175 - "VoiceWaveform.kt"
Cohesion: 0.40
Nodes (9): appendRidge(), catmullRom(), envelope(), FloatArray, Modifier, VoiceStratum, VoiceWaveform(), VoiceWaveformDarkPreview() (+1 more)

### Community 177 - "VoiceGender"
Cohesion: 0.08
Nodes (9): Flow, TextToSpeechPreferenceDataStoreRepository, Flow, Flow, TextToSpeechPreferenceRepository, VoiceGender, Female, Male (+1 more)

### Community 179 - "ESpeakNG"
Cohesion: 0.24
Nodes (4): ByteArray, Flow, MutableStateFlow, SynthReadyCallback

### Community 194 - "ObjectCharacterRecognitionAnalyzer"
Cohesion: 0.22
Nodes (8): ByteBuffer, FloatArray, ObjectCharacterRecognitionAnalyzer, PendingAcquire, FontWeight, BOLD, REGULAR, ObjectCharacterRecogniserResult

## Knowledge Gaps
- **677 isolated node(s):** `program`, `aPos`, `uUv`, `uTex`, `uClipFlipY` (+672 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Engine` connect `Engine` to `TextLine`, `ocr_track.cc`, `Engine::detect`, `TextBox`, `Engine::runErase`, `ocr_pipeline.h`, `JNIEnv`, `engine.cc`?**
  _High betweenness centrality (0.189) - this node is a cross-community bridge._
- **Why does `string` connect `engine.cc` to `Engine`, `ocr_track.cc`, `Recognizer`, `OpenJTalk`, `JNIEnv`, `OcrBlockLayoutCache`, `TextBox`, `whisper.cc`, `leanmt.cc`, `ocr_pipeline.h`, `NodeFeature`, `espeak_ng.cc`?**
  _High betweenness centrality (0.087) - this node is a cross-community bridge._
- **Why does `CameraTranslationViewModel` connect `CameraTranslationViewModel` to `ObjectCharacterRecognitionAnalyzer`, `CameraTranslationResult`, `Preview`, `Spacing.kt`, `MainApplication.kt`, `AnchorWarmupGate`, `OcrStripStore`, `screen/CameraTranslation.kt`?**
  _High betweenness centrality (0.084) - this node is a cross-community bridge._
- **What connects `program`, `aPos`, `uUv` to the rest of the system?**
  _677 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `TextLine` be split into smaller, more focused modules?**
  _Cohesion score 0.08907563025210084 - nodes in this community are weakly interconnected._
- **Should `Theme.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1166429587482219 - nodes in this community are weakly interconnected._
- **Should `LanguageMemoryRepository` be split into smaller, more focused modules?**
  _Cohesion score 0.06225520511234797 - nodes in this community are weakly interconnected._