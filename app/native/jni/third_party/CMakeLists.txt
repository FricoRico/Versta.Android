set(USE_MBROLA OFF CACHE BOOL "" FORCE)
set(USE_ASYNC OFF CACHE BOOL "" FORCE)

include(cld2.cmake)
include(ocr-clipper.cmake)

add_subdirectory(espeak-ng espeak-ng)
add_subdirectory(neon-sse neon-sse)
add_subdirectory(open-jtalk/src open-jtalk)
add_subdirectory(onnxruntime onnxruntime)
add_subdirectory(sentencepiece sentencepiece)
add_subdirectory(litert litert/build)

include_directories(litert)