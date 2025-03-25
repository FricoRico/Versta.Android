//
// Created by Ricardo Snoek on 14/03/2025.
//
#include <jni.h>
#include <string>

#include "onnxruntime/onnxruntime_c_api.h"

typedef struct {
    size_t dimensions;
    size_t elementCount;
    ONNXTensorElementDataType onnxTypeEnum;
} TensorShape;

void checkTensorStatus(JNIEnv *env, const OrtApi *api, OrtStatus *status) {
    if (status == nullptr) {
        return;
    }

    const char *message = api->GetErrorMessage(status);
    size_t len = strlen(message) + 1;
    std::string errorMessage(message, len);
    api->ReleaseStatus(status);

    jclass exception = env->FindClass("java/lang/Exception");
    if (exception != nullptr) {
        env->ThrowNew(exception, message);
    }
}

void getTensorShape(JNIEnv *env, TensorShape *output, const OrtApi *api,
                    const OrtValue *value) {
    OrtTensorTypeAndShapeInfo *info;

    try {
        checkTensorStatus(env, api, api->GetTensorTypeAndShape(value, &info));
        checkTensorStatus(env, api, api->GetDimensionsCount(info, &output->dimensions));
        checkTensorStatus(env, api, api->GetTensorShapeElementCount(info, &output->elementCount));
        checkTensorStatus(env, api, api->GetTensorElementType(info, &output->onnxTypeEnum));
    } catch (...) {
        api->ReleaseTensorTypeAndShapeInfo(info);
        throw;
    }

    api->ReleaseTensorTypeAndShapeInfo(info);
}

size_t getTensorSize(ONNXTensorElementDataType type) {
    switch (type) {
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT8:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT8:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_BOOL:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT8E4M3FN:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT8E4M3FNUZ:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT8E5M2:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT8E5M2FNUZ:
            return 1;
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT16:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT16:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_BFLOAT16:
            return 2;
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT32:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT32:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT:
            return 4;
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_UINT64:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_DOUBLE:
            return 8;
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_STRING:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_UNDEFINED:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_COMPLEX64:
        case ONNX_TENSOR_ELEMENT_DATA_TYPE_COMPLEX128:
        default:
            return 0;
    }
}

