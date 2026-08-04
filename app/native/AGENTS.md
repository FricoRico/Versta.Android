# Native Bridge Conventions

This file describes conventions for the C++/JNI bridge library
`app_versta_translate_bridge` under `app/native/`. It complements the root
`/AGENTS.md`; read both before writing or modifying native code.

## Layout

- `app/native/jni/CMakeLists.txt` — top-level build. Adds `src` and `third_party`, defines the shared library, include dirs, and links the vendored libs.
- `app/native/jni/src/*.cc` — one JNI binding file per native component (`whisper.cc`, `leanmt.cc`, `paddle_ocr.cc`, `espeak_ng.cc`, `open_jtalk.cc`, `language_detect.cc`, `vocabulary.cc`, `ocr_text_analyzer.cc`, `tensor_utils.cc`).
- `app/native/jni/src/include/` — shared headers (`Log.h`, `config.h`).
- `app/native/jni/third_party/` — vendored libraries as git submodules (leanmt, whisper.cpp, espeak-ng, open-jtalk, cld2, opencv-mobile, onnxruntime, ocr-clipper, neon-sse). Each with its own `CMakeLists.txt`; per-ABI SIMD flags live here where `ANDROID_ABI` is reliable.

## JNI binding shape

- Native methods are declared on Kotlin bridge classes as instance `private external fun`s (never `@JvmStatic` companion statics); the class wraps them with public guarded methods. See `bridge/whisper/WhisperRecognizer.kt` for the canonical Kotlin shape.
- The JNI symbol for a method is `Java_app_versta_translate_bridge_<package>_<Class>_<method>`. Keep the JNI name in sync with the Kotlin class (`bridge.whisper.WhisperModel` → `Java_app_versta_translate_bridge_whisper_WhisperModel_create`).
- JNI entry points are declared inside `extern "C" { }` blocks. Export with `JNIEXPORT` / `JNICALL`, first two parameters always `JNIEnv *env, jobject` (or `jclass` for statics).
- Convert strings with the local `jstr(JNIEnv*, jstring)` helper; release all `GetStringUTFChars`/`GetStringUTFRegion` buffers and local references before returning.
- Do not throw across the JNI boundary. Return `0`/`nullptr`/`0L` to signal failure; the Kotlin side decides how to surface it.

## Opaque handle pattern

- One native struct per component (`WhisperModelHandle`, etc.). Kotlin holds a `Long` handle; the native side packs all state into the struct.
- Keep instances in a module-level `std::unordered_map<jlong, std::unique_ptr<T>>` keyed by a monotonically increasing counter; `jlong 0` means "invalid/uninitialized" and is never a real handle.
- `create` allocates the struct, stores it, and returns its handle. `destroy`/`close` erases it and deletes the instance.
- Guard every entry point with a null/bounds check on the handle; a destroyed or unknown handle must be handled gracefully, not dereferenced.

## Ownership & threading

- Native calls are blocking. The Kotlin side must invoke them from a background coroutine dispatcher, never the main thread; there is no async native API.
- Protect shared native state with `std::mutex` (see the process-loop mutex in `whisper.cc`). The JNI calls from multiple threads — one lock per critical section, not one global lock.
- The model file handle outlives the recognizer/session; the session must not outlive the model (Kotlin `teardownSession` mirrors this ordering).
- Memory maps (mmap) are used for model files; document the lifetime contract between model, recognizer, and the byte buffer in the owning class.

## C++ style & build

- C++17 (`CMAKE_CXX_STANDARD 17`). Use `//` or `/* */` comments for non-obvious design decisions; keep them with the component's design notes at the top of the file.
- Release build only: `-O3 -DNDEBUG`. For `arm64-v8a` add `-march=armv8.2-a+dotprod+fp16`.
- Keep the 16 KB ELF page-size link flag (`-Wl,-z,max-page-size=16384`).
- Log through the shared `Log.h` helper; never `printf` into production.
- Standard library and vendored APIs only — no new third-party C++ code checked in directly.

## Vendored libraries

- `third_party/` entries are git submodules. Never edit vendored source in place; patch by forking/replacing the submodule, or by wrapping in `src/`.
- On clone, initialize submodules with `git submodule update --init --recursive`.
- Per-ABI optimization flags go in the submodule's own `CMakeLists.txt`, not in Gradle flavor arguments (AGP 9 leaks flavor arguments between ABI flavors).
