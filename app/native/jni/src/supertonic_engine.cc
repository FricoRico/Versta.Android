// supertonic_engine.cc — see supertonic_engine.h for the design contract.
//
// Implementation notes:
//  - One MNN Interpreter + Session per stage, all CPU, all with the same
//    thread count. Precision_Low + Memory_Low match the NEU-64 spike
//    configuration that produced the validated RTF/RSS numbers.
//  - Dynamic text length goes through resize-on-input: resize every input
//    tensor, resize the session once, then copy host mirrors in and run.
//  - All public entry points serialize on one engine mutex (seam contract:
//    concurrent synthesize from the dispatcher is serialized, not racy).

#include "supertonic_engine.h"

#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <mutex>
#include <random>

#include "include/tinyjson.h"  // shared headers live in src/include/, spelled include/<h> like Log.h

#ifdef __ANDROID__
#include <android/log.h>
#define ST_LOGW(...) __android_log_print(ANDROID_LOG_WARN, "SupertonicEngine", __VA_ARGS__)
#else
#include <cstdio>
#define ST_LOGW(...) fprintf(stderr, __VA_ARGS__)
#endif

namespace supertonic {

namespace {

struct Net {
    MNN::Interpreter* interp = nullptr;
    MNN::Session* session = nullptr;

    Net(const std::string& path, int threads) {
        interp = MNN::Interpreter::createFromFile(path.c_str());
        if (!interp) {
            throw Error("supertonic: failed to load model: " + path);
        }
        MNN::ScheduleConfig sc;
        sc.type = MNN_FORWARD_CPU;
        sc.numThread = threads;
        MNN::BackendConfig bc;
        bc.precision = MNN::BackendConfig::Precision_Low;
        bc.memory = MNN::BackendConfig::Memory_Low;
        sc.backendConfig = &bc;
        session = interp->createSession(sc);
        if (!session) {
            MNN::Interpreter::destroy(interp);
            interp = nullptr;
            throw Error("supertonic: failed to create session for: " + path);
        }
    }

    ~Net() {
        if (interp) {
            if (session) {
                interp->releaseSession(session);
            }
            MNN::Interpreter::destroy(interp);
        }
    }

    Net(const Net&) = delete;
    Net& operator=(const Net&) = delete;

    void resize(const std::vector<std::pair<std::string, std::vector<int>>>& tensors) {
        for (const auto& item : tensors) {
            auto* t = interp->getSessionInput(session, item.first.c_str());
            if (!t) {
                throw Error("supertonic: model is missing input '" + item.first + "'");
            }
            interp->resizeTensor(t, item.second);
        }
        interp->resizeSession(session);
    }

    // Copies a host buffer into a session input. Call only after resize() —
    // the session tensors are allocated by resizeSession.
    void feed(const std::string& name, const void* data, size_t bytes) {
        auto* t = interp->getSessionInput(session, name.c_str());
        if (!t) {
            throw Error("supertonic: model is missing input '" + name + "'");
        }
        MNN::Tensor host(t, t->getDimensionType(), true);
        // Every tensor this engine feeds is f32 or i32 (4-byte elements).
        // A byte-count mismatch means a contract bug; fail loudly here
        // rather than corrupting session memory.
        if (bytes != static_cast<size_t>(host.elementSize()) * 4) {
            throw Error("supertonic: input size mismatch feeding '" + name + "'");
        }
        memcpy(host.host<void>(), data, bytes);
        t->copyFromHostTensor(&host);
    }

    void run() {
        if (interp->runSession(session) != MNN::NO_ERROR) {
            throw Error("supertonic: session run failed");
        }
    }

    // Declared shape of a model input (from the model file, pre-resize);
    // dims may be -1 where dynamic.
    std::vector<int> inputShape(const std::string& name) const {
        auto* t = interp->getSessionInput(session, name.c_str());
        if (!t) {
            throw Error("supertonic: model is missing input '" + name + "'");
        }
        return t->shape();
    }

    std::vector<float> output(const std::string& name, std::vector<int>* dims) const {
        auto* t = interp->getSessionOutput(session, name.c_str());
        if (!t) {
            throw Error("supertonic: model is missing output '" + name + "'");
        }
        MNN::Tensor host(t, t->getDimensionType(), true);
        t->copyToHostTensor(&host);
        if (dims) {
            *dims = host.shape();
        }
        size_t n = host.elementSize();
        std::vector<float> out(n);
        memcpy(out.data(), host.host<float>(), n * sizeof(float));
        return out;
    }
};

std::vector<int> readDims(const tinyjson::Value& v) {
    std::vector<int> dims;
    for (const auto& d : v.asArray()) {
        dims.push_back(static_cast<int>(d.asLong()));
    }
    return dims;
}

long dimProduct(const std::vector<int>& dims) {
    long n = 1;
    for (int d : dims) {
        n *= d;
    }
    return n;
}

}  // namespace

struct Engine::Impl {
    Config config;
    Net dp;
    Net encoder;
    Net estimator;
    Net vocoder;
    int steps = kDefaultSteps;
    uint32_t seed = kDefaultSeed;
    Style style;
    bool hasStyle = false;
    bool warnedClamp = false;
    // Declared style input shapes from the loaded models; style files must
    // match these exactly (static in every supported export).
    std::vector<int> expectTtlDims;
    std::vector<int> expectDpDims;
    std::mutex mutex;

    Impl(const std::string& dpPath,
         const std::string& encoderPath,
         const std::string& estimatorPath,
         const std::string& vocoderPath,
         int threads)
        : dp(dpPath, threads),
          encoder(encoderPath, threads),
          estimator(estimatorPath, threads),
          vocoder(vocoderPath, threads) {}
};

Engine::Engine(std::unique_ptr<Impl> impl) : impl_(std::move(impl)) {}

Engine::~Engine() = default;

Config Engine::parseConfig(const std::string& configJson) {
    // The public contract is supertonic::Error on any failure; tinyjson::Error
    // is an implementation detail and must not escape the engine boundary.
    try {
        auto doc = tinyjson::parse(configJson);
        Config config;
        config.sampleRate = static_cast<int>(doc.at("ae").at("sample_rate").asLong());
        config.baseChunkSize = static_cast<int>(doc.at("ae").at("base_chunk_size").asLong());
        config.latentDim = static_cast<int>(doc.at("ttl").at("latent_dim").asLong());
        config.chunkCompress = static_cast<int>(doc.at("ttl").at("chunk_compress_factor").asLong());
        if (const auto* vocab = doc.find("vocab_size")) {
            config.vocabSize = static_cast<int>(vocab->asLong());
        }
        if (config.sampleRate <= 0 || config.baseChunkSize <= 0 || config.latentDim <= 0 ||
            config.chunkCompress <= 0) {
            throw Error("supertonic: tts.json values must be positive");
        }
        return config;
    } catch (const tinyjson::Error& e) {
        throw Error(std::string("supertonic: invalid tts.json: ") + e.what());
    }
}

std::unique_ptr<Engine> Engine::load(const std::string& durationPredictorPath,
                                     const std::string& textEncoderPath,
                                     const std::string& vectorEstimatorPath,
                                     const std::string& vocoderPath,
                                     const std::string& configJson,
                                     int threads) {
    const Config config = parseConfig(configJson);
    if (threads < 1) {
        threads = 1;
    }

    auto impl = std::make_unique<Impl>(durationPredictorPath,
                                       textEncoderPath,
                                       vectorEstimatorPath,
                                       vocoderPath,
                                       threads);
    impl->config = config;
    impl->expectTtlDims = impl->encoder.inputShape("style_ttl");
    impl->expectDpDims = impl->dp.inputShape("style_dp");
    return std::unique_ptr<Engine>(new Engine(std::move(impl)));
}

int Engine::sampleRate() const {
    return impl_->config.sampleRate;
}

void Engine::setSeed(uint32_t seed) {
    std::lock_guard<std::mutex> lock(impl_->mutex);
    impl_->seed = seed;
}

void Engine::setSteps(int steps) {
    if (steps < 1) {
        throw Error("supertonic: steps must be >= 1");
    }
    std::lock_guard<std::mutex> lock(impl_->mutex);
    impl_->steps = steps;
}

Style Engine::parseStyle(const std::string& json) const {
    try {
        auto doc = tinyjson::parse(json);
        Style style;
        style.ttlDims = readDims(doc.at("style_ttl").at("dims"));
        style.dpDims = readDims(doc.at("style_dp").at("dims"));
        style.ttl = doc.at("style_ttl").at("data").flattenFloats();
        style.dp = doc.at("style_dp").at("data").flattenFloats();
        if (static_cast<long>(style.ttl.size()) != dimProduct(style.ttlDims)) {
            throw Error("supertonic: style_ttl data size does not match dims");
        }
        if (static_cast<long>(style.dp.size()) != dimProduct(style.dpDims)) {
            throw Error("supertonic: style_dp data size does not match dims");
        }
        // Batch is fixed at 1 (single-speaker contract) and the leading dims
        // must match the models' declared style input shapes.
        if (style.ttlDims.empty() || style.ttlDims[0] != 1 || style.dpDims.empty() || style.dpDims[0] != 1) {
            throw Error("supertonic: style dims must have batch 1");
        }
        if (style.ttlDims.size() != 3 || style.dpDims.size() != 3) {
            throw Error("supertonic: style dims must be rank 3 ([1, *, *])");
        }
        // Contract check against the loaded models (seam: "validates both
        // vectors (dims vs config)"). A style from another model rev fails
        // here, while the caller still has the previous style active.
        if (style.ttlDims != impl_->expectTtlDims || style.dpDims != impl_->expectDpDims) {
            throw Error("supertonic: style dims do not match the loaded models");
        }
        return style;
    } catch (const tinyjson::Error& e) {
        throw Error(std::string("supertonic: invalid voice style: ") + e.what());
    }
}

void Engine::setStyle(const Style& style) {
    std::lock_guard<std::mutex> lock(impl_->mutex);
    // Atomic swap: on exception the previous style stays active (the caller
    // already validated via parseStyle, so this is just an assignment).
    impl_->style = style;
    impl_->hasStyle = true;
}

void Engine::clearStyle() {
    std::lock_guard<std::mutex> lock(impl_->mutex);
    impl_->hasStyle = false;
    impl_->style = Style{};
}

std::vector<int32_t> Engine::normalizeTokens(const std::vector<int64_t>& tokens,
                                             int vocabSize,
                                             bool* clampedOut) {
    size_t count = tokens.size();
    if (count > static_cast<size_t>(kMaxTokens)) {
        ST_LOGW("supertonic: truncating %zu tokens to %d (upstream chunker tripwire)",
                count, kMaxTokens);
        count = static_cast<size_t>(kMaxTokens);
    }
    bool clamped = false;
    std::vector<int32_t> ids(count);
    for (size_t i = 0; i < count; ++i) {
        int64_t id = tokens[i];
        if (id < 0) {
            // ONNX Gather wraps negative indices; replicate exactly.
            id += vocabSize;
            if (id < 0) {
                id = 0;
                clamped = true;
            }
        } else if (id >= vocabSize) {
            id = vocabSize - 1;
            clamped = true;
        }
        ids[i] = static_cast<int32_t>(id);
    }
    if (clampedOut) {
        *clampedOut = clamped;
    }
    return ids;
}

std::vector<float> Engine::synthesize(const std::vector<int64_t>& tokens, float speed) {
    return synthesizeImpl(tokens, speed, nullptr);
}

std::vector<float> Engine::synthesizeWithLatent(const std::vector<int64_t>& tokens,
                                                float speed,
                                                const std::vector<float>& latent) {
    return synthesizeImpl(tokens, speed, &latent);
}

std::vector<float> Engine::synthesizeImpl(const std::vector<int64_t>& tokens,
                                          float speed,
                                          const std::vector<float>* injectedLatent) {
    std::lock_guard<std::mutex> lock(impl_->mutex);
    Impl& s = *impl_;

    if (tokens.empty()) {
        return {};
    }
    if (!s.hasStyle) {
        throw Error("supertonic: no voice style bound (call setStyle first)");
    }

    bool clamped = false;
    std::vector<int32_t> ids = normalizeTokens(tokens, s.config.vocabSize, &clamped);
    if (clamped && !s.warnedClamp) {
        s.warnedClamp = true;
        ST_LOGW("supertonic: out-of-vocab token id clamped (reported once per load)");
    }
    const int T = static_cast<int>(ids.size());

    if (!(speed >= 0.5f && speed <= 2.0f)) {
        float clamped = std::min(2.0f, std::max(0.5f, speed));
        ST_LOGW("supertonic: speed %.3f out of [0.5, 2.0], clamped to %.3f", speed, clamped);
        speed = clamped;
    }

    std::vector<float> textMask(static_cast<size_t>(T), 1.0f);
    const std::vector<int> idsDims{1, T};
    const std::vector<int> maskDims{1, 1, T};

    // --- duration predictor ---
    float durationSec;
    {
        s.dp.resize({{"text_ids", idsDims},
                     {"style_dp", s.style.dpDims},
                     {"text_mask", maskDims}});
        s.dp.feed("text_ids", ids.data(), ids.size() * sizeof(int32_t));
        s.dp.feed("style_dp", s.style.dp.data(), s.style.dp.size() * sizeof(float));
        s.dp.feed("text_mask", textMask.data(), textMask.size() * sizeof(float));
        s.dp.run();
        auto dur = s.dp.output("duration", nullptr);
        if (dur.empty() || !std::isfinite(dur[0]) || dur[0] <= 0.0f || dur[0] > 120.0f) {
            throw Error("supertonic: duration predictor returned an implausible value");
        }
        durationSec = dur[0] / speed;
    }

    // --- text encoder ---
    std::vector<float> textEmb;
    std::vector<int> embDims;
    {
        s.encoder.resize({{"text_ids", idsDims},
                          {"style_ttl", s.style.ttlDims},
                          {"text_mask", maskDims}});
        s.encoder.feed("text_ids", ids.data(), ids.size() * sizeof(int32_t));
        s.encoder.feed("style_ttl", s.style.ttl.data(), s.style.ttl.size() * sizeof(float));
        s.encoder.feed("text_mask", textMask.data(), textMask.size() * sizeof(float));
        s.encoder.run();
        textEmb = s.encoder.output("text_emb", &embDims);
    }

    // --- latent sampling (seeded; per-call reseed keeps calls reproducible) ---
    const long wavLen = static_cast<long>(durationSec * s.config.sampleRate);
    const int chunk = s.config.chunkSamples();
    const int latentLen = static_cast<int>((wavLen + chunk - 1) / chunk);
    const int L = s.config.latentWidth();
    if (latentLen < 1 || latentLen > 10 * 1024) {
        throw Error("supertonic: implausible latent length from duration predictor");
    }

    std::vector<float> xt;
    if (injectedLatent != nullptr) {
        const size_t want = static_cast<size_t>(L) * latentLen;
        if (injectedLatent->size() != want) {
            throw Error("supertonic: injected latent size does not match the predicted duration");
        }
        xt = *injectedLatent;
    } else {
        std::mt19937 rng(s.seed);
        std::normal_distribution<float> gauss(0.0f, 1.0f);
        xt.resize(static_cast<size_t>(L) * latentLen);
        for (auto& v : xt) {
            v = gauss(rng);
        }
    }
    // bsz=1 latent mask is all-ones by construction (ceil covers wav_len);
    // the reference multiplies noise by it, which is the identity here.
    std::vector<float> latentMask(static_cast<size_t>(latentLen), 1.0f);
    const std::vector<int> latentDims{1, L, latentLen};
    const std::vector<int> latentMaskDims{1, 1, latentLen};
    const float totalStep = static_cast<float>(s.steps);

    // --- diffusion loop ---
    for (int step = 0; step < s.steps; ++step) {
        const float currentStep = static_cast<float>(step);
        s.estimator.resize({{"noisy_latent", latentDims},
                            {"text_emb", embDims},
                            {"style_ttl", s.style.ttlDims},
                            {"latent_mask", latentMaskDims},
                            {"text_mask", maskDims},
                            {"current_step", {1}},
                            {"total_step", {1}}});
        s.estimator.feed("noisy_latent", xt.data(), xt.size() * sizeof(float));
        s.estimator.feed("text_emb", textEmb.data(), textEmb.size() * sizeof(float));
        s.estimator.feed("style_ttl", s.style.ttl.data(), s.style.ttl.size() * sizeof(float));
        s.estimator.feed("latent_mask", latentMask.data(), latentMask.size() * sizeof(float));
        s.estimator.feed("text_mask", textMask.data(), textMask.size() * sizeof(float));
        s.estimator.feed("current_step", &currentStep, sizeof(float));
        s.estimator.feed("total_step", &totalStep, sizeof(float));
        s.estimator.run();
        xt = s.estimator.output("denoised_latent", nullptr);
    }

    // --- vocoder ---
    s.vocoder.resize({{"latent", latentDims}});
    s.vocoder.feed("latent", xt.data(), xt.size() * sizeof(float));
    s.vocoder.run();
    // Matches the reference: output length is latent_len * chunk samples;
    // no tail truncation (masked tail decays to silence in-model).
    return s.vocoder.output("wav_tts", nullptr);
}

}  // namespace supertonic
