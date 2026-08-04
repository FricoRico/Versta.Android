# Versta.Android

This file describes the project's conventions. Read it before writing or
modifying code. More specific conventions live in the nested AGENTS.md files:

- `/app/AGENTS.md` — Kotlin app-layer conventions (architecture, naming, Kotlin style, storage, inference, UI).
- `/app/native/AGENTS.md` — C++/JNI bridge conventions.

## Project identity

Versta is an offline-first translation app for Android. It translates text,
camera/OCR, speech, and text-to-speech entirely on-device.

- Everything must work fully offline after the required model files are downloaded. Never send user data off-device.
- Privacy-first: no telemetry, no analytics, no network calls except explicit user-triggered model downloads.
- Source-available under the Source First license. Attribution must be preserved.
- Android only. Kotlin is the primary language; native inference lives in C++ via JNI.

## Repo map

- `app/` — the single Android application module (Kotlin + Compose). See `/app/AGENTS.md`.
- `app/native/` — C++/JNI bridge library `app_versta_translate_bridge`. See `/app/native/AGENTS.md`.
- `app/native/jni/third_party/` — vendored native libraries kept as git submodules (leanmt, whisper.cpp, espeak-ng, open-jtalk, cld2, opencv-mobile, onnxruntime, ocr-clipper, neon-sse). Never edit vendored code in place.
- `gradle/libs.versions.toml` — single source of truth for dependency versions (version catalog).
- The sibling repo `Versta.Models` (separate project) contains the Python tooling that exports/quantizes the model bundles consumed by this app. It is out of scope here.

## Architecture

Hexagonal (ports & adapters) within a single module, keyed by package under
`app.versta.translate`:

- `core/entity` — pure domain models and value objects. No Android framework types, no ViewModel, no access to the composition root.
- `core/model` — ViewModels. Own UI state and orchestrate ports; expose `Flow`s to the UI.
- `adapter/inbound` — driving adapters: WorkManager workers, notifications, Activity-registered helpers.
- `adapter/outbound` — driven adapters. Kotlin interfaces (the ports) are declared here together with their real and mock implementations.
- `bridge` — Kotlin wrappers over the native C++ library.
- `database` — SQLDelight `DatabaseContainer`, `.sq` schemas, manual migrations.
- `ui/component`, `ui/screen`, `ui/theme` — Compose Material 3 Expressive UI.
- `utils` — extension functions only.

Dependency direction: `ui` → `core/model` → `adapter/outbound` + `bridge` + `database`. `adapter/inbound` may use `core` and the outbound ports. `core/entity` depends on nothing app-internal.

## Dependency injection

Manual, constructor-based. No Hilt/Koin.

- Composition root: `MainApplication.module : ApplicationModuleInterface`, a lazy-singleton `ApplicationModule` built in `MainApplication.onCreate`.
- Every repository/inference/tokenizer dependency is declared on `ApplicationModuleInterface` and wired by constructor.
- ViewModels are created with the `viewModelFactory { }` helper in `utils/ViewModelExtensions.kt`, reading dependencies from `MainApplication.module`.
- Previews and tests construct the same object graph by hand using the `*Memory`/`*Mock` implementations — never real drivers, native libraries, or file I/O.

## Testing

- All tests live in `app/src/androidTest`, mirroring the main package layout, and run on a device or emulator via `connectedDebugAndroidTest`. There is no JVM-only `app/src/test` source set.
- Unit-style tests for core logic (mappers, post-processors, normalization, pure domain behavior) use `*Memory`/`*Mock` implementations and live in `androidTest` too.
- Instrumented tests cover UI, integration, and real-driver behavior.
- Every new feature must ship with unit tests for its core logic. Tests assert behavior, not implementation details.
- Tests must exercise live production code only: when a class is removed or its behavior changes, remove or update its tests in the same change. No placeholder or template tests.
- Mocks must never perform real I/O or load native libraries.

## Build & verification

- Gradle Kotlin DSL. Dependencies and versions come from `gradle/libs.versions.toml`.
- AGP 9.0.1, Kotlin 2.3.10, JDK 17 target, compile/target SDK 36, min SDK 28.
- Native code is built via CMake (`app/native/jni/CMakeLists.txt`, C++17, NDK 28.1). See `/app/native/AGENTS.md`.
- ABI product flavors: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
- Useful commands:
  - `./gradlew :app:compileDebugKotlin` — fast Kotlin compile check.
  - `./gradlew :app:assembleDebug` — full debug build including the native library.
  - `./gradlew :app:lintDebug` — Android lint.
  - `./gradlew :app:connectedDebugAndroidTest` — tests (device/emulator required; tests live in `app/src/androidTest`).
- The `getRemoteData` Gradle task fetches the model-metadata JSON files into `app/src/main/res/raw/` and runs as a `preBuild` dependency. It needs network access.
- `keystore.properties` (gitignored) provides signing credentials; `keystore.properties.example` documents the keys. Never commit secrets.

## Documentation

- Keep `README.md` current. Update it in the same commit as any change to the app's description, features, roadmap, language models, build, or license.

## Git conventions

- Commit summaries are sentence-case and imperative, capitalized, with no trailing period. Examples: `Implementing LeanMT instead of OpusMT for Firefox models`, `Adding back feedback sections`.
- Keep commits small and focused; squash feature work before merge.
- Never commit build artifacts, `.cxx`, keystores, or generated model JSON.
- Third-party native code arrives exclusively through git submodules under `app/native/jni/third_party/` — never vendor it by copying files in.
