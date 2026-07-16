include(cld2.cmake)
include(ocr-clipper.cmake)

add_subdirectory(espeak-ng espeak-ng)
add_subdirectory(neon-sse neon-sse)
add_subdirectory(open-jtalk/src open-jtalk)
add_subdirectory(onnxruntime onnxruntime)

add_subdirectory(leanmt leanmt)

target_include_directories(leanmt PUBLIC
        ${CMAKE_CURRENT_BINARY_DIR}/leanmt/src
        ${CMAKE_CURRENT_SOURCE_DIR}/leanmt/src)
