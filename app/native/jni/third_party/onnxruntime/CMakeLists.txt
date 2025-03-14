cmake_minimum_required(VERSION 3.22.1)

project(onnxruntime)

set(ONNXRUNTIME_SRCS
        onnxruntime_c_api.h)

add_library(onnxruntime INTERFACE ${ONNXRUNTIME_SRCS})