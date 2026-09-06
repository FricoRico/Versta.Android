//
// OCR engine: orchestration, MNN model plumbing and the JNI surface.
// Pipeline math lives in detect.cc / dewarp.cc / recognize.cc /
// glyphmatte.cc — this file only wires the stages together.
//

#define LOG_TAG "VerstaOcr"

#include <jni.h>
#include <array>
#include <atomic>
#include <cstdio>
#include <numeric>
#include <sys/auxv.h>
#include <unordered_map>

#include <MNN/Interpreter.hpp>
#include <MNN/Tensor.hpp>

#include "include/Log.h"
#include "include/ocr_pipeline.h"

namespace ocr {

/// UTF-32 -> UTF-8 encoding for JNI jstrings.
std::string utf8For(const std::u32string& text) {
    std::string out;
    for (char32_t ch : text) {
        if (ch < 0x80) {
            out += static_cast<char>(ch);
        } else if (ch < 0x800) {
            out += static_cast<char>(0xC0 | (ch >> 6));
            out += static_cast<char>(0x80 | (ch & 0x3F));
        } else if (ch < 0x10000) {
            out += static_cast<char>(0xE0 | (ch >> 12));
            out += static_cast<char>(0x80 | ((ch >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (ch & 0x3F));
        } else {
            out += static_cast<char>(0xF0 | (ch >> 18));
            out += static_cast<char>(0x80 | ((ch >> 12) & 0x3F));
            out += static_cast<char>(0x80 | ((ch >> 6) & 0x3F));
            out += static_cast<char>(0x80 | (ch & 0x3F));
        }
    }
    return out;
}

// ---------------------------------------------------------------------------
// Model plumbing
// ---------------------------------------------------------------------------

static bool hasSdot() {
#if defined(__aarch64__) && defined(__ANDROID__)
    constexpr unsigned long asimddpBit = 1UL << 20;
    return (getauxval(AT_HWCAP) & asimddpBit) != 0;
#else
    return true;
#endif
}

Model::Session::Session(MNN::Interpreter* net, int threads) {
    MNN::ScheduleConfig config;
    config.type = MNN_FORWARD_CPU;
    config.numThread = threads;

    MNN::BackendConfig backend;
    backend.precision = MNN::BackendConfig::Precision_Low;
    backend.memory = hasSdot()
        ? MNN::BackendConfig::Memory_Low
        : MNN::BackendConfig::Memory_High;
    config.backendConfig = &backend;

    session = net->createSession(config);
}

std::unique_ptr<Model> Model::load(const std::string& path) {
    auto interpreter = std::unique_ptr<MNN::Interpreter>(
        MNN::Interpreter::createFromFile(path.c_str()));
    if (!interpreter) {
        LOGE("OCR: failed to load MNN model %s", path.c_str());
        return nullptr;
    }

    auto model = std::make_unique<Model>();
    model->interpreter = std::move(interpreter);

    return model;
}

std::unique_ptr<Model::Session> Model::session(int threads) {
    return std::make_unique<Session>(interpreter.get(), threads);
}


std::vector<float> Model::run(MNN::Session* session,
                              const std::vector<float>& data,
                              const std::vector<int>& shape,
                              std::vector<int>& outShape) {
    auto* liveInput = interpreter->getSessionInput(session, nullptr);
    interpreter->resizeTensor(liveInput, shape);
    interpreter->resizeSession(session);

    liveInput = interpreter->getSessionInput(session, nullptr);
    MNN::Tensor host(liveInput, MNN::Tensor::CAFFE);
    ::memcpy(host.host<float>(), data.data(), data.size() * sizeof(float));
    liveInput->copyFromHostTensor(&host);
    interpreter->runSession(session);

    // The exports may carry more than the heatmap/logits; take the largest
    // float tensor. getSessionOutput(nullptr) is unreliable with multiple outputs.
    auto outputs = interpreter->getSessionOutputAll(session);
    MNN::Tensor* output = nullptr;
    size_t best = 0;
    for (const auto& [name, tensor] : outputs) {
        const size_t count = static_cast<size_t>(std::max(0, tensor->elementSize()));
        if (count > best) { best = count; output = tensor; }
    }
    if (!output || best == 0) {
        LOGE("OCR: model run produced no float output");
        return {};
    }

    MNN::Tensor outHost(output, MNN::Tensor::CAFFE);
    output->copyToHostTensor(&outHost);

    const auto& dims = outHost.shape();
    bool sane = dims.size() >= 1 && dims.size() <= 8;
    if (sane) for (int d : dims) sane &= d >= 0 && d <= (1 << 24);
    if (!sane) {
        std::string dump;
        for (size_t i = 0; i < dims.size() && i < 8; i++) {
            dump += std::to_string(dims[i]) + "x";
        }
        LOGE("OCR: model output has insane shape ndim=%zu [%s]", dims.size(), dump.c_str());
        outShape = {};
        return {};
    }

    outShape = outHost.shape();
    const auto size = outHost.elementSize();
    if (size <= 0 || static_cast<long>(size) > (1L << 28)) {
        LOGE("OCR: model output has invalid element count %d", size);
        return {};
    }
    auto* elements = outHost.host<float>();
    if (!elements) {
        LOGE("OCR: model output has no host memory");
        return {};
    }
    return {elements, elements + size};
}

// ---------------------------------------------------------------------------
// Recognizer
// ---------------------------------------------------------------------------

std::unique_ptr<Recognizer> Recognizer::load(const std::string& modelPath,
                                             const std::string& vocabPath,
                                             bool rtl) {
    auto model = Model::load(modelPath);
    if (!model) {
        return nullptr;
    }

    auto recognizer = std::make_unique<Recognizer>();
    recognizer->model = std::move(model);
    recognizer->charset = loadCharset(vocabPath);
    recognizer->rtl = rtl;
    if (recognizer->charset.size() < 3) {
        LOGE("OCR: recognizer charset too small (%s)", vocabPath.c_str());
        return nullptr;
    }

    // One single-threaded session per worker: heterogeneous strip widths make
    // inter-session parallelism cheaper than intra-session threading.
    for (int i = 0; i < REC_PARALLELISM; i++) {
        recognizer->sessions.push_back(recognizer->model->session(1));
    }

    return recognizer;
}

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

bool Engine::setDetector(const std::string& path, int threads) {
    std::lock_guard<std::mutex> lock(_mutex);

    // Probe the probability-map stride on a throwaway interpreter; the folded
    // det variants emit the heatmap at 1/2 or 1/4 of the input resolution,
    // which compensates box inflation downstream. Kept separate from the
    // inference interpreter because resizing an already-realized int8 session
    // can corrupt its output shape.
    auto probe = Model::load(path);
    if (!probe) return false;
    {
        auto probeSession = probe->session(threads);
        std::vector<int> shape;
        auto out = probe->run(probeSession->session,
                              std::vector<float>(3 * 64 * 64, 0.0f),
                              {1, 3, 64, 64}, shape);
        if (shape.size() < 4) {
            LOGE("OCR: detector probe output shape unexpected (%zu dims)", shape.size());
            return false;
        }
        _detStride = 64.0f / static_cast<float>(shape.back());
    }

    auto model = Model::load(path);
    if (!model) return false;
    _detector = std::move(model);
    _detSession = _detector->session(threads);

    return true;
}

bool Engine::setScriptClassifier(const std::string& path, std::vector<std::string> routes) {
    std::lock_guard<std::mutex> lock(_mutex);
    LOGI("OCR load pulc %s (%zu routes)", path.c_str(), routes.size());
    auto model = Model::load(path);
    if (!model) return false;

    _pul = std::move(model);
    // One single-threaded session per worker: an 80x160 classifier is too
    // small for intra-op threading to pay off; inter-session parallelism does.
    for (int i = 0; i < REC_PARALLELISM; i++) {
        _pulSessions.push_back(_pul->session(1));
    }
    _routes = std::move(routes);

    return true;
}

bool Engine::setGlyphMatte(const std::string& path) {
    std::lock_guard<std::mutex> lock(_mutex);
    LOGI("OCR load glyphmatte %s", path.c_str());
    // Conv-only matte model. Memory_High dequant was once assumed faster
    // (Winograd/Strassen on dequantized weights); measured on-device it is
    // BOTH slower (464 vs 258 ms x86) and weaker (matte under-covers ink),
    // so the int8 GEMM path stays.
    auto model = Model::load(path);
    if (!model) return false;

    _matte = std::move(model);
    for (int i = 0; i < REC_PARALLELISM; i++) {
        _matteSessions.push_back(_matte->session(1));
    }

    return true;
}

bool Engine::setAligner(const std::string& path) {
    std::lock_guard<std::mutex> lock(_mutex);
    LOGI("OCR load aligner %s", path.c_str());
    auto model = Model::load(path);
    if (!model) return false;

    _aligner = std::move(model);
    _alignerSession = _aligner->session(1);

    return true;
}

bool Engine::addRecognizer(const std::string& key, const std::string& modelPath,
                           const std::string& vocabPath) {
    std::lock_guard<std::mutex> lock(_mutex);
    LOGI("OCR add recognizer %s (%s)", key.c_str(), modelPath.c_str());
    auto slot = std::make_unique<RecognizerSlot>();
    slot->key = key;
    slot->modelPath = modelPath;
    slot->vocabPath = vocabPath;
    slot->rtl = key.find("arabic") != std::string::npos;
    _slots.push_back(std::move(slot));

    if (key.find("tiny_rec") != std::string::npos) {
        _dominantKey = key;
    }
    if (_dominantKey.empty()) _dominantKey = key;

    return true;
}

Recognizer* Engine::recognizer(const std::string& key) {
    // Called from analyze()'s worker threads (analyze holds _mutex): slots are
    // append-only via addRecognizer (also under _mutex), and slot init is
    // guarded by std::call_once.
    for (auto& slot : _slots) {
        if (slot->key != key) continue;

        std::call_once(slot->once, [&slot]() {
            slot->recognizer = Recognizer::load(slot->modelPath, slot->vocabPath, slot->rtl);
        });

        if (!slot->recognizer) {
            LOGE("OCR: recognizer %s failed to load", slot->key.c_str());
            return nullptr;
        }
        return slot->recognizer.get();
    }
    return nullptr;
}

// ---------------------------------------------------------------------------
// Document alignment (docaligner regressed corners -> perspective warp)
// ---------------------------------------------------------------------------

/// Decodes the docaligner's output into the 4 TL/TR/BR/BL corners in the
/// upright frame's pixel space. The model ships in two shapes — regressed
/// [1,8] points or per-corner heatmaps [1,C,H,W] — both land here so the
/// caller only ever handles the geometry.
static bool decodeAlignCorners(const std::vector<float>& out,
                               const std::vector<int>& shape,
                               int inputDim, int frameW, int frameH,
                               std::vector<cv::Point2f>& corners) {
    if (shape.size() == 2 && out.size() == 8) {
        // Regressed (x,y) x4 — normalized 0..1 or input-space 0..256
        // depending on export.
        float maxAbs = 0;
        for (float v : out) maxAbs = std::max(maxAbs, std::fabs(v));
        const float unit = maxAbs <= 1.2f ? static_cast<float>(inputDim) : 1.0f;
        for (int c = 0; c < 4; c++) {
            corners[c] = {out[c * 2] * unit * frameW / inputDim,
                          out[c * 2 + 1] * unit * frameH / inputDim};
        }
        return true;
    }
    if (shape.size() == 4 && shape[1] >= 4) {
        // Corner heatmaps: one channel per corner, peak = its position.
        const int hmH = shape[2], hmW = shape[3];
        for (int c = 0; c < 4; c++) {
            const float* map = out.data() + static_cast<size_t>(c) * hmH * hmW;
            int argmax = 0;
            float maxV = map[0];
            for (int i = 1; i < hmW * hmH; i++) {
                if (map[i] > maxV) { maxV = map[i]; argmax = i; }
            }
            corners[c] = {(float)(argmax % hmW) / hmW * frameW,
                          (float)(argmax / hmW) / hmH * frameH};
        }
        return true;
    }
    return false;
}

bool Engine::alignDocument(const cv::Mat& upright, cv::Mat& warped, cv::Mat& inverse) {
    if (!_aligner) return false;

    constexpr int kInput = 256;
    cv::Mat resized;
    cv::resize(upright, resized, {kInput, kInput});

    std::vector<float> input(3 * kInput * kInput);
    const int plane = kInput * kInput;
    for (int y = 0; y < kInput; y++) {
        const auto* row = resized.ptr<cv::Vec3b>(y);
        for (int x = 0; x < kInput; x++) {
            const int idx = y * kInput + x;
            // ImageNet normalization, same as the detector.
            input[idx] = (row[x][0] / 255.0f - PPOCR_DET_MEAN[0]) / PPOCR_DET_STD[0];
            input[plane + idx] = (row[x][1] / 255.0f - PPOCR_DET_MEAN[1]) / PPOCR_DET_STD[1];
            input[2 * plane + idx] = (row[x][2] / 255.0f - PPOCR_DET_MEAN[2]) / PPOCR_DET_STD[2];
        }
    }

    std::vector<int> shape;
    auto out = _aligner->run(_alignerSession->session, input,
                             {1, 3, kInput, kInput}, shape);
    if (out.empty()) return false;

    std::vector<cv::Point2f> corners(4);
    if (!decodeAlignCorners(out, shape, kInput, upright.cols, upright.rows, corners)) {
        LOGE("OCR align: unexpected output shape");
        return false;
    }

    // Gates: convex quad, no slivers, decent coverage — otherwise the "document"
    // peaks latch onto random scene content and the warp would scramble the frame.
    auto cross = [](const cv::Point2f& o, const cv::Point2f& a, const cv::Point2f& b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    };
    float sign = 0;
    bool convex = true;
    for (int i = 0; i < 4; i++) {
        const float z = cross(corners[i], corners[(i + 1) % 4], corners[(i + 2) % 4]);
        if (sign == 0) sign = z;
        if (z * sign < 0) convex = false;
    }
    const float quadArea = std::fabs(cv::contourArea(corners));
    const float imageArea = static_cast<float>(upright.cols) * upright.rows;
    const float diagLen = std::hypot((float)upright.cols, (float)upright.rows);
    float minEdge = 1e30f;
    for (int i = 0; i < 4; i++) {
        minEdge = std::min(minEdge, (float)cv::norm(corners[i] - corners[(i + 1) % 4]));
    }
    if (!convex || quadArea < imageArea * 0.2f || minEdge < diagLen * 0.1f) {
        return false;
    }

    // Destination rectangle from the quad's own mean edge lengths (the doc's
    // true aspect is unknown; warping onto the mean rectangle preserves detail).
    const float w0 = ((float)cv::norm(corners[1] - corners[0]) +
                      (float)cv::norm(corners[2] - corners[3])) / 2.0f;
    const float h0 = ((float)cv::norm(corners[3] - corners[0]) +
                      (float)cv::norm(corners[2] - corners[1])) / 2.0f;
    std::vector<cv::Point2f> dst = {{0, 0}, {w0, 0}, {w0, h0}, {0, h0}};
    const cv::Mat H = cv::getPerspectiveTransform(corners, dst);

    cv::warpPerspective(upright, warped, H, {(int)std::lround(w0), (int)std::lround(h0)},
                        cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    inverse = H.inv();

    return true;
}

// ---------------------------------------------------------------------------
// analyze orchestration
// ---------------------------------------------------------------------------

std::vector<TextLine> Engine::analyze(const uint8_t* rgba, int width, int height,
                                      int rotationDegrees, Profile profile,
                                      const std::string& forcedKey) {
    std::lock_guard<std::mutex> lock(_mutex);
    _builtStripsThisCall = false;

    if (!_detector || _slots.empty()) {
        return {};
    }

    cv::Mat frame(height, width, CV_8UC4, const_cast<uint8_t*>(rgba));
    rotateByDegrees(frame, rotationDegrees);
    cv::Mat upright;
    cv::cvtColor(frame, upright, cv::COLOR_RGBA2RGB);

    // Live frames route through the anchor tracker (acquire / relocalize /
    // quiet gate); stills always run the full pass.
    if (profile == Profile::Live) {
        return analyzeLive(upright, forcedKey, rotationDegrees);
    }

    return runFullPipeline(upright, profile, forcedKey);
}

std::vector<TextLine> Engine::runFullPipeline(const cv::Mat& upright, Profile profile,
                                              const std::string& forcedKey) {
    // Stills: rectify the document first so downstream geometry lives in flat
    // page space; points get mapped back through the inverse warp at the end.
    cv::Mat image = upright;
    cv::Mat inverseWarp;
    const bool aligned = profile == Profile::Still && alignDocument(upright, image, inverseWarp);

    auto boxes = detect(image, profile);
    if (boxes.empty()) {
        return {};
    }

    // Dewarp once per box; the duel, routing, recognition and matte all read
    // these strips (used to re-warp the same geometry four times per pass).
    auto strips = buildStrips(image, boxes);

    Recognizer* duel = recognizer(forcedKey.empty() ? _dominantKey : forcedKey);
    const int canonical = canonicalQuadrant(boxes, strips, duel);

    const auto keys = forcedKey.empty()
        ? routeScripts(image, boxes, strips, canonical)
        : std::vector<std::string>(boxes.size(), forcedKey);

    auto lines = recognize(boxes, strips, keys, canonical, profile);

    applyGlyphMatte(image, lines, strips, canonical);

    // Paragraph blocks + canonical column snap BEFORE erase/quad derivation:
    // one translation unit per block, snapped once in canonical space so the
    // erase envelope and the render quad stay identical, and the per-frame
    // homography supplies the perspective (reference: surface-space
    // normalize, then warp per frame).
    assignBlocks(lines);
    snapBlockTightRects(lines);

    // Text erasure: each line's padded render region gets the ink replaced by
    // the block-median field; patches ride the anchor and re-pose per frame.
    runErase(image, lines, strips);

    // Overlay render quad: the detection box the reference renders — tight band
    // expanded isotropically by the DB unclip distance + border, at the line's
    // resolved tilt angle (≈1.1–1.25x the ink; the tight band alone is ~0.55x).
    // rectCorners is shared with the erase envelope — they must agree.
    for (auto& line : lines) {
        const OrientedRect& t = line.box.tight;
        const float bandH = std::max(t.height, 1.0f);
        const float expandDist = renderExpandDistance(bandH, detPoolCompensationPx());
        rectCorners(t.cx, t.cy, t.angle, t.width, bandH, expandDist, expandDist,
                    line.box.corners);
    }

    if (aligned) {
        auto remap = [&](Point* pts4, size_t n) {
            std::vector<cv::Point2f> f(n);
            for (size_t p = 0; p < n; p++) f[p] = {pts4[p].x, pts4[p].y};
            cv::perspectiveTransform(f, f, inverseWarp);
            for (size_t p = 0; p < n; p++) pts4[p] = {f[p].x, f[p].y};
        };
        for (auto& line : lines) {
            remap(line.box.corners, 4);
            if (line.erase) remap(line.eraseCorners, 4);
            remap(line.box.contour.data(), line.box.contour.size());
        }
    }

    return lines;
}

} // namespace ocr

// ---------------------------------------------------------------------------
// JNI surface — app.versta.translate.bridge.inference.OcrEngine
// ---------------------------------------------------------------------------

namespace {
std::unordered_map<jlong, std::unique_ptr<ocr::Engine>> engineInstances;
jlong engineInstanceCounter = 0;
} // namespace

extern "C" {

// Guarded handle lookup; unlike map::operator[], never grows the table on
// garbage handles.
static ocr::Engine* engineFor(jlong handle) {
    auto it = engineInstances.find(handle);
    return it == engineInstances.end() ? nullptr : it->second.get();
}

JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_construct(JNIEnv*, jobject) {
    jlong handle = ++engineInstanceCounter;
    engineInstances[handle] = std::make_unique<ocr::Engine>();
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_close(JNIEnv*, jobject, jlong handle) {
    return engineInstances.erase(handle) > 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_setDetector(
        JNIEnv* env, jobject, jlong handle, jstring modelPath, jint threads) {
    auto* engine = engineFor(handle);
    if (!engine) return JNI_FALSE;

    const char* chars = env->GetStringUTFChars(modelPath, nullptr);
    bool ok = engine->setDetector(chars, threads);
    env->ReleaseStringUTFChars(modelPath, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_setScriptClassifier(
        JNIEnv* env, jobject, jlong handle, jstring modelPath, jobjectArray routes) {
    auto* engine = engineFor(handle);
    if (!engine) return JNI_FALSE;

    std::vector<std::string> routeKeys;
    const jsize n = env->GetArrayLength(routes);
    routeKeys.reserve(n);
    for (jsize i = 0; i < n; i++) {
        auto str = static_cast<jstring>(env->GetObjectArrayElement(routes, i));
        const char* chars = env->GetStringUTFChars(str, nullptr);
        routeKeys.emplace_back(chars);
        env->ReleaseStringUTFChars(str, chars);
        env->DeleteLocalRef(str);
    }

    const char* chars = env->GetStringUTFChars(modelPath, nullptr);
    bool ok = engine->setScriptClassifier(chars, std::move(routeKeys));
    env->ReleaseStringUTFChars(modelPath, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_setGlyphMatte(
        JNIEnv* env, jobject, jlong handle, jstring modelPath) {
    auto* engine = engineFor(handle);
    if (!engine) return JNI_FALSE;

    const char* chars = env->GetStringUTFChars(modelPath, nullptr);
    bool ok = engine->setGlyphMatte(chars);
    env->ReleaseStringUTFChars(modelPath, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_setAligner(
        JNIEnv* env, jobject, jlong handle, jstring modelPath) {
    auto* engine = engineFor(handle);
    if (!engine) return JNI_FALSE;

    const char* chars = env->GetStringUTFChars(modelPath, nullptr);
    bool ok = engine->setAligner(chars);
    env->ReleaseStringUTFChars(modelPath, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_addRecognizer(
        JNIEnv* env, jobject, jlong handle, jstring key, jstring modelPath, jstring vocabPath) {
    auto* engine = engineFor(handle);
    if (!engine) return JNI_FALSE;

    const char* keyChars = env->GetStringUTFChars(key, nullptr);
    const char* modelChars = env->GetStringUTFChars(modelPath, nullptr);
    const char* vocabChars = env->GetStringUTFChars(vocabPath, nullptr);
    bool ok = engine->addRecognizer(keyChars, modelChars, vocabChars);
    env->ReleaseStringUTFChars(key, keyChars);
    env->ReleaseStringUTFChars(modelPath, modelChars);
    env->ReleaseStringUTFChars(vocabPath, vocabChars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

/// One line's erased-strip JNI payload. Pixels cross JNI only on the acquire
/// that built them (fresh epoch); tracked frames re-pose the corners only and
/// the Kotlin side reuses the cached patch.
struct MarshalledStrip {
    jbyteArray bytes = nullptr;
    jfloatArray corners = nullptr;
    jint w = 0;
    jint h = 0;
};

static MarshalledStrip marshalStrip(JNIEnv* env, const ocr::TextLine& line, int freshEpoch) {
    MarshalledStrip out;
    if (!line.erase) return out;
    out.w = line.erase->w;
    out.h = line.erase->h;
    out.corners = env->NewFloatArray(8);
    std::vector<jfloat> sc(8);
    for (int p = 0; p < 4; p++) {
        sc[p * 2] = line.eraseCorners[p].x;
        sc[p * 2 + 1] = line.eraseCorners[p].y;
    }
    env->SetFloatArrayRegion(out.corners, 0, 8, sc.data());
    if (line.erase->epoch == freshEpoch) {
        out.bytes = env->NewByteArray(static_cast<jsize>(line.erase->rgba.size()));
        env->SetByteArrayRegion(out.bytes, 0,
                                static_cast<jsize>(line.erase->rgba.size()),
                                reinterpret_cast<const jbyte*>(line.erase->rgba.data()));
    }
    return out;
}

static void releaseStrip(JNIEnv* env, const MarshalledStrip& strip) {
    if (strip.bytes) env->DeleteLocalRef(strip.bytes);
    if (strip.corners) env->DeleteLocalRef(strip.corners);
}

static jobjectArray marshalLines(JNIEnv* env, const std::vector<ocr::TextLine>& lines,
                                 int freshEpoch) {
    jclass lineClass = env->FindClass("app/versta/translate/core/entity/OcrDetectedLine");
    if (!lineClass) return nullptr;
    jmethodID ctor = env->GetMethodID(lineClass, "<init>",
        "([FLjava/lang/String;FIIZI[BII[F)V");
    if (!ctor) return nullptr;

    jobjectArray result = env->NewObjectArray(static_cast<jsize>(lines.size()), lineClass, nullptr);

    jsize i = 0;
    for (const auto& line : lines) {
        std::vector<jfloat> points(8);
        for (int p = 0; p < 4; p++) {
            points[p * 2] = line.box.corners[p].x;
            points[p * 2 + 1] = line.box.corners[p].y;
        }
        jfloatArray pointsArray = env->NewFloatArray(8);
        env->SetFloatArrayRegion(pointsArray, 0, 8, points.data());

        jstring text = env->NewStringUTF(ocr::utf8For(line.text).c_str());
        const MarshalledStrip strip = marshalStrip(env, line, freshEpoch);

        jobject lineObject = env->NewObject(
            lineClass, ctor,
            pointsArray, text, static_cast<jfloat>(line.score),
            static_cast<jint>(line.fgColor), static_cast<jint>(line.bgColor),
            static_cast<jboolean>(line.bold),
            static_cast<jint>(line.blockId),
            strip.bytes, strip.w, strip.h, strip.corners);

        env->SetObjectArrayElement(result, i++, lineObject);

        env->DeleteLocalRef(lineObject);
        env->DeleteLocalRef(pointsArray);
        env->DeleteLocalRef(text);
        releaseStrip(env, strip);
    }

    env->DeleteLocalRef(lineClass);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_analyze(
        JNIEnv* env, jobject, jlong handle, jobject input,
        jint inputWidth, jint inputHeight, jint rotationDegrees, jint profile,
        jstring forcedRecognizer) {
    auto* engine = engineFor(handle);
    if (!engine) return nullptr;

    auto* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(input));
    if (!data) return nullptr;

    std::string forced;
    if (forcedRecognizer != nullptr) {
        const char* chars = env->GetStringUTFChars(forcedRecognizer, nullptr);
        forced = chars;
        env->ReleaseStringUTFChars(forcedRecognizer, chars);
    }

    auto lines = engine->analyze(data, inputWidth, inputHeight, rotationDegrees,
                                 profile == 0 ? ocr::Profile::Still : ocr::Profile::Live,
                                 forced);
    // Strip pixels marshal only when THIS call ran the erasure (fresh acquire):
    // projected lines on locked ticks still carry the acquire's epoch, so a
    // bare epoch check re-sent the pixels every analyze tick (and reset the
    // Kotlin patch store, flashing text-without-background at frame edges).
    return marshalLines(env, lines,
                        engine->builtStripsThisCall() ? engine->currentEraseEpoch() : -1);
}

JNIEXPORT jfloatArray JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_tick(
        JNIEnv* env, jobject, jlong handle, jobject input,
        jint inputWidth, jint inputHeight, jint rotationDegrees) {
    auto* engine = engineFor(handle);
    if (!engine) return nullptr;

    auto* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(input));
    if (!data) return nullptr;

    ocr::hmat::H9 h;
    uint64_t epoch = 0, version = 0;
    if (!engine->liveTick(data, inputWidth, inputHeight, rotationDegrees,
                          h, epoch, version)) {
        return nullptr;
    }

    // 9 pose floats + anchor epoch + content version. The pose composes the
    // overlay; version moves tell Kotlin when liveContent() has anything new
    // to marshal.
    std::vector<jfloat> packed(11);
    for (int i = 0; i < 9; i++) packed[i] = h[i];
    packed[9] = static_cast<jfloat>(epoch);
    packed[10] = static_cast<jfloat>(version);
    jfloatArray out = env->NewFloatArray(11);
    env->SetFloatArrayRegion(out, 0, 11, packed.data());
    return out;
}

JNIEXPORT jobjectArray JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_liveContent(
        JNIEnv* env, jobject, jlong handle) {
    auto* engine = engineFor(handle);
    if (!engine) return nullptr;

    // Strip bytes ride only with the epoch that built them (locked ticks
    // keep the acquire's epoch, and marshaling must not spam pixels).
    return marshalLines(env, engine->lastLiveLines(), engine->lastLiveEpoch());
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrEngine_probeLive(
        JNIEnv* env, jobject, jlong handle, jobject input,
        jint inputWidth, jint inputHeight, jint rotationDegrees) {
    auto* engine = engineFor(handle);
    if (!engine) return JNI_FALSE;

    auto* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(input));
    if (!data) return JNI_FALSE;

    return engine->probeStillness(data, inputWidth, inputHeight, rotationDegrees)
                   ? JNI_TRUE
                   : JNI_FALSE;
}


} // extern "C"
