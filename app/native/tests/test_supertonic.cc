// test_supertonic.cc — host-side tests for the Supertonic 3 engine.
//
// Tiers (see app/native/tests/CMakeLists.txt):
//   1. tinyjson unit tests (no models needed)
//   2. tts.json config parsing (happy path + fail-fast)
//   3. normalizeTokens fuzz (wrap/clamp/truncate/empty, deterministic seed)
//   4. full-pipeline model tests, gated on SUPERTONIC_TEST_MODELS pointing
//      at a directory with the four .mnn files + tts.json + voice_styles/
//      (the NEU-68 dist bundle layout)
//   5. reference-vector comparison, gated on SUPERTONIC_TEST_VECTORS
//      (golden PCM emitted by the NEU-68 Python reference; skipped loudly
//      until those vectors land)
//   6. micro benchmark (RTF + peak RSS), gated on SUPERTONIC_BENCH=1 —
//     prints markdown rows for the PR benchmark table
//
// No test framework: the conventions forbid new third-party deps, so this is
// a tiny CHECK harness with a nonzero exit on any failure.

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <numeric>
#include <random>
#include <sstream>
#include <string>
#include <vector>

#include "supertonic_engine.h"
#include "tinyjson.h"

using supertonic::Config;
using supertonic::Engine;
using supertonic::Error;
using supertonic::Style;

namespace {

int g_failures = 0;
int g_checks = 0;

void check(bool cond, const char* expr, const char* file, int line) {
    ++g_checks;
    if (!cond) {
        ++g_failures;
        fprintf(stderr, "FAIL %s:%d: %s\n", file, line, expr);
    }
}

#define CHECK(cond) check((cond), #cond, __FILE__, __LINE__)

// Every engine API contract states it throws supertonic::Error; this guards
// that no std::exception (or worse) escapes.
template <typename Fn>
void checkThrows(Fn fn, const char* expr, const char* file, int line) {
    ++g_checks;
    try {
        fn();
        ++g_failures;
        fprintf(stderr, "FAIL %s:%d: expected Error from %s\n", file, line, expr);
    } catch (const Error&) {
        // expected
    } catch (const std::exception& e) {
        ++g_failures;
        fprintf(stderr, "FAIL %s:%d: %s threw non-Error: %s\n", file, line, expr, e.what());
    }
}

#define CHECK_THROWS(fn) checkThrows((fn), #fn, __FILE__, __LINE__)

std::string slurp(const std::string& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) {
        throw Error("test: cannot read " + path);
    }
    std::ostringstream ss;
    ss << in.rdbuf();
    return ss.str();
}

// ---------------------------------------------------------------------------
// Tier 1: tinyjson
// ---------------------------------------------------------------------------

void testTinyjson() {
    auto doc = tinyjson::parse(
        R"({"n": 42, "f": -1.5e-3, "s": "héllo\n", "a": [1, [2.5, 3]], "b": true, "z": null})");
    CHECK(doc.at("n").asLong() == 42);
    CHECK(std::abs(doc.at("f").asDouble() - (-1.5e-3)) < 1e-12);
    CHECK(doc.at("s").asString() == "héllo\n");
    CHECK(doc.at("b").boolean && doc.at("b").type == tinyjson::Value::Type::Bool);
    CHECK(doc.at("z").type == tinyjson::Value::Type::Null);
    CHECK(doc.at("a").asArray().size() == 2);
    // Nested arrays flatten row-major for style data.
    auto flat = doc.at("a").flattenFloats();
    CHECK(flat.size() == 3);
    CHECK(flat[0] == 1.0f && flat[1] == 2.5f && flat[2] == 3.0f);
    // find() on a missing key is nullptr, not an error.
    CHECK(doc.find("missing") == nullptr);
    CHECK(doc.find("n") != nullptr);
    // Unicode escapes survive as UTF-8 bytes.
    auto esc = tinyjson::parse(R"({"u": "\u00e9\u4e2d"})");
    CHECK(esc.at("u").asString() == "\xc3\xa9\xe4\xb8\xad");  // é中
    // Malformed input throws with a byte offset.
    for (const char* bad : {R"({"a" 1})", R"({"a": [1, 2],})", "{", R"(["unterminated")"}) {
        bool threw = false;
        try {
            tinyjson::parse(bad);
        } catch (const tinyjson::Error&) {
            threw = true;
        }
        ++g_checks;
        if (!threw) {
            ++g_failures;
            fprintf(stderr, "FAIL tinyjson accepted malformed input: %s\n", bad);
        }
    }
}

// ---------------------------------------------------------------------------
// Tier 2: tts.json config contract
// ---------------------------------------------------------------------------

void testParseConfig() {
    const std::string minimal =
        R"({"ae": {"sample_rate": 44100, "base_chunk_size": 512},)"
        R"("ttl": {"latent_dim": 24, "chunk_compress_factor": 6}})";
    Config c = Engine::parseConfig(minimal);
    CHECK(c.sampleRate == 44100);
    CHECK(c.baseChunkSize == 512);
    CHECK(c.latentDim == 24);
    CHECK(c.chunkCompress == 6);
    CHECK(c.vocabSize == supertonic::kDefaultVocabSize);
    CHECK(c.chunkSamples() == 3072);
    CHECK(c.latentWidth() == 144);

    // Optional vocab_size override.
    Config v = Engine::parseConfig(R"({"ae": {"sample_rate": 22050, "base_chunk_size": 256},)"
                                   R"("ttl": {"latent_dim": 12, "chunk_compress_factor": 4},)"
                                   R"("vocab_size": 999})");
    CHECK(v.vocabSize == 999);
    CHECK(v.sampleRate == 22050);

    // Fail-fast: every contract key missing/malformed throws. (JSON lives in
    // named constants: stringizing a raw literal inside CHECK_THROWS trips
    // the preprocessor.)
    const std::string missingKey =
        R"({"ae": {"base_chunk_size": 512},)"
        R"("ttl": {"latent_dim": 24, "chunk_compress_factor": 6}})";
    const std::string nonPositive =
        R"({"ae": {"sample_rate": 44100, "base_chunk_size": 512},)"
        R"("ttl": {"latent_dim": 0, "chunk_compress_factor": 6}})";
    const std::string unterminated = R"({"ttl": {"latent_dim": 24,)";
    CHECK_THROWS([&] { Engine::parseConfig(unterminated); });
    CHECK_THROWS([&] { Engine::parseConfig(missingKey); });
    CHECK_THROWS([&] { Engine::parseConfig(nonPositive); });
    CHECK_THROWS([&] { Engine::parseConfig(minimal + "}"); });  // trailing garbage
}

// ---------------------------------------------------------------------------
// Tier 3: token normalization fuzz
// ---------------------------------------------------------------------------

void testNormalizeTokens() {
    // Contract cases.
    CHECK(Engine::normalizeTokens({}, supertonic::kDefaultVocabSize).empty());

    bool clamped = false;
    auto ids = Engine::normalizeTokens({-1, -supertonic::kDefaultVocabSize}, supertonic::kDefaultVocabSize, &clamped);
    CHECK(!clamped);
    CHECK(ids.size() == 2);
    CHECK(ids[0] == supertonic::kDefaultVocabSize - 1);  // ONNX Gather wrap
    CHECK(ids[1] == 0);                                  // exactly -vocab -> 0
    CHECK(!clamped);

    ids = Engine::normalizeTokens({-supertonic::kDefaultVocabSize - 1}, supertonic::kDefaultVocabSize, &clamped);
    CHECK(clamped);
    CHECK(ids[0] == 0);  // below -vocab saturates at 0

    ids = Engine::normalizeTokens({supertonic::kDefaultVocabSize}, supertonic::kDefaultVocabSize, &clamped);
    CHECK(clamped);
    CHECK(ids[0] == supertonic::kDefaultVocabSize - 1);

    // Truncation above kMaxTokens keeps the prefix.
    std::vector<int64_t> big(supertonic::kMaxTokens + 512);
    for (size_t i = 0; i < big.size(); ++i) {
        big[i] = static_cast<int64_t>(i % 1000);
    }
    ids = Engine::normalizeTokens(big, supertonic::kDefaultVocabSize, &clamped);
    CHECK(!clamped);
    CHECK(ids.size() == static_cast<size_t>(supertonic::kMaxTokens));
    CHECK(ids.back() == (supertonic::kMaxTokens - 1) % 1000);

    // Fuzz: deterministic random int64 across the full range; every output
    // id must land in [0, vocab) and negatives must equal the wrap rule.
    const int vocab = supertonic::kDefaultVocabSize;
    std::mt19937_64 rng(0xBEEF);
    std::uniform_int_distribution<int64_t> dist(
        -3LL * vocab, 3LL * vocab);
    for (int iter = 0; iter < 2000; ++iter) {
        std::vector<int64_t> input(64);
        for (auto& x : input) {
            x = dist(rng);
        }
        auto out = Engine::normalizeTokens(input, vocab, &clamped);
        bool expectClamp = false;
        for (size_t i = 0; i < input.size(); ++i) {
            int64_t want = input[i];
            if (want < 0) {
                want += vocab;
                if (want < 0) {
                    want = 0;
                    expectClamp = true;
                }
            } else if (want >= vocab) {
                want = vocab - 1;
                expectClamp = true;
            }
            if (out[i] != static_cast<int32_t>(want)) {
                ++g_failures;
                fprintf(stderr, "FAIL normalizeTokens fuzz: in=%lld out=%d want=%lld\n",
                        static_cast<long long>(input[i]), out[i], static_cast<long long>(want));
                ++g_checks;
                return;  // stop at first divergence; the contract is broken
            }
        }
        CHECK(clamped == expectClamp);
    }
}

// ---------------------------------------------------------------------------
// Tier 4+5+6: model-backed tests
// ---------------------------------------------------------------------------

std::string envOr(const char* key, const std::string& fallback) {
    const char* v = std::getenv(key);
    return v ? std::string(v) : fallback;
}

std::vector<int64_t> sampleTokens() {
    // A short mixed-range token sequence: unicode-derived ids (some near the
    // vocab top), all valid. Any ids < vocab exercise the real input path.
    return {12, 341, 2048, 8320, 5, 777, 100, 4321, 96, 1234};
}

long peakRssKb() {
    // VmHWM from /proc/self/status: high-water resident set.
    std::ifstream in("/proc/self/status");
    std::string line;
    while (std::getline(in, line)) {
        if (line.rfind("VmHWM:", 0) == 0) {
            long kb = 0;
            sscanf(line.c_str(), "VmHWM: %ld kB", &kb);
            return kb;
        }
    }
    return 0;
}

double rms(const std::vector<float>& pcm) {
    double acc = 0.0;
    for (float v : pcm) {
        acc += static_cast<double>(v) * v;
    }
    return pcm.empty() ? 0.0 : std::sqrt(acc / static_cast<double>(pcm.size()));
}

void testModelTier(const std::string& models) {
    const std::string configJson = slurp(models + "/tts.json");
    auto engine = Engine::load(models + "/duration_predictor.mnn",
                               models + "/text_encoder.mnn",
                               models + "/vector_estimator.mnn",
                               models + "/vocoder.mnn",
                               configJson,
                               2);
    const Config config = Engine::parseConfig(configJson);
    CHECK(engine->sampleRate() == config.sampleRate);

    // Synthesize without a style must throw (and never crash).
    CHECK_THROWS([&] { engine->synthesize(sampleTokens(), 1.0f); });

    Style style = engine->parseStyle(slurp(models + "/voice_styles/F1.json"));
    engine->setStyle(style);

    // Determinism: two runs with the same seed are bit-identical.
    auto a = engine->synthesize(sampleTokens(), 1.0f);
    auto b = engine->synthesize(sampleTokens(), 1.0f);
    CHECK(a.size() == b.size());
    CHECK(!a.empty());
    CHECK(a == b);

    // PCM sanity: length is whole chunks (vocoder emits latent_len *
    // chunk_samples), duration plausible, signal not degenerate.
    CHECK(a.size() % static_cast<size_t>(config.chunkSamples()) == 0);
    const double seconds = static_cast<double>(a.size()) / config.sampleRate;
    CHECK(seconds > 0.05 && seconds < 60.0);
    const double level = rms(a);
    CHECK(level > 1e-4 && level < 5.0);

    // A different seed changes the latent draw (and typically the output).
    engine->setSeed(4321);
    auto c = engine->synthesize(sampleTokens(), 1.0f);
    engine->setSeed(supertonic::kDefaultSeed);
    CHECK(c.size() > 0);
    CHECK(c != a);

    // Speed shortens the output (duration /= speed before latent sizing).
    auto fast = engine->synthesize(sampleTokens(), 2.0f);
    auto slow = engine->synthesize(sampleTokens(), 0.5f);
    CHECK(fast.size() < a.size());
    CHECK(slow.size() > a.size());
    // Out-of-range speed clamps instead of misbehaving.
    auto clampedFast = engine->synthesize(sampleTokens(), 99.0f);
    CHECK(clampedFast.size() == fast.size());

    // Empty input -> empty audio, even with a bound style.
    CHECK(engine->synthesize({}, 1.0f).empty());

    // Garbage-but-in-range inputs still produce audio (never crash): the
    // extreme ends of the vocab and max-length chunker tripwire.
    CHECK(!engine->synthesize({0, supertonic::kDefaultVocabSize - 1}, 1.0f).empty());
    std::vector<int64_t> maxTokens(supertonic::kMaxTokens, 42);
    CHECK(!engine->synthesize(maxTokens, 1.0f).empty());

    // Steps knob: 1 step runs the loop once and yields audio of the same
    // shape contract; invalid setter throws.
    engine->setSteps(1);
    CHECK(!engine->synthesize(sampleTokens(), 1.0f).empty());
    engine->setSteps(supertonic::kDefaultSteps);
    CHECK_THROWS([&] { engine->setSteps(0); });

    // Injected-latent golden path (NEU-68 delta 2): a fixed latent produces
    // bit-identical output across calls, independent of the RNG seed; a
    // wrong-sized latent fails fast.
    {
        const size_t latentLen =
            static_cast<size_t>(std::ceil(static_cast<double>(a.size()) / config.chunkSamples()));
        std::vector<float> latent(static_cast<size_t>(config.latentWidth()) * latentLen);
        std::mt19937 rng(7);
        std::normal_distribution<float> gauss(0.0f, 1.0f);
        for (auto& v : latent) {
            v = gauss(rng);
        }
        engine->setSeed(111);
        auto withLatent1 = engine->synthesizeWithLatent(sampleTokens(), 1.0f, latent);
        engine->setSeed(222);  // seed must not matter when the latent is injected
        auto withLatent2 = engine->synthesizeWithLatent(sampleTokens(), 1.0f, latent);
        engine->setSeed(supertonic::kDefaultSeed);
        CHECK(!withLatent1.empty());
        CHECK(withLatent1 == withLatent2);
        CHECK_THROWS([&] {
            engine->synthesizeWithLatent(sampleTokens(), 1.0f, std::vector<float>(latent.size() + 1, 0.0f));
        });
    }

    // Style validation: a structurally valid JSON with wrong dims fails
    // parseStyle, and the previously bound style stays active.
    const std::string badStyle =
        R"({"style_ttl": {"dims": [1, 2, 3], "data": [[[0, 0, 0], [0, 0, 0]]]},)"
        R"("style_dp": {"dims": [1, 2, 3], "data": [[[0, 0, 0], [0, 0, 0]]]}})";
    CHECK_THROWS([&] { engine->parseStyle(badStyle); });
    CHECK_THROWS([&] { engine->parseStyle("{}"); });
    const std::string unterminatedStyle = R"({"style_ttl": {"dims": [2, 50, 256], "data": []},)";
    CHECK_THROWS([&] { engine->parseStyle(unterminatedStyle); });
    auto after = engine->synthesize(sampleTokens(), 1.0f);
    CHECK(after == a);  // old style (and seed) still active, bit-identical
}

// Reference-vector tier: golden PCM from the NEU-68 conversion-side
// verification harness. Per the NEU-68 delta, that harness injects the
// noisy_latent (never unseeded np.random), so a cross-implementation vector
// must carry that exact latent — the engine's own RNG stream cannot match a
// numpy draw. Vector file format (JSON):
//   {"tokens": [...], "speed": 1.0, "style": "voice_styles/F1.json",
//    "latent": [...],              # row-major [1, latent_width, latent_len]
//    "pcm": [...]}
// Comparison is exact on length with a tight absolute tolerance (CPU kernels
// may round differently across compilers); divergence is reported loudly.
void testReferenceVectors(const std::string& models, const std::string& vectorFile) {
    auto doc = tinyjson::parse(slurp(vectorFile));
    if (doc.find("latent") == nullptr) {
        ++g_failures;
        fprintf(stderr,
                "FAIL reference vectors: %s has no injected \"latent\" — "
                "an RNG-seeded vector is not comparable across implementations\n",
                vectorFile.c_str());
        return;
    }
    std::vector<int64_t> tokens;
    for (const auto& t : doc.at("tokens").asArray()) {
        tokens.push_back(t.asLong());
    }
    const float speed = static_cast<float>(doc.at("speed").asDouble());
    const auto latent = doc.at("latent").flattenFloats();

    auto engine = Engine::load(models + "/duration_predictor.mnn",
                               models + "/text_encoder.mnn",
                               models + "/vector_estimator.mnn",
                               models + "/vocoder.mnn",
                               slurp(models + "/tts.json"),
                               1);
    engine->setSteps(supertonic::kDefaultSteps);
    engine->setStyle(engine->parseStyle(slurp(models + "/" + doc.at("style").asString())));

    auto got = engine->synthesizeWithLatent(tokens, speed, latent);
    auto want = doc.at("pcm").flattenFloats();
    if (got.size() != want.size()) {
        ++g_failures;
        fprintf(stderr, "FAIL reference vectors: length %zu != %zu (%s)\n",
                got.size(), want.size(), vectorFile.c_str());
        return;
    }
    double maxDiff = 0.0;
    size_t worst = 0;
    for (size_t i = 0; i < got.size(); ++i) {
        double d = std::abs(static_cast<double>(got[i]) - want[i]);
        if (d > maxDiff) {
            maxDiff = d;
            worst = i;
        }
    }
    ++g_checks;
    // Tolerance is NEU-68's to calibrate (their thresholds are fitted to spike
    // data). Default 1e-3; override with SUPERTONIC_TEST_TOLERANCE.
    const double tolerance = std::atof(envOr("SUPERTONIC_TEST_TOLERANCE", "1e-3").c_str());
    if (maxDiff > tolerance) {
        ++g_failures;
        fprintf(stderr, "FAIL reference vectors: max |diff| %.6f > %.1e at %zu (%.4f vs %.4f) (%s)\n",
                maxDiff, tolerance, worst, got[worst], want[worst], vectorFile.c_str());
    } else {
        printf("  reference vectors: max |diff| %.2e over %zu samples (%s)\n",
               maxDiff, got.size(), vectorFile.c_str());
    }
}

void benchTier(const std::string& models, const char* label) {
    const int threads = std::max(1, std::atoi(envOr("SUPERTONIC_BENCH_THREADS", "1").c_str()));
    auto engine = Engine::load(models + "/duration_predictor.mnn",
                               models + "/text_encoder.mnn",
                               models + "/vector_estimator.mnn",
                               models + "/vocoder.mnn",
                               slurp(models + "/tts.json"),
                               threads);
    engine->setStyle(engine->parseStyle(slurp(models + "/voice_styles/F1.json")));
    const auto tokens = sampleTokens();

    // Warm-up (first run pays MNN lazy init), then timed runs.
    auto warm = engine->synthesize(tokens, 1.0f);
    std::vector<double> rtfs;
    const int runs = 5;
    for (int i = 0; i < runs; ++i) {
        const auto t0 = std::chrono::steady_clock::now();
        auto pcm = engine->synthesize(tokens, 1.0f);
        const auto t1 = std::chrono::steady_clock::now();
        const double wall = std::chrono::duration<double>(t1 - t0).count();
        const double audio = static_cast<double>(pcm.size()) / engine->sampleRate();
        rtfs.push_back(wall / audio);
    }
    std::sort(rtfs.begin(), rtfs.end());
    const double mean = (std::accumulate(rtfs.begin(), rtfs.end(), 0.0)) / runs;
    printf("BENCH|%s|threads=%d|tokens=%zu|audio_s=%.2f|rtf_mean=%.3f|rtf_median=%.3f|peak_rss_mb=%.0f\n",
           label, threads, tokens.size(),
           static_cast<double>(warm.size()) / engine->sampleRate(),
           mean, rtfs[runs / 2], peakRssKb() / 1024.0);
}

}  // namespace

int main(int argc, char** argv) {
    (void)argc;
    (void)argv;

    // Bench-only: skip the test tiers so peak RSS reflects the engine load +
    // synthesis alone (VmHWM is process-wide and would otherwise include the
    // test tiers' allocations).
    if (std::getenv("SUPERTONIC_BENCH_ONLY")) {
        const std::string models = envOr("SUPERTONIC_TEST_MODELS", "");
        if (models.empty()) {
            fprintf(stderr, "SUPERTONIC_BENCH_ONLY needs SUPERTONIC_TEST_MODELS\n");
            return 1;
        }
        try {
            benchTier(models, envOr("SUPERTONIC_BENCH_LABEL", "default").c_str());
        } catch (const std::exception& e) {
            fprintf(stderr, "FAIL bench threw: %s\n", e.what());
            return 1;
        }
        return 0;
    }

    printf("tier 1: tinyjson\n");
    testTinyjson();

    printf("tier 2: tts.json config\n");
    testParseConfig();

    printf("tier 3: normalizeTokens fuzz\n");
    testNormalizeTokens();

    const std::string models = std::getenv("SUPERTONIC_TEST_MODELS")
                                   ? std::string(std::getenv("SUPERTONIC_TEST_MODELS"))
                                   : "";
    if (!models.empty()) {
        printf("tier 4: model pipeline (%s)\n", models.c_str());
        try {
            testModelTier(models);

            const char* vectors = std::getenv("SUPERTONIC_TEST_VECTORS");
            if (vectors && *vectors) {
                printf("tier 5: reference vectors (%s)\n", vectors);
                testReferenceVectors(models, vectors);
            } else {
                printf("tier 5: SKIP (SUPERTONIC_TEST_VECTORS unset — NEU-68 vectors not wired)\n");
            }

            if (std::getenv("SUPERTONIC_BENCH")) {
                printf("tier 6: benchmark\n");
                benchTier(models, std::getenv("SUPERTONIC_BENCH_LABEL") ? std::getenv("SUPERTONIC_BENCH_LABEL")
                                                                        : "default");
            }
        } catch (const std::exception& e) {
            ++g_failures;
            fprintf(stderr, "FAIL model tier threw: %s\n", e.what());
        }
    } else {
        printf("tier 4-6: SKIP (SUPERTONIC_TEST_MODELS unset)\n");
    }

    printf("%d checks, %d failures\n", g_checks, g_failures);
    return g_failures == 0 ? 0 : 1;
}
