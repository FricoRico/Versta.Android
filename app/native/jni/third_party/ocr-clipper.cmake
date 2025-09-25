if(TARGET ocr-clipper)
    return()
endif()

# Collect all source files
set(OCR_CLIPPER_SOURCES
        ocr-clipper/ocr_clipper.hpp
        ocr-clipper/ocr_clipper.cpp
)

# Create shared library
add_library(ocr-clipper STATIC ${OCR_CLIPPER_SOURCES})

# Set properties for the library
set_target_properties(ocr-clipper PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        SOVERSION 1
        VERSION 1.0.0
)

target_compile_options(ocr-clipper PRIVATE
        -Wno-narrowing
)

# Add include directories
target_include_directories(ocr-clipper
        PUBLIC
        ocr-clipper
)
