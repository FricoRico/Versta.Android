# Host-side native tests for the Supertonic 3 engine (no JVM, no device).
#
# Builds the pure-C++ engine (supertonic_engine.cc) against a host MNN build
# from the vendored submodule and runs:
#   1. tinyjson unit tests
#   2. tts.json config parsing (happy path + fail-fast on missing keys)
#   3. token normalization fuzz (random int64, empty, truncation, wrap/clamp)
#   4. when SUPERTONIC_TEST_MODELS points at a model dir: full 4-stage
#      pipeline — determinism (two runs bit-identical), style validation,
#      empty input, and RMS/shape sanity of the PCM output.
#
# The model-dependent tests are skipped (exit 0, loud SKIP line) unless the
# env var is set, so CI can run the unit tier everywhere and the golden tier
# once the NEU-68 reference vectors land.
#
# Usage:
#   cmake -S app/native/tests -B build/native-tests -DCMAKE_BUILD_TYPE=Release
#   cmake --build build/native-tests -j
#   ./build/native-tests/supertonic_test                          # unit tier
#   SUPERTONIC_TEST_MODELS=<dir> ./build/native-tests/supertonic_test

cmake_minimum_required(VERSION 3.22.1)
project(supertonic_host_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

set(JNI_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jni)

# Host MNN: same CPU-only option matrix as third_party/mnn.cmake; static so
# the test binary runs without rpath juggling.
set(MNN_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD         OFF CACHE BOOL "" FORCE)
set(MNN_USE_THREAD_POOL   ON  CACHE BOOL "" FORCE)
# Host build: no <android/log.h>. MNN defaults this ON.
set(MNN_USE_LOGCAT        OFF CACHE BOOL "" FORCE)
set(MNN_JNI               OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TOOLS       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_BENCHMARK   OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TEST        OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DEMO        OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TRAIN       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_CONVERTER   OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_OPENCV      OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_AUDIO       OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_LLM         OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_DIFFUSION   OFF CACHE BOOL "" FORCE)
set(MNN_EVALUATION        OFF CACHE BOOL "" FORCE)
set(MNN_VULKAN            OFF CACHE BOOL "" FORCE)
set(MNN_OPENCL            OFF CACHE BOOL "" FORCE)
set(MNN_OPENGL            OFF CACHE BOOL "" FORCE)

add_subdirectory(${JNI_DIR}/third_party/mnn mnn)

add_executable(supertonic_test
    test_supertonic.cc
    ${JNI_DIR}/src/supertonic_engine.cc
)

target_include_directories(supertonic_test PRIVATE
    ${JNI_DIR}/src
    ${JNI_DIR}/src/include
    ${JNI_DIR}/third_party/mnn/include
)

target_link_libraries(supertonic_test PRIVATE MNN)

enable_testing()
add_test(NAME supertonic COMMAND supertonic_test)
