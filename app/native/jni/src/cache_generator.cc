//
// Created by Ricardo Snoek on 18/02/2026.
//

#include <jni.h>
#include <string>
#include <map>
#include <memory>
#include <cstring>
#include <vector>

#include "ort_utils.cc"

struct DecoderCacheContext {
    int numLayers;
    int numHeads;
    int headDim;
    int beamSize;
    OrtMemoryInfo *memoryInfo;
};

std::unordered_map<jlong, std::unique_ptr<DecoderCacheContext>> decoderCacheInstances;
jlong decoderCacheInstanceCounter = 0;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_inference_DecoderCache_nativeCreate(
        JNIEnv *env,
        jobject,
        jlong ortApiHandle,
        jint numLayers,
        jint numHeads,
        jint headDim,
        jint beamSize
) {
    const auto *api = (const OrtApi *) ortApiHandle;

    auto ctx = std::make_unique<DecoderCacheContext>();
    ctx->numLayers = numLayers;
    ctx->numHeads = numHeads;
    ctx->headDim = headDim;
    ctx->beamSize = beamSize;

    OrtMemoryInfo *memoryInfo = nullptr;
    OrtStatus *status = api->CreateCpuMemoryInfo(OrtArenaAllocator, OrtMemTypeDefault, &memoryInfo);
    if (status != nullptr) {
        checkTensorStatus(env, api, status);
        return 0L;
    }

    ctx->memoryInfo = memoryInfo;

    jlong handle = ++decoderCacheInstanceCounter;
    decoderCacheInstances[handle] = std::move(ctx);

    return handle;
}

JNIEXPORT jobject JNICALL
Java_app_versta_translate_bridge_inference_DecoderCache_nativeGetCache(
        JNIEnv *env,
        jobject,
        jlong handle,
        jlong ortApiHandle
) {
    auto ctx = decoderCacheInstances[handle].get();
    if (!ctx) {
        return nullptr;
    }

    const auto *api = (const OrtApi *) ortApiHandle;

    jclass hashMapClass = env->FindClass("java/util/HashMap");
    jmethod hashMapConstructor = env->GetMethodID(hashMapClass, "<init>", "()V");
    jmethod hashMapPut = env->GetMethodID(hashMapClass, "put",
                                          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");

    jobject resultMap = env->NewObject(hashMapClass, hashMapConstructor);

    for (int layer = 0; layer < ctx->numLayers; layer++) {
        for (const char *type: {"key", "value"}) {
            char name[64];
            snprintf(name, sizeof(name), "past_key_values.%d.%s", layer, type);

            int64_t shape[4] = {ctx->beamSize, ctx->numHeads, 0, ctx->headDim};
            size_t shapeSize = 4;

            size_t elementCount = 0;
            for (size_t i = 0; i < shapeSize; i++) {
                elementCount *= shape[i];
            }

            uint8_t *emptyData = nullptr;
            size_t dataSize = 0;

            OrtValue *tensor = nullptr;
            OrtStatus *status = api->CreateTensorWithDataAsOrtValue(
                    ctx->memoryInfo,
                    emptyData,
                    dataSize,
                    shape,
                    shapeSize,
                    ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT,
                    &tensor
            );

            if (status != nullptr) {
                checkTensorStatus(env, api, status);
                continue;
            }

            jobject byteBuffer = env->NewDirectByteBuffer(emptyData, dataSize);

            jstring keyStr = env->NewStringUTF(name);
            env->CallObjectMethod(resultMap, hashMapPut, keyStr, byteBuffer);

            env->DeleteLocalRef(keyStr);
            env->DeleteLocalRef(byteBuffer);

            if (tensor != nullptr) {
                api->ReleaseValue(tensor);
            }
        }
    }

    return resultMap;
}

JNIEXPORT jobject JNICALL
Java_app_versta_translate_bridge_inference_DecoderCache_nativeTransposeBuffer(
        JNIEnv *env,
        jobject,
        jlong handle,
        jlong ortApiHandle,
        jlong tensorHandle,
        jintArray beamIndices
) {
    auto ctx = decoderCacheInstances[handle].get();
    if (!ctx) {
        return nullptr;
    }

    const auto *api = (const OrtApi *) ortApiHandle;
    auto *ortValue = (OrtValue *) tensorHandle;
    TensorShape typeShape;

    try {
        getTensorShape(env, &typeShape, api, ortValue);

        size_t tensorSize = getTensorSize(typeShape.onnxTypeEnum);
        size_t sizeBytes = typeShape.elementCount * tensorSize;

        uint8_t *arr = nullptr;
        checkTensorStatus(env, api, api->GetTensorMutableData(ortValue, (void **) &arr));

        jsize indicesLength = env->GetArrayLength(beamIndices);
        jint *indices = env->GetIntArrayElements(beamIndices, nullptr);

        if (indicesLength == 0 || sizeBytes % indicesLength != 0) {
            env->ReleaseIntArrayElements(beamIndices, indices, JNI_ABORT);
            return nullptr;
        }

        auto *transposed = new uint8_t[sizeBytes];

        size_t elementSize = sizeBytes / indicesLength;

#pragma omp parallel for
        for (jsize i = 0; i < indicesLength; ++i) {
            auto oldIndex = indices[i];
            auto newIndex = i;
            std::memcpy(transposed + newIndex * elementSize, arr + oldIndex * elementSize,
                        elementSize);
        }

        env->ReleaseIntArrayElements(beamIndices, indices, JNI_ABORT);

        return env->NewDirectByteBuffer(transposed, (jlong) sizeBytes);
    } catch (...) {
        return nullptr;
    }
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_inference_DecoderCache_nativeClose(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto ctx = decoderCacheInstances[handle].get();
    if (!ctx) {
        return;
    }

    if (ctx->memoryInfo != nullptr) {
        const auto *api = (const OrtApi *) TensorUtils::getOrtApiHandle();
        if (api != nullptr) {
            api->ReleaseMemoryInfo(ctx->memoryInfo);
        }
        ctx->memoryInfo = nullptr;
    }

    decoderCacheInstances.erase(handle);
}

#ifdef __cplusplus
}
#endif
