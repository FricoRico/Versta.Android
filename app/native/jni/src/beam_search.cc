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

#include "ort_utils.cc"

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
        newBeams.reserve(beamSize * 128);

        for (size_t i = 0; i < beams.size(); ++i) {
            jfloat *beamLogitsPtr = tensorLogits + i * size;

            const int topK = std::min(256, size);
            std::vector<int> topIndices;
            topIndices.reserve(topK);

            for (int j = 0; j < size; ++j) {
                if (beamLogitsPtr[j] > minP) {
                    topIndices.push_back(j);
                }
            }

            std::sort(topIndices.begin(), topIndices.end(),
                      [beamLogitsPtr](int a, int b) {
                          return beamLogitsPtr[a] > beamLogitsPtr[b];
                      });

            if (topIndices.size() > topK) {
                topIndices.resize(topK);
            }

            for (int token: topIndices) {
                std::vector<int64_t> sequence = beams[i].sequence;
                sequence.push_back(token);

                float logit = beamLogitsPtr[token];
                float score = beams[i].score + logit;
                score = penalizeRepetition(sequence, score, repetitionPenalty);

                newBeams.emplace_back(i, sequence, score);
            }
        }

        std::sort(newBeams.begin(), newBeams.end(), [](const Beam &a, const Beam &b) {
            return a.score > b.score;
        });

        beams.clear();
        beams.reserve(beamSize);
        for (size_t i = 0; i < std::min(static_cast<size_t>(beamSize), newBeams.size()); ++i) {
            beams.push_back(std::move(newBeams[i]));
        }
    }

    [[nodiscard]] std::vector<std::vector<int64_t>> getLastTokens() const {
        std::vector<std::vector<int64_t>> tokens;
        for (const auto &beam: beams) {
            tokens.push_back({beam.sequence.back()});
        }
        return tokens;
    }

    [[nodiscard]] bool complete(bool completeOnRepeat) const {
        if (beams.empty()) {
            return false;
        }

        if (std::find(beams.front().sequence.begin(), beams.front().sequence.end(), eosId) !=
            beams.front().sequence.end()) {
            return true;
        }

        auto topN = static_cast<size_t>(std::ceil(beamSize * 0.75));
        size_t completedBeams = 0;
        for (size_t i = 0; i < std::min(beams.size(), topN); ++i) {
            const auto &sequence = beams[i].sequence;

            if (std::find(sequence.begin(), sequence.end(), eosId) != sequence.end()) {
                completedBeams++;
                continue;
            }

            if (completeOnRepeat) {
                std::unordered_set<int64_t> tokens;

                for (auto it = sequence.rbegin(); it != sequence.rend(); ++it) {
                    if (tokens.find(*it) != tokens.end()) {
                        completedBeams++;
                        break;
                    }

                    tokens.insert(*it);
                }
            }
        }

        return completedBeams >= topN;
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
jlong beamSearchInstanceCounter = 0;

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
    jlong handle = ++beamSearchInstanceCounter;
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
    try {
        checkTensorStatus(env, api, api->GetTensorMutableData(ortValue, (void **) &logits));

        auto beamSearch = beamSearchInstances[handle].get();
        if (!beamSearch) {
            return;
        }

        beamSearch->search(logits, size);
    } catch (...) {
        return;
    }

}

JNIEXPORT jobject JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_lastTokens(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return nullptr;
    }

    std::vector<std::vector<int64_t>> tokens = beamSearch->getLastTokens();
    auto totalTokens = tokens.size();

    size_t bufferSize = totalTokens * sizeof(int64_t);
    auto *buffer = new int64_t[totalTokens];

    for (size_t i = 0; i < tokens.size(); ++i) {
        if (!tokens[i].empty()) {
            buffer[i] = tokens[i][0];
        } else {
            buffer[i] = 0;
        }
    }

    return env->NewDirectByteBuffer(buffer, static_cast<jlong>(bufferSize));
}

JNIEXPORT jboolean JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_complete(
        JNIEnv *env,
        jobject,
        jlong handle,
        jboolean completeOnRepeat
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return JNI_FALSE;
    }
    return beamSearch->complete(completeOnRepeat) ? JNI_TRUE : JNI_FALSE;
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

JNIEXPORT jintArray JNICALL Java_app_versta_translate_bridge_inference_BeamSearch_getBeamIndices(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    auto beamSearch = beamSearchInstances[handle].get();
    if (!beamSearch) {
        return nullptr;
    }

    std::vector<int> indices = beamSearch->getTopBeamIds();
    jintArray result = env->NewIntArray(indices.size());
    env->SetIntArrayRegion(result, 0, indices.size(), indices.data());

    return result;
}

#ifdef __cplusplus
}
#endif
