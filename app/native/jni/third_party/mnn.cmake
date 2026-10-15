# MNN (third_party/mnn, pinned tag 3.6.1) — CPU inference runtime for the
# Supertonic 3 TTS engine. Option matrix per the NEU-64 spike validation:
# CPU only, no tools/tests/train/backends we never link, one shared lib
# (MNN_SEP_BUILD=OFF) so the APK gains exactly one 2-4 MB .so per ABI.

set(MNN_BUILD_SHARED_LIBS ON  CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD          OFF CACHE BOOL "" FORCE)
set(MNN_USE_THREAD_POOL    ON  CACHE BOOL "" FORCE)

# Logcat is Android-only; the host-side test build (app/native/tests)
# add_subdirectory's this same file, where <android/log.h> does not exist.
if(ANDROID)
    set(MNN_USE_LOGCAT     ON  CACHE BOOL "" FORCE)
else()
    set(MNN_USE_LOGCAT     OFF CACHE BOOL "" FORCE)
endif()

# CPU only: none of the GPU backends are wired through the engine.
set(MNN_VULKAN  OFF CACHE BOOL "" FORCE)
set(MNN_OPENCL  OFF CACHE BOOL "" FORCE)
set(MNN_OPENGL  OFF CACHE BOOL "" FORCE)
set(MNN_METAL   OFF CACHE BOOL "" FORCE)

# Not used by the engine; keeps CI build time sane (NEU-16 pattern).
set(MNN_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_BENCHMARK OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TEST      OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DEMO      OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TRAIN     OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_CONVERTER OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_OPENCV    OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_AUDIO     OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_LLM       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DIFFUSION OFF CACHE BOOL "" FORCE)
set(MNN_EVALUATION      OFF CACHE BOOL "" FORCE)
set(MNN_JNI             OFF CACHE BOOL "" FORCE)

# ARMv8.2 fp16 kernels on arm64 (matches the bridge's
# -march=armv8.2-a+dotprod+fp16); MNN already forces this OFF for armv7.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(MNN_ARM82 ON CACHE BOOL "" FORCE)
endif()

add_subdirectory(mnn mnn)

# Play's 16 KB page-size requirement applies to every shipped .so; MNN does
# not set the flag itself.
target_link_options(MNN PRIVATE "-Wl,-z,max-page-size=16384")
