if(TARGET cld2)
    return()
endif()


# Collect all source files
set(CLD2_SOURCES
        cld2/internal/cldutil.cc
        cld2/internal/cldutil_shared.cc
        cld2/internal/compact_lang_det.cc
        cld2/internal/compact_lang_det_hint_code.cc
        cld2/internal/compact_lang_det_impl.cc
        cld2/internal/debug.cc
        cld2/internal/fixunicodevalue.cc
        cld2/internal/generated_entities.cc
        cld2/internal/generated_language.cc
        cld2/internal/generated_ulscript.cc
        cld2/internal/getonescriptspan.cc
        cld2/internal/lang_script.cc
        cld2/internal/offsetmap.cc
        cld2/internal/scoreonescriptspan.cc
        cld2/internal/tote.cc
        cld2/internal/utf8statetable.cc
        cld2/internal/cld_generated_cjk_uni_prop_80.cc
        cld2/internal/cld2_generated_cjk_compatible.cc
        cld2/internal/cld_generated_cjk_delta_bi_4.cc
        cld2/internal/generated_distinct_bi_0.cc
        cld2/internal/cld2_generated_quadchrome_2.cc
        cld2/internal/cld2_generated_deltaoctachrome.cc
        cld2/internal/cld2_generated_distinctoctachrome.cc
        cld2/internal/cld_generated_score_quad_octa_2.cc
)

# Create shared library
add_library(cld2 STATIC ${CLD2_SOURCES})

# Set properties for the library
set_target_properties(cld2 PROPERTIES
        POSITION_INDEPENDENT_CODE ON
        SOVERSION 1
        VERSION 1.0.0
)

target_compile_options(cld2 PRIVATE
        -Wno-narrowing
)

# Add include directories
target_include_directories(cld2
        PUBLIC
        cld2
        cld2/public
        cld2/internal
)
