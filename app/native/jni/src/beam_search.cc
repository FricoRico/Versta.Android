//
// Created by Ricardo Snoek on 16/12/2024.
//

#include <jni.h>
#include <utility>
#include <vector>
#include <algorithm>
#include <cmath>
#include <unordered_set>
#include <memory>
#include <cstdint>
#include <numeric>

#include "OrtJniUtil.h"

struct Beam {
    int id{};
    std::vector<int64_t> sequence;
    float score{};

    Beam(int id, std::vector<int64_t> sequence, float score)
            : id(id), sequence(std::move(sequence)), score(score) {}

    struct HashFunction {
        std::size_t operator()(const Beam &beam) const {
            std::size_t hash = std::accumulate(beam.sequence.begin(), beam.sequence.end(),
                                               std::size_t(0),
                                               [](std::size_t acc, int64_t val) {
                                                   return acc * 31 + std::hash<int64_t>()(val);
                                               });
            hash = hash * 31 + std::hash<float>()(beam.score);
            return hash;
        }
    };

    bool operator==(const Beam &other) const {
        return sequence == other.sequence && score == other.score;
    }
};

class BeamSearch {
public:
    BeamSearch(int beamSize, float minP, float repetitionPenalty, int64_t padId, int64_t eosId)
            : beamSize(beamSize),
              minP(minP),
              repetitionPenalty(repetitionPenalty),
              eosId(eosId) {
        beams.reserve(beamSize);

        for (int i = 0; i < beamSize; ++i) {
            beams.emplace_back(i, std::vector<int64_t>{padId}, -1e-9f);
        }
    }

    void search(jfloat *tensorLogits, int size) {
        std::vector<Beam> newBeams;

        for (size_t i = 0; i < beams.size(); ++i) {
            std::vector<float> beamLogits(tensorLogits + i * size, tensorLogits + (i + 1) * size);

            std::vector<float> probabilities = softmax(beamLogits);
            std::vector<int> indices = minPIndices(probabilities, minP);

#pragma omp parallel for
            for (int token: indices) {
                std::vector<int64_t> sequence = beams[i].sequence;
                sequence.push_back(token);

                float logit = std::max(probabilities[token], -1e-9f);
                float score = beams[i].score + std::log(logit);
                score = penalizeRepetition(sequence, score, repetitionPenalty);

                newBeams.emplace_back(i, sequence, score);
            }
        }

        // Use unordered_set to remove duplicates
        std::unordered_set < Beam, Beam::HashFunction >
                                   uniqueBeams(newBeams.begin(), newBeams.end());

        // Sort beams by score
        std::vector<Beam> sortedBeams(uniqueBeams.begin(), uniqueBeams.end());
        std::sort(sortedBeams.begin(), sortedBeams.end(), [](const Beam &a, const Beam &b) {
            return a.score > b.score;
        });

        // Keep only the top N beams
        beams.assign(sortedBeams.begin(),
                     sortedBeams.begin() + std::min(sortedBeams.size(), beamSize));
    }

    [[nodiscard]] std::vector<std::vector<int64_t>> getLastTokens() const {
        std::vector<std::vector<int64_t>> tokens;
        for (const auto &beam: beams) {
            tokens.push_back({beam.sequence.back()});
        }
        return tokens;
    }

    [[nodiscard]] bool complete() const {
        if (!beams.empty() && beams.front().sequence.back() == eosId) {
            return true;
        }

        auto topN = static_cast<size_t>(std::ceil(beamSize / 2));
        size_t completedBeams = 0;
        for (size_t i = 0; i < std::min(beams.size(), topN); ++i) {
            if (beams[i].sequence.back() == eosId) {
                completedBeams++;
            }
        }

        return completedBeams == topN;
    }

    [[nodiscard]] std::vector<int64_t> best() const {
        if (beams.empty()) return {};
        return beams.front().sequence;
    }

    [[nodiscard]] std::vector<int> getTopBeamIds() const {
        std::vector<int> ids;
        for (size_t i = 0; i < std::min(beams.size(), static_cast<size_t>(beamSize)); ++i) {
            ids.push_back(beams[i].id);
        }
        return ids;
    }

    static std::vector<float> softmax(const std::vector<float> &logits) {
        std::vector<float> output(logits.size());
        float max = *std::max_element(logits.begin(), logits.end());

        std::vector<float> exps(logits.size());
        for (size_t i = 0; i < logits.size(); i++) {
            exps[i] = std::exp(logits[i] - max);
        }

        float sum = 0.0f;
        for (float exp: exps) {
            sum += exp;
        }

        for (float &exp: exps) {
            exp /= sum;
        }

        return exps;
    }

    static std::vector<int> minPIndices(const std::vector<float> &values, float threshold) {
        std::vector<int> indices;
        indices.reserve(values.size());

        for (size_t i = 0; i < values.size(); i++) {
            if (values[i] > threshold) {
                indices.push_back(static_cast<int>(i));
            }
        }

        return indices;
    }

    static float
    penalizeRepetition(const std::vector<int64_t> &sequence, float score, float penalty) {
        std::unordered_map<int64_t, int> wordFreq;
        for (int64_t token: sequence) {
            wordFreq[token]++;
        }

        for (const auto &pair: wordFreq) {
            if (pair.second > 1) {
                score -= penalty * static_cast<float>(pair.second - 1);
            }
        }

        return score;
    }

private:
    std::vector<Beam> beams;
    size_t beamSize;
    float minP;
    float repetitionPenalty;
    uint64_t eosId;
};

std::unordered_map<jlong, std::unique_ptr<BeamSearch>> beamSearchInstances;
jlong instanceCounter = 0;

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_inference_BeamSearch_construct(
        JNIEnv *env,
        jobject,
        jint beamSize,
        jfloat minP,
        jfloat repetitionPenalty,
        jlong padId,
        jlong eosId
) {
    auto beamSearch = std::make_unique<BeamSearch>(beamSize, minP, repetitionPenalty, padId, eosId);
    jlong handle = ++instanceCounter;
    beamSearchInstances[handle] = std::move(beamSearch);
    return handle;
}

JNIEXPORT void JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_search(
        JNIEnv *env,
        jobject,
        jlong handle,
        jlong apiHandle,
        jlong tensorHandle,
        jint size
) {
    const auto *api = (const OrtApi *) apiHandle;
    auto *ortValue = (OrtValue *) tensorHandle;

    jfloat *logits = nullptr;
    OrtErrorCode code = checkOrtStatus(env, api,
                                       api->GetTensorMutableData(ortValue, (void **) &logits));
    if (code != ORT_OK) {
        return;
    }

    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return;
    }

    beamSearch->search(logits, size);
}

JNIEXPORT jobjectArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_lastTokens(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return nullptr;
    }

    std::vector<std::vector<int64_t>> tokens = beamSearch->getLastTokens();
    jobjectArray result = env->NewObjectArray(tokens.size(), env->FindClass("[J"), nullptr);

    for (size_t i = 0; i < tokens.size(); ++i) {
        jlongArray tokenArray = env->NewLongArray(tokens[i].size());
        env->SetLongArrayRegion(tokenArray, 0, tokens[i].size(), tokens[i].data());
        env->SetObjectArrayElement(result, i, tokenArray);
        env->DeleteLocalRef(tokenArray);
    }

    return result;
}

JNIEXPORT jboolean JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_complete(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return JNI_FALSE;
    }
    return beamSearch->complete() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlongArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_best(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return nullptr;
    }

    std::vector<int64_t> bestSequence = beamSearch->best();
    jlongArray result = env->NewLongArray(bestSequence.size());
    env->SetLongArrayRegion(result, 0, bestSequence.size(), bestSequence.data());

    return result;
}

JNIEXPORT jboolean JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_close(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    if (beamSearchInstances.erase(handle) > 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

JNIEXPORT jobject JNICALL
Java_app_versta_translate_bridge_inference_BeamSearch_transposeBuffer(
        JNIEnv *env,
        jobject,
        jlong handle,
        jlong apiHandle,
        jlong tensorHandle
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return nullptr;
    }

    const auto *api = (const OrtApi *) apiHandle;
    auto *ortValue = (OrtValue *) tensorHandle;
    JavaTensorTypeShape typeShape;
    OrtErrorCode code = getTensorTypeShape(env, &typeShape, api, ortValue);

    if (code != ORT_OK) {
        return nullptr;
    }
    size_t typeSize = onnxTypeSize(typeShape.onnxTypeEnum);
    size_t sizeBytes = typeShape.elementCount * typeSize;

    uint8_t *arr = nullptr;
    code = checkOrtStatus(env, api, api->GetTensorMutableData(ortValue, (void **) &arr));

    if (code != ORT_OK) {
        return nullptr;
    }

    std::vector<int> indices = beamSearch->getTopBeamIds();
    auto indicesLength = indices.size();

    auto *transposed = new uint8_t[sizeBytes];

    size_t elementSize = sizeBytes / indicesLength;

#pragma omp parallel for
    for (size_t i = 0; i < indicesLength; ++i) {
        auto oldIndex = indices[i];
        auto newIndex = i;
        std::memcpy(transposed + newIndex * elementSize, arr + oldIndex * elementSize, elementSize);
    }

    return env->NewDirectByteBuffer(transposed, (jlong) sizeBytes);
}
#ifdef __cplusplus
}
#endif