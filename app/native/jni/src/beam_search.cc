//
// Created by Ricardo Snoek on 16/12/2024.
//

#include <jni.h>
#include <vector>

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jintArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_topKIndices(
        JNIEnv *env,
        jobject,
        jfloatArray jArray,
        jint k
) {
    jsize length = env->GetArrayLength(jArray);

    std::vector<float> values(length);
    env->GetFloatArrayRegion(jArray, 0, length, values.data());

    std::vector<int> indices(length);
    for (int i = 0; i < length; i++) {
        indices[i] = i;
    }

    auto comparator = [&](int a, int b) {
        return values[a] > values[b];
    };

    if (k > 0 && k <= length) {
        std::nth_element(indices.begin(), indices.begin() + k, indices.end(), comparator);
        std::sort(indices.begin(), indices.begin() + k, comparator);
    } else {
        k = 0;
    }

    jintArray result = env->NewIntArray(k);
    if (k > 0) {
        env->SetIntArrayRegion(result, 0, k, indices.data());
    }

    return result;
}
#ifdef __cplusplus
}
#endif