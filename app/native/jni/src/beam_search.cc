//
// Created by Ricardo Snoek on 16/12/2024.
//

#include <jni.h>
#include <vector>

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jfloatArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_softmax(
        JNIEnv *env,
        jobject,
        jfloatArray logits
) {
    jsize size = env->GetArrayLength(logits);
    if (size == 0) {
        return env->NewFloatArray(0);
    }

    std::vector<float> output(size);
    env->GetFloatArrayRegion(logits, 0, size, output.data());

    float max = *std::max_element(output.begin(), output.end());

    std::vector<float> exps(size);
    for (int i = 0; i < size; i++) {
        exps[i] = std::exp(output[i] - max);
    }

    float sum = 0.0f;
    for (int i = 0; i < size; i++) {
        sum += exps[i];
    }

    for (int i = 0; i < size; i++) {
        exps[i] /= sum;
    }

    jfloatArray probabilities = env->NewFloatArray(size);
    env->SetFloatArrayRegion(probabilities, 0, size, exps.data());

    return probabilities;
}

JNIEXPORT jintArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_topKIndices(
        JNIEnv *env,
        jobject,
        jfloatArray probabilities,
        jint k
) {
    jsize size = env->GetArrayLength(probabilities);

    std::vector<float> values(size);
    env->GetFloatArrayRegion(probabilities, 0, size, values.data());

    std::vector<int> indices(size);
    for (int i = 0; i < size; i++) {
        indices[i] = i;
    }

    auto comparator = [&](int a, int b) {
        return values[a] > values[b];
    };

    if (k > 0 && k <= size) {
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

JNIEXPORT jintArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_minPIndices(
        JNIEnv *env,
        jobject,
        jfloatArray probabilities,
        jfloat p
) {
    jsize size = env->GetArrayLength(probabilities);
    if (size == 0) {
        return env->NewIntArray(0);
    }

    std::vector<float> values(size);
    env->GetFloatArrayRegion(probabilities, 0, size, values.data());

    std::vector<int> indices;
    indices.reserve(size);

    for (int i = 0; i < size; i++) {
        if (values[i] > p) {
            indices.push_back(i);
        }
    }

    jintArray result = env->NewIntArray(static_cast<jsize>(indices.size()));
    if (!indices.empty()) {
        env->SetIntArrayRegion(result, 0, indices.size(), indices.data());
    }

    return result;
}
#ifdef __cplusplus
}
#endif