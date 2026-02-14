cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)

project(app_versta_translate_bridge)

set(SRC_DIR "${CMAKE_SOURCE_DIR}/src")
set(THIRD_PARTY_DIR "${CMAKE_SOURCE_DIR}/third_party")

set(OpenCV_DIR ${THIRD_PARTY_DIR}/opencv-mobile/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

add_subdirectory(src)
add_subdirectory(third_party)

add_library(${CMAKE_PROJECT_NAME} SHARED ${SRC_FILES})

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        $<BUILD_INTERFACE:${SRC_DIR}>
        $<BUILD_INTERFACE:${THIRD_PARTY_DIR}>
        $<BUILD_INTERFACE:${THIRD_PARTY_DIR}/litert>
        $<BUILD_INTERFACE:${CMAKE_BINARY_DIR}/include>
        $<BUILD_INTERFACE:${CMAKE_BINARY_DIR}/_deps/abseil-cpp-src>
        ${OpenCV_INCLUDE_DIRS}
)

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        LITERT_DISABLE_OPENCL_SUPPORT
)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")

target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        log
        android
        ucd
        cld2
        ocr-clipper
        espeak-ng
        sentencepiece
        openjtalk
        litert_cc_api
        absl::status
        absl::statusor
        absl::span
        absl::strings
        absl::log
        absl::log_internal_check_op
        absl::base
        absl::raw_hash_set
        flatbuffers::flatbuffers
        ${OpenCV_LIBS}
)
