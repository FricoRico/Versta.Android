set(USE_MBROLA OFF CACHE BOOL "" FORCE)
set(USE_ASYNC OFF CACHE BOOL "" FORCE)

include(cld2.cmake)

add_subdirectory(espeak-ng espeak-ng)
add_subdirectory(open-jtalk/src open-jtalk)
add_subdirectory(onnxruntime onnxruntime)
add_subdirectory(sentencepiece sentencepiece)
