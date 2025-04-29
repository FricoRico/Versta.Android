cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED True)

project(app_versta_translate_bridge)

set(SRC_DIR "${CMAKE_SOURCE_DIR}/src")
set(THIRD_PARTY_DIR "${CMAKE_SOURCE_DIR}/third_party")

add_subdirectory(src)
add_subdirectory(third_party)

add_library(${CMAKE_PROJECT_NAME} SHARED ${SRC_FILES})

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        $<BUILD_INTERFACE:${SRC_DIR}>
        $<BUILD_INTERFACE:${THIRD_PARTY_DIR}>
)

target_link_libraries(
        ${CMAKE_PROJECT_NAME}
        log
        android
        ucd
        cld2
        espeak-ng
        sentencepiece
)