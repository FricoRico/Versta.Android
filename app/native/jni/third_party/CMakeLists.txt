add_subdirectory(${THIRD_PARTY_DIR}/sentencepiece sentencepiece)

include_directories(onnxruntime)

set(THIRD_PARTY_FILES
        ${THIRD_PARTY_DIR}/onnxruntime/OrtJniUtil.c
        PARENT_SCOPE
)
