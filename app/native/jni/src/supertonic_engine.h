// supertonic_engine.h — Supertonic 3 four-model TTS pipeline on MNN (CPU).
//
// Pure C++17, no JNI: this header is also compiled by the host-side native
// tests (app/native/tests), so keep Android/JNI types out of it. The JNI
// surface lives in supertonic.cc; the Kotlin bridge in
// bridge/speech/Supertonic3.kt.
//
// Pipeline per synthesize() call (mirrors the reference vra/supertonic-mnn
// engine.py semantics, one sentence chunk per call):
//
//   duration_predictor(text_ids, style_dp, text_mask)   -> total seconds [1]
//   text_encoder(text_ids, style_ttl, text_mask)        -> text_emb
//   xt = mt19937-seeded gaussian [1, latent_dim*compress, latent_len]
//   vector_estimator(xt, text_emb, style_ttl, masks, step, total_step) x steps
//   vocoder(xt)                                          -> PCM f32 @ tts.json ae.sample_rate
//
// Seam decisions locked with the app layer (NEU-69 issue thread, kept here so
// the contract has one home):
//  - token ids < 0 (uncovered code points): kept in sequence, normalized as
//    id += vocab_size before the embedding gather, matching ONNX Gather's
//    negative-index wrap in the reference. ids >= vocab_size (only possible
//    via JNI misuse) clamp to vocab_size - 1, logged once per load.
//  - empty token array -> empty audio, never an error.
//  - overlong input (> kMaxTokens) truncates with a warning log; production
//    chunking upstream should make this unreachable.
//  - speed arrives pre-clamped to [0.5, 2.0]; re-clamped defensively, applied
//    as duration /= speed right after the duration predictor.
//  - deterministic by default: the gaussian latent is drawn from a per-call
//    reseeded std::mt19937 (default seed kDefaultSeed, overridable via
//    setSeed() for golden-audio tests). The reference samples unseeded; we
//    deliberately trade sample-variety for reproducible output.
//  - dynamic text length is handled by MNN resize-on-input (exact reference
//    parity; bit-comparable with NEU-68 reference vectors). Shape bucketing
//    is a documented future optimization only — padding changes results at
//    conv boundaries, which would silently diverge from the reference.

#ifndef APP_VERSTA_SUPERTONIC_ENGINE_H
#define APP_VERSTA_SUPERTONIC_ENGINE_H

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace supertonic {

class Error : public std::runtime_error {
public:
    explicit Error(const std::string& msg) : std::runtime_error(msg) {}
};

// Hard cap on tokens per synthesis call. Two inputs set it:
//  - the reference chunker splits at 300 chars (120 for ko/ja), so this is a
//    tripwire for upstream chunking, not a working range;
//  - the exported vector_estimator itself refuses T > 1000: measured
//    empirically (2026-10-15), T=1001 broadcast-fails at
//    vector_field/main_blocks.3/attn (a length-1000 constant is baked into
//    the exported graph), while T=1000 runs. 1024 would let a caller feed
//    exactly the range that crashes the model, so the cap is the model's.
constexpr int kMaxTokens = 1000;
// Default diffusion step count and RNG seed (part of the golden-audio
// contract with the NEU-68 reference vectors).
constexpr int kDefaultSteps = 5;
constexpr uint32_t kDefaultSeed = 1234;
// Embedding rows of the current export (unicode_indexer max id 8320 + 1,
// dense). tts.json may override with an explicit "vocab_size" once the
// model-format spec adds it; until then this constant is the contract.
constexpr int kDefaultVocabSize = 8321;

struct Config {
    int sampleRate = 44100;
    int baseChunkSize = 512;
    int latentDim = 24;
    int chunkCompress = 6;
    int vocabSize = kDefaultVocabSize;

    int chunkSamples() const { return baseChunkSize * chunkCompress; }
    int latentWidth() const { return latentDim * chunkCompress; }
};

// Parsed voice_style file: two row-major float vectors with validated shapes.
struct Style {
    std::vector<float> ttl;  // shape styleTtlDims()
    std::vector<float> dp;   // shape styleDpDims()
    std::vector<int> ttlDims;
    std::vector<int> dpDims;
};

class Engine {
public:
    ~Engine();

    Engine(const Engine&) = delete;
    Engine& operator=(const Engine&) = delete;

    // Loads the four MNN models and the tts.json config. modelPaths must
    // contain exactly the keys of Supertonic3.MODEL_STAGES:
    //   duration_predictor, text_encoder, vector_estimator, vocoder
    // Throws Error on any load/validation failure. threads applies to all
    // four runtimes; changing threads later means reloading.
    static std::unique_ptr<Engine> load(
        const std::string& durationPredictorPath,
        const std::string& textEncoderPath,
        const std::string& vectorEstimatorPath,
        const std::string& vocoderPath,
        const std::string& configJson,
        int threads);

    // Parses the tts.json contract (ae.sample_rate, ae.base_chunk_size,
    // ttl.latent_dim, ttl.chunk_compress_factor; optional vocab_size).
    // Throws Error on missing/malformed keys — a format incompatibility fails
    // at load, never mid-synthesis. Static for unit tests.
    static Config parseConfig(const std::string& configJson);

    int sampleRate() const;

    // Default synthesis settings come from construction; these four are the
    // test/pipeline knobs, all safe to call between synthesize() calls.
    void setSeed(uint32_t seed);
    void setSteps(int steps);

    // Binds a style for subsequent synthesize() calls. Throws Error if the
    // file is unreadable or its dims do not match the loaded models' style
    // inputs (previous style stays active on failure). load() starts with no
    // style bound; synthesize() without a style throws.
    void setStyle(const Style& style);
    void clearStyle();

    // Parses a voice_style JSON document ({style_ttl:{dims,data}, style_dp:
    // {dims,data}}; nested data arrays flattened row-major) and validates the
    // dims against the loaded models. Exposed so the JNI layer can hand the
    // file bytes over without the engine doing file I/O beyond models.
    Style parseStyle(const std::string& json) const;

    // One sentence chunk -> PCM f32 @ sampleRate(). Never throws for ordinary
    // garbage input (empty/clamped/truncated paths degrade gracefully); throws
    // Error only for engine-state problems (no style bound, session failure).
    // Serialized internally: concurrent calls are safe and run one at a time.
    // The initial latent is drawn from the seeded RNG (see setSeed).
    std::vector<float> synthesize(const std::vector<int64_t>& tokens, float speed);

    // Golden-audio seam (NEU-68 delta 2): synthesize with a caller-supplied
    // initial latent instead of drawing one from the RNG. The conversion-side
    // verification harness injects `noisy_latent` (never unseeded np.random),
    // so a reference vector must carry that exact tensor for the diffusion
    // path to be comparable across implementations. Dims are validated
    // against the ones the duration predictor implies; a mismatch throws.
    // Row-major [1, latentWidth(), latentLen]. Not part of the JNI surface.
    std::vector<float> synthesizeWithLatent(const std::vector<int64_t>& tokens,
                                            float speed,
                                            const std::vector<float>& latent);

    // Token-id normalization, extracted from synthesize() so the input path
    // can be fuzzed without loading models (see app/native/tests):
    //  - truncates at kMaxTokens
    //  - ids < 0 wrap as id += vocabSize (ONNX Gather semantics), saturating
    //    at 0 for inputs below -vocabSize
    //  - ids >= vocabSize clamp to vocabSize - 1; clampedOut (when given) is
    //    set so the caller can log once per load
    static std::vector<int32_t> normalizeTokens(const std::vector<int64_t>& tokens,
                                                int vocabSize,
                                                bool* clampedOut = nullptr);

private:
    struct Impl;
    explicit Engine(std::unique_ptr<Impl> impl);
    std::unique_ptr<Impl> impl_;

    // Shared pipeline; injectedLatent == nullptr draws from the seeded RNG.
    std::vector<float> synthesizeImpl(const std::vector<int64_t>& tokens,
                                      float speed,
                                      const std::vector<float>* injectedLatent);
};

}  // namespace supertonic

#endif  // APP_VERSTA_SUPERTONIC_ENGINE_H
