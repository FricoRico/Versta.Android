# whisper.cpp (ggml-org) — speech recognition core library.
#
# Adds the vendored whisper.cpp as a subdirectory. By default (when consumed via
# add_subdirectory from our top-level, i.e. not the standalone project) its
# examples, tests and server are disabled, so we only get the `whisper` and
# `ggml` targets. We force those options off to be explicit and avoid pulling
# in CLI binaries, the HTTP server or the bundled model downloader.

if(TARGET whisper)
    return()
endif()

set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)

# Static build for all ABIs. No dynamic backend loading.
set(BUILD_SHARED_LIBS     OFF CACHE BOOL "" FORCE)

set(GGML_LTO              ON  CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper.cpp whisper.cpp)

# Apply optimisation flags only to the whisper/ggml targets so they don't
# leak into sibling libraries (leanmt, espeak-ng, etc.).
foreach(tgt whisper ggml)
    if(TARGET ${tgt})
        target_compile_options(${tgt} PRIVATE -O3)
        target_compile_definitions(${tgt} PRIVATE NDEBUG)
    endif()
endforeach()
