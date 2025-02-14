//
// Created by Ricardo Snoek on 16/12/2024.
//

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif
extern "C" JNIEXPORT void JNICALL Java_app_versta_translate_bridge_inference_TensorUtils_closeBuffer(
        JNIEnv *env,
        jobject,
        jobject buffer
) {
    auto *alloc = (uint8_t *) env->GetLongField(buffer, env->GetFieldID(env->FindClass("java/nio/Buffer"), "address", "J"));
    delete[] alloc;
}
#ifdef __cplusplus
}
#endif