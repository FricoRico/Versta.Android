# MNN (third_party/mnn, pinned tag 3.6.1) — the single owner of the MNN build.
#
# MNN is the CPU inference runtime for both on-device model families: the OCR
# pipeline (src/ocr) and the Supertonic 3 TTS engine (supertonic_engine.cc).
# Every MNN option lives here and nowhere else; the host-side test build
# (app/native/tests) includes this same file so the engine is tested against
# the exact option matrix that ships.
#
# Static build, linked into the single bridge library: one .so per ABI, no
# second library to load or ship, and the linker drops what neither consumer
# references. Converter/tools/protobuf/train stay off — models are converted
# offline in Versta.Models and this copy only runs inference on .mnn
# flatbuffers.

set(MNN_BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD             OFF CACHE BOOL "" FORCE)
set(MNN_USE_THREAD_POOL       ON  CACHE BOOL "" FORCE)
set(MNN_SUPPORT_DEPRECATED_OP OFF CACHE BOOL "" FORCE)
set(MNN_JNI                   OFF CACHE BOOL "" FORCE)

# Logcat is Android-only; the host test build has no <android/log.h>.
if(ANDROID)
    set(MNN_USE_LOGCAT ON  CACHE BOOL "" FORCE)
else()
    set(MNN_USE_LOGCAT OFF CACHE BOOL "" FORCE)
endif()

# CPU only: no GPU backend is wired through either consumer.
set(MNN_VULKAN  OFF CACHE BOOL "" FORCE)
set(MNN_OPENCL  OFF CACHE BOOL "" FORCE)
set(MNN_OPENGL  OFF CACHE BOOL "" FORCE)
set(MNN_METAL   OFF CACHE BOOL "" FORCE)

# Nothing below is linked by the bridge; keeping it off keeps CI build time sane.
set(MNN_BUILD_TOOLS       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_QUANTOOLS   OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_BENCHMARK   OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TEST        OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DEMO        OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TRAIN       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_CONVERTER   OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_PROTOBUFFER OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_OPENCV      OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_AUDIO       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_LLM         OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DIFFUSION   OFF CACHE BOOL "" FORCE)
set(MNN_EVALUATION        OFF CACHE BOOL "" FORCE)

# ARMv8.2 fp16 kernels on arm64 (matches the bridge's
# -march=armv8.2-a+dotprod+fp16); MNN already forces this OFF for armv7.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(MNN_ARM82 ON CACHE BOOL "" FORCE)
endif()

# 32-bit x86: MNN's SSE path emits 64-bit AVX objects that fail the elf_i386 link.
if(ANDROID_ABI STREQUAL "x86")
    set(MNN_USE_SSE OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/mnn ${CMAKE_CURRENT_BINARY_DIR}/mnn)

# MNN's own CMakeLists exports its headers with a directory-scoped
# include_directories() only, which stops at the mnn/ subtree. Export them on
# the target, the way cld2.cmake does, so every consumer (the bridge and the
# host test binary) inherits them.
target_include_directories(MNN PUBLIC ${CMAKE_CURRENT_LIST_DIR}/mnn/include)
