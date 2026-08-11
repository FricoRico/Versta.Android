# App Layer Conventions

This file describes conventions for the Kotlin Android application module
under `app/`. It complements the root `/AGENTS.md`; read both before writing
or modifying code here.

## Package layout

All code lives under `app.versta.translate`. One file per top-level
declaration, named after it (companion objects and secondary types may live
in the same file when they are implementation details).

- `core/entity` — pure domain models: `data class`, `sealed class`, `enum class`, value objects. No Android framework types, no ViewModel, no `MainApplication`/composition-root access.
- `core/model` — `ViewModel` subclasses. Own UI state via `MutableStateFlow` + `asStateFlow()`, expose `Flow`s, orchestrate ports. Dependency-injected by constructor.
- `adapter/inbound` — driving adapters: WorkManager `Worker`s, notification builders, Activity-registered helpers (`ModelFilePicker`, `TranslateBubble*`).
- `adapter/outbound` — driven adapters. Ports are Kotlin interfaces declared here; real and mock implementations sit beside them.
- `bridge` — Kotlin wrappers over the native `app_versta_translate_bridge` library, grouped by domain (`bridge/leanmt`, `bridge/whisper`, `bridge/speech`, `bridge/inference`, `bridge/tokenize`, `bridge/utils`).
- `database` — `DatabaseContainer`, SQLDelight schema (`database/sqldelight/*.sq`), manual migrations (`database/migrations/`).
- `ui/component`, `ui/screen`, `ui/theme` — Compose UI.
- `utils` — extension functions only. Never business logic.
- Top-level (`MainApplication.kt`, `MainActivity.kt`, `BubbleActivity.kt`) — composition root and entry points.

## Naming conventions

- **Ports** are bare interfaces: `DataRepository`, `TranslationInference`, `AudioPlayer`, `TextToSpeechTokenizer`, `ObjectCharacterRecognitionRepository`.
- **Implementations** carry the mechanism suffix:
  - `*DatabaseRepository` — SQLDelight-backed.
  - `*FileRepository` — raw-resource JSON-backed (model metadata).
  - `*DataStoreRepository` — DataStore Preferences-backed.
  - `*MemoryRepository` — in-memory mock for previews/tests.
  - `*MockInference`, `*MockTokenizer`, `AudioMockPlayer` — mock ports for previews/tests.
  - `*Inference`, `*Tokenizer`, `*PostProcessor`, `*Transliterator`, `*Player`, `*Capture`, `*Saver`, `*Extractor`, `*Validator` — real implementations of the corresponding port.
- Defaults for a port are `internal const val DEFAULT_*` declared in the port's file (`TranslationPreferenceRepository.kt`).
- Entity-to-database mapping is done by private `mapXToY` functions inside the repository implementation.
- Logging tag: `companion object { private val TAG = ClassName::class.java.simpleName }`; log via `Timber.tag(TAG)`.
- Sealed class state: singular, meaningful names (`LoadingProgress.Idle/InProgress/Completed/Error`), never `Loading`/`Loaded`.

## Graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)


## Kotlin style

- Prefer `val`; only use `var` for `MutableStateFlow` backing fields or genuinely mutable state.
- `data class` for data containers, `sealed class` for restricted state hierarchies, `enum class` for closed value sets.
- Extension functions over static/util classes; they live in `utils/`.
- Coroutines and `Flow` for all asynchronous work; no callback APIs.
- Use null-safety idioms (`?.`, `?:`); avoid `!!`.
- Early returns to keep nesting shallow.
- KDoc on public interfaces, methods, and classes. Keep it concise; cover `@param` and `@return` only where they add information. Do not add comments that restate the code.
- Prefer single-word names for locals and parameters when the word is unambiguous in context (`tokens` not `tokenIds`, `path` not `filePath`). Multi-word names are fine where a single word would be ambiguous or where the name is part of a public API, a data class property, or maps to a DB column/JSON key.
- 4-space indentation; Kotlin official style (`kotlin.code.style=official`).

## Logging

- Timber only: `Timber.tag(TAG).v/d/i/w/e(...)`.
- Never use `android.util.Log` or `println`.
- Errors that cross a public boundary are logged with `Timber.tag(TAG).e(e, "message")`.

## State management

- ViewModel state: `private val _x = MutableStateFlow<T>(initial); val x = _x.asStateFlow()`.
- Combine/flatten derived flows with `map`, `distinctUntilChanged`, `combine`; keep logic in the ViewModel, not the composable.
- UI collects with `collectAsStateWithLifecycle()` (never `collectAsState()` unless there is no lifecycle).
- One-off events and dialog visibility are also modeled as state flows, not lambdas passed around.

## Storage

- **SQLDelight** for app data (language pairs, models, voices, OCR/speech models). Schema in `database/sqldelight/*.sq` with named queries; generate queries by convention. Database access goes through `DatabaseContainer`.
- **Migrations** are manual: one `MigrationN` class per version in `database/migrations/`, registered in `DatabaseContainer`. Add a new one when the schema changes; never edit an existing migration.
- **DataStore Preferences** (`Context.dataStore`) for user settings and preferences. Reads are `Flow`s with `DEFAULT_*` fallbacks; writes go through `edit { }`. Keys are `stringPreferencesKey`s in the repository's companion.
- Never use SharedPreferences.

## Inference

- Native C++ via JNI (`bridge/`) for: translation (LeanMT), speech recognition (whisper.cpp), language detection (cld2), TTS phonemization (espeak-ng, open-jtalk), and OCR (PaddleOCR). See `/app/native/AGENTS.md`.
- ONNX Runtime (`OrtEnvironment`, `OrtSession`) only for StyleTTS2 synthesis and OCR inference fallbacks.
- Bridge classes own a native handle: private `external fun`s, guards against `handle == 0L`, `AutoCloseable`, and `System.loadLibrary("app_versta_translate_bridge")` in the companion. See `bridge/whisper/WhisperRecognizer.kt` for the canonical shape.
- Blocking native calls run on a background dispatcher (`Dispatchers.Default`/`Dispatchers.IO`) from the calling coroutine; never call the native bridge on the main thread.
- Ports wrap bridge classes so the rest of the app depends on the port, not the JNI wrapper.

## UI (Compose, Material 3 Expressive)

- Screens are top-level `@Composable` functions that receive their ViewModels and `innerPadding` as parameters; registered in `ui/component/Router.kt` with Navigation3 `ListDetailSceneStrategy` panes.
- Reusable, stateless components go in `ui/component`; screens compose them.
- Theming in `ui/theme`: `TranslateTheme`, custom color schemes (`ObsidianTheme`), `Spacing`/`Easing` composition locals consumed as `MaterialTheme.spacing` / `MaterialTheme.easing`, `Typography`.
- Every screen gets a `@Preview` composable wired with `*Memory`/`*Mock` implementations — no real drivers, no native libraries, no file I/O in previews.
- Use `Modifier`, `PaddingValues`, and `WindowInsets` for layout; prefer `MaterialTheme.spacing.*` over hard-coded `.dp` values.
- Strings via `stringResource(R.string.…)`; icons via `ImageVector.vectorResource(R.drawable.…)`. Never hard-code user-visible strings.
- Mark experimental APIs with `@OptIn(...)` at the composable or function scope, not at file scope.
- The app-wide mesh gradient (`ui/component/MeshGradientBackground.kt`, Compose `MeshGradientPainter`) is specced in `/DESIGN.md` under *Gradient mesh in the Android app* — consult it before touching the backdrop's placement or colors.

## Testing

- Tests live in `app/src/androidTest`, mirroring the package of the class under test; one test file per class, named `XTest`.
- Write unit-style tests against `*Memory`/`*Mock` implementations only — never real drivers, native libraries, file I/O, or the network.
- One behavior per test; assert observable outcomes, not implementation details.
- Every new feature ships with unit tests for its core logic.
- When production code is removed or its behavior changes, remove or update its tests in the same change.
- Run via `./gradlew :app:connectedDebugAndroidTest` (device/emulator required).

## Don'ts

- No composition-root access from `core/entity` (no `MainApplication.module`, no `MainApplication.context`).
- No `android.util.Log` or `println` — Timber only.
- No SharedPreferences — DataStore or SQLDelight.
- No callbacks where coroutines/Flows exist.
- No `!!` where `?.`/`?:` works.
- Don't add new dependencies without updating `gradle/libs.versions.toml`.
- Don't write tests or previews against real drivers, native libraries, or the network.
- Don't keep tests that cover removed or unused code — delete them with the dead code.
- Don't keep template or placeholder tests (e.g. Android Studio `ExampleInstrumentedTest`).
