include(cld2.cmake)

# Architecture-specific SIMD selection — done here in CMake (where
# ANDROID_ABI is reliable) instead of Gradle product-flavour arguments,
# which are subject to a shared-mutable-list bug in AGP 9.0.1 that leaks
# arguments between flavours.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(USE_NEON           ON  CACHE BOOL "" FORCE)
    set(LEANMT_ARM64_TUNE  ON  CACHE BOOL "" FORCE)
    set(ANDROID_ARM_NEON   ON  CACHE BOOL "" FORCE)

    # ggml-cpu defaults to baseline armv8-a when cross-compiling (GGML_NATIVE is
    # OFF), which makes Q8_0 matmuls fall back to the slow vmull_s8 path and
    # compiles zero KleidiAI matmul kernels (they are gated on +dotprod/+i8mm
    # in ARCH_FLAGS). Pin the architecture so dotprod + fp16 kernels and the
    # KleidiAI chain are built. The bridge target already assumes
    # -march=armv8.2-a+dotprod+fp16 (see top-level CMakeLists.txt), so this
    # introduces no new device constraint.
    set(GGML_CPU_KLEIDIAI  ON              CACHE BOOL   "" FORCE)
    set(GGML_CPU_ARM_ARCH  "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(USE_SSE2           ON  CACHE BOOL "" FORCE)
    set(GGML_SSE42         1   CACHE STRING "" FORCE)
    # Cross-compiling disables GGML_NATIVE, so AVX2/FMA/F16C default off and
    # x86_64 would run SSE4.2 kernels. All realistic x86_64 Android targets
    # (devices + emulators) support AVX2/FMA/F16C.
    set(GGML_AVX2          ON  CACHE BOOL "" FORCE)
    set(GGML_FMA           ON  CACHE BOOL "" FORCE)
    set(GGML_F16C          ON  CACHE BOOL "" FORCE)
elseif(ANDROID_ABI MATCHES "^x86")
    set(USE_SSE2           ON  CACHE BOOL "" FORCE)
    set(GGML_SSE42         1   CACHE STRING "" FORCE)
endif()

add_subdirectory(espeak-ng espeak-ng)
add_subdirectory(neon-sse neon-sse)
add_subdirectory(open-jtalk/src open-jtalk)
add_subdirectory(onnxruntime onnxruntime)

# MNN (official Alibaba inference engine) powers the OCR pipeline. Static build:
# the bridge is a single shared library and there is no reason to ship a second
# .so. Converter/tools/train stay off — models are converted offline in
# Versta.Models and this device-side copy only runs inference. Protobuf is only
# needed by the offline converters, not to load .mnn flatbuffers.
set(MNN_BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD            OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TOOLS          OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_CONVERTER      OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_PROTOBUFFER    OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TRAIN          OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DEMO           OFF CACHE BOOL "" FORCE)
set(MNN_EVALUATION           OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_QUANTOOLS      OFF CACHE BOOL "" FORCE)
set(MNN_SUPPORT_DEPRECATED_OP OFF CACHE BOOL "" FORCE)
set(MNN_JNI                  OFF CACHE BOOL "" FORCE)
# 32-bit x86: MNN's SSE path emits 64-bit AVX objects that fail the elf_i386 link.
if(ANDROID_ABI STREQUAL "x86")
    set(MNN_USE_SSE          OFF CACHE BOOL "" FORCE)
endif()
add_subdirectory(mnn mnn)

add_subdirectory(leanmt leanmt)

target_include_directories(leanmt PUBLIC
        ${CMAKE_CURRENT_BINARY_DIR}/leanmt/src
        ${CMAKE_CURRENT_SOURCE_DIR}/leanmt/src)

include(whisper.cpp.cmake)
