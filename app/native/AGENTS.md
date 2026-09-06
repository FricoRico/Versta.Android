# Native Bridge Conventions

This file describes conventions for the C++/JNI bridge library
`app_versta_translate_bridge` under `app/native/`. It complements the root
`/AGENTS.md`; read both before writing or modifying native code.

## Layout

- `app/native/jni/CMakeLists.txt` — top-level build. Adds `src` and `third_party`, defines the shared library, include dirs, and links the vendored libs.
- `app/native/jni/src/*.cc` — one JNI binding file per native component (`whisper.cc`, `leanmt.cc`, `espeak_ng.cc`, `open_jtalk.cc`, `language_detect.cc`, `vocabulary.cc`, `tensor_utils.cc`). The OCR pipeline is split across `engine.cc` (JNI entry points, model/session wiring, orchestration) and per-stage translation units `detect.cc` (DB text detection), `dewarp.cc` (baseline-profile dewarp), `recognize.cc` (CTC decode, script routing), `glyphmatte.cc` (style matte, fg color from the F channel, bg = paper sampled from strip pixels masked by the matte), `erase.cc` (text erasure + background field fill, erased-strip patches), `homography.cc` (DLT/affine/similarity fits + 3x3 math), `anchor.cc` (FAST-9, oriented BRIEF-256, PROSAC RANSAC, homography EKF), `blocks.cc` (paragraph-block union-find grouping), and `track.cc` (anchor acquire/relocalize/KLT coarse tracking for the live preview).
- `app/native/jni/src/include/` — shared headers (`Log.h`, `config.h`, `ocr_pipeline.h` with the OCR `Engine` state shared by the stage units).
- `app/native/jni/third_party/` — vendored libraries as git submodules (leanmt, whisper.cpp, espeak-ng, open-jtalk, cld2, opencv-mobile, onnxruntime, MNN, neon-sse). Each with its own `CMakeLists.txt`; per-ABI SIMD flags live here where `ANDROID_ABI` is reliable.

## JNI binding shape

- Native methods are declared on Kotlin bridge classes as instance `private external fun`s (never `@JvmStatic` companion statics); the class wraps them with public guarded methods. See `bridge/whisper/Whisper.kt` for the canonical Kotlin shape.
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
- Prefer single-word names for locals and parameters when the word is unambiguous in context (`tokens` not `token_ids`). Multi-word names are fine where a single word would be ambiguous or where the name is part of the public JNI surface.
- Release build only: `-O3 -DNDEBUG`. For `arm64-v8a` add `-march=armv8.2-a+dotprod+fp16`.
- Keep the 16 KB ELF page-size link flag (`-Wl,-z,max-page-size=16384`).
- Log through the shared `Log.h` helper; never `printf` into production.
- Standard library and vendored APIs only — no new third-party C++ code checked in directly.

## OCR pipeline (MNN)

- Inference runs on MNN (vendored static, CPU-only build; converter/tools/protobuf/train all OFF, `MNN_USE_SSE=OFF` on 32-bit x86). Detection strides must be probed on a throwaway interpreter — never on the live session.
- Never cache MNN `Tensor*` across `resizeSession`; refetch session input/output inside every run. Never `assign(t.shape().begin(), t.shape().end())` — MNN `shape()` returns a fresh vector per call and the double-temporary iterator pair corrupts. Copy the whole `shape()` result instead.
- PP-OCR CTC has blank at class 0; dictionary index is `argmax - 1` and blank-separated repeated characters survive the collapse.
- PP-OCR's DB detector outputs its *kernel* — the x-height core of a line, not the full glyph band. Strips must dewarp the band p05–p95 spine spread inflated ×2.4 with a descender slice below the baseline (see `dewarpContour`), or the recognizer sees 12px glyph-core slivers and hallucinates case-confused text. Bands/strips are dewarped once per box per pass and shared by duel/routing/recognize/matte (`DewarpedStrip` cache built in `runFullPipeline`).
- Multi-output MNN models: fetch outputs by name, never by "largest float tensor" (that heuristic in `Model::run` exists for the single-useful-output det/rec models; it once silently picked a colour head over the matte). The glyph-matte model emits four named tensors: `matte` (alpha), `weight` (stroke boldness), `foreground` (ink RGB), `background` (paper RGB), each `[1,C,48,W]`.
- The glyph-matte model's `background` head is a *prediction*, never paint it. Paper comes from the strip's own pixels clear of the matte (p75 of the eroded paper mask); foreground comes from `foreground` α²-weighted over the matte's stroke core, guarded against the measured ink's polarity (white-on-dark reads bright — swap to the measurement when the sign flips). `foreground`/`background` together drive the closed-form alpha kill-switch that pulls matte texels back off paper.
- No `measure_line` recentring onto the matte's x-height interval: our renderer centres the baseline in the render band, and the matte band excludes the descender allowance the inflate already bakes in — recentring offsets text ~half a band off its own paper.
- Docaligner outputs regressed `[1,8]` TL/TR/BR/BL corners (not heatmaps); auto-scale by 256 when values look normalized.
- Erasure strips cover the *render* quad (tight band + unclip/border) plus reference pads (0.15w / 0.75h, min 4/8 px), not the raw oriented box — the latter is the kernel band and leaves glyph tops/bottoms unerased. The tall vertical pad deliberately overlaps neighbouring lines; the strip mask samples the frame-space union of ALL lines' ink (`unionMask`), so a neighbour's glyphs inside the pad are erased here too and never peek out — do not shrink the pad "to avoid touching the neighbour", the union makes the overlap safe by construction. Erased strip pixels cross JNI only on the acquire that built them (epoch check); tracked frames re-pose corners only and the Kotlin `OcrStripStore` reuses the cached bitmap — frame-level reset once, not per block.
- The union ink mask binds to frame space by a PER-PIXEL walk of each band region (`projectMaskIntoUnion`, reference `project_box_ink`), never `warpAffine`: the Matx23f affine warp path silently sampled empty/uncorrelated coordinates in this pipeline (coverage 0% with ink visibly present). The same failure hit strip RGB sampling — it is likewise an explicit per-pixel loop (clamped bilinear taps = border-replicate), so NO `warpAffine` remains anywhere in the erase path. Mask sampling out of the union is also the explicit per-pixel loop with nearest center-pixel mapping, and out-of-frame strip pixels are *masked* (field-filled), never border-replicated. Field-measured coverage sanity is ≈30–40% of strip px on dense scenes, glyph-shaped, never flat walls.
- Strip bitmap bytes are packed **memory-order R,G,B,A** — device-probed on emulator + phone: packing the "canonical" little-endian-ARGB order (B,G,R,A) renders all strip content R/B-swapped (brown desk shows as blue, blue emblem as brown). Bytes are **premultiplied** (RGB × A/255 at pack): Canvas composites Bitmaps premultiplied, and straight-alpha feather rims add full-strength RGB at A≈0 — a white halo around every strip.
- Every strip mask (union-sampled or matte-less distance) passes the **graphics kill-switch before the rim dilate**: a masked pixel whose scene colour is far from BOTH the line's paper and ink AND saturated (max-min channel spread) is artwork, not typography — the matte fires on crisp illustration strokes (emblem guilloche, plaques) exactly like on glyphs, and without the switch saturated artwork gets wiped to the paper median. Dropped pixels stay visible in the composite and are field-eligible, so glyphs printed ON artwork fill artwork-coloured locally. Killing before the dilate keeps antialiased glyph rims on artwork covered: the dilate regrows only from surviving glyph cores.
- opencv-mobile links only `core`, `imgproc`, `video` — no `calib3d` (no `findHomography`/`estimateAffine2D`/`RANSAC`) and no `imgcodecs`. All fitter/RANSAC geometry lives in `homography.cc`; KLT uses `cv::calcOpticalFlowPyrLK` (video module) with a manual forward-backward consistency check.
- Relocalize matches GUIDED (reference `match_descriptors_guided`): anchor keypoints project through the `hView` prior, frame candidates limited to a 30 px disk, windowed Lowe 0.85, exactly-one-candidate windows fall back to an absolute 60-bit Hamming gate; brute Lowe fallback only fires under 75 guided matches (prior went stale).
- The relocalize path runs on a DEDICATED WORKER THREAD (reference TrackerCompute): single pending slot, drop-if-busy, epoch-keyed (anchor swaps invalidate in-flight work), owning the correction-side filter state (homography EKF, inlier-EMA freeze gate, sanity gates). The presenter (JNI) thread per locked tick: apply the newest correction — snap, or WEAVE with the KLT motion since the frame the worker consumed via the 16-entry pose ring (`poseRing` on the anchor) — then coarse KLT, ring push, re-dispatch. The coarse pose is emitted RAW (never filtered): filtering lives exclusively on the correction path, and the weave absorbs its one-frame lag.
- Suspicious-fit freeze (inlier EMA drop 0.3 vs EMA ≥ 60, budget 3) and rejected fits travel as typed `RelocResult`s; only the presenter counts failures toward Lost (3 consecutive) or touches KLT state — frozen frames touch neither seeds nor prevGray, keeping them consistent with each other.
- Dropped anchors go into a 3-entry LRU (`_anchorCache`) with their features AND canonical overlay lines (erase strips ride along via shared_ptr); while anchorless, each analyze tick brute-matches the cache first (reference `try_cached_anchors`) so a returning scene re-locks in ~one tick with no stillness wait. Re-lock skips the delta gate (the scene legitimately moved while unlocked); the cache dies with the engine instance (engines are never reused across bundle swaps).
- A successful re-lock RESTORES strips the Kotlin patch store has long overwritten: rebadge the restored lines' `erase->epoch` into the fresh `_eraseEpoch` AND mark the frame strip-fresh, or the overlay renders text with no erased background for the anchor's whole life (rotation away-and-back is the everyday trigger).
- The canonical block snap (`snapBlockTightRects`) shares ONLY reading angle + perpendicular stack alignment (each line keeps its own along-axis position — centering short lines sideways ghosts their start ink) — never width: own widths keep erase envelopes per-line-sized (a union width slabs narrow lines) and keep the legal perspective foreshortening of far lines on an oblique page (the reference's width union is only valid in its rectified surface space, not in our camera-frame canonical space).

## Vendored libraries

- `third_party/` entries are git submodules. Never edit vendored source in place; patch by forking/replacing the submodule, or by wrapping in `src/`.
- On clone, initialize submodules with `git submodule update --init --recursive`.
- Per-ABI optimization flags go in the submodule's own `CMakeLists.txt`, not in Gradle flavor arguments (AGP 9 leaks flavor arguments between ABI flavors).
