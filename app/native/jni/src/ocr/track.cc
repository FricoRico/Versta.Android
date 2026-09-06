//
// Live anchor tracking: one anchor holds FAST+BRIEF features of an upright
// canonical frame plus the full OCR pass's canonical overlays. Every JNI tick
// on the presenter thread applies the relocalize worker's latest correction
// (snap, or woven with the KLT motion accumulated since the frame the worker
// consumed — 16-entry pose ring), then runs the coarse KLT step whose
// similarity delta composes onto the pose, then re-dispatches the worker.
// Overlays render as the canonical lines projected through the pose.
//
// Ports of translator-tracker (planar_engine.rs / coarse_tracker.rs /
// live_tracker_pipeline.rs) with the simplified single-anchor variant. The
// expensive path (descriptor match + PROSAC RANSAC + sanity gates +
// homography EKF) runs on a dedicated single-slot worker thread so its burst
// never stalls the frame loop; dispatches drop while it's busy. Dropped
// anchors keep their features + overlays in a 3-entry LRU re-lock cache, so a
// scene returning into view snaps back instantly (reference
// try_cached_anchors); a fresh full pipeline still gates on a 200 ms
// stillness window.
//

#define LOG_TAG "VerstaOcr"

#include <algorithm>
#include <chrono>
#include <cmath>

#include <opencv2/video/tracking.hpp>

#include "include/Log.h"
#include "include/ocr_pipeline.h"

namespace ocr {

namespace {

constexpr int COARSE_MIN_INLIERS = 8;  // coarse_tracker.rs emit gate
constexpr int MAX_SEEDS = 80;          // coarse_tracker.rs seed budget
constexpr size_t POSE_RING_CAP = 16;   // coarse_tracker.rs RING — weave lookback
constexpr size_t ANCHOR_CACHE_MAX = 3; // reference anchor_cache_size (5), trimmed
constexpr float LOWE_LOCKED = 0.85f;   // TrackerConfig::lowe_ratio_locked
constexpr int RELOC_MIN_INLIERS = 25;  // TrackerConfig::min_inliers
constexpr int RELOC_MAX_FAILURES = 3;  // consecutive relocalize misses before Lost

// Reference: planar_engine.rs sanity gates. A geometrically impossible or
// wildly jumping fit is dropped (counts toward Lost); a suspiciously thin
// inlier set against a healthy EMA FREEZES the pose (substitutes the last
// accepted homography) for up to GATE_FREEZE_BUDGET consecutive ticks —
// wrong fixes must never reach the screen.
constexpr float MAX_CORNER_JUMP_PX = 300.0f; // MAX_CORNER_JUMP_PX
constexpr float INLIER_EMA_ALPHA = 0.2f;     // inlier_ema_alpha
constexpr float GATE_MIN_EMA = 60.0f;        // sanity_gate_min_ema
constexpr float GATE_DROP_RATIO = 0.3f;      // sanity_gate_drop_ratio
constexpr int GATE_FREEZE_BUDGET = 3;        // sanity_gate_max_consecutive

constexpr float GUIDED_RADIUS_PX = 30.0f; // TrackerConfig::guided_search_radius_px
constexpr int GUIDED_BRUTE_FALLBACK = 75; // reference GUIDED_BRUTE_FALLBACK_THRESHOLD

// Correction quality gates (evidence: Pixel pans produced wrong-basin fits at
// med 26–80 px with inlier counts collapsed to 26–130, while the same
// device's HEALTHY handheld corrections run med 6–18 px at 200–280 inliers —
// camera sharpening/noise lifts real-phone residuals far above the
// reference's clean 0.5–1 px). 25 px keeps the healthy band and rejects the
// poison band; treat trips as freeze-worthy, not fatal.
constexpr float GATE_MAX_MEDIAN_RESIDUAL_PX = 25.0f;

// Coarse-defense grace: one bad KLT tick must not kill the anchor while a
// reloc verdict is still in flight (the "lost after 0 frames" churn seen on
// device: acquire snapshots arrive ~500 ms stale, and the first KLT defense
// cannot bridge pan motion that accumulated during the pipeline). Frozen
// frames present the last pose and touch NO tracker state — seeds and
// prevGray stay consistent with each other (same discipline as the
// correction freeze gate).
constexpr int COARSE_MAX_FAILURES = 3;

constexpr int KLT_WIN_RADIUS = 5;      // KltConfig::window_radius
constexpr int KLT_MAX_ITER = 8;
constexpr double KLT_EPS = 0.03;
constexpr float KLT_FB_EPS_PX = 0.5f;  // KltConfig::fb_check_eps

// Coarse KLT runs at half scale: the fwd+bwd PyrLK pair rebuilds four image
// pyramids per tick, so blur cost dominates everything (measured ~40 ms/tick
// avg, 124 ms worst at 720x960 on a Pixel 9 Pro). Half-res quarters the blurred
// pixels to ~5-10 ms. Displacements are rescaled back to full-res, so every
// downstream gate (ransac fits, 4 px rescore, 300 px delta sanity) keeps its
// full-res semantics; the FB epsilon tightens to the same effective error.
constexpr float KLT_SMALL_SCALE = 0.5f;
constexpr int KLT_LEVELS_SMALL = 3;    // one fewer level: the pyramid top moved 2x closer
constexpr float KLT_FB_EPS_SMALL_PX = KLT_FB_EPS_PX * KLT_SMALL_SCALE;

cv::Mat smallGray(const cv::Mat& gray) {
    cv::Mat small;
    cv::resize(gray, small, {}, KLT_SMALL_SCALE, KLT_SMALL_SCALE, cv::INTER_AREA);
    return small;
}

/// PyrLK tuning shared by the prewarm and the real coarse tick — the dummy
/// run must warm exactly the configuration the presenter will exercise.
struct KltParams {
    cv::TermCriteria criteria{cv::TermCriteria::COUNT | cv::TermCriteria::EPS,
                              KLT_MAX_ITER, KLT_EPS};
    cv::Size win{KLT_WIN_RADIUS * 2 + 1, KLT_WIN_RADIUS * 2 + 1};
    static constexpr int maxLevel = KLT_LEVELS_SMALL - 1;
};

// One dummy fwd+bwd PyrLK on the fresh anchor: the first REAL locked tick
// otherwise eats the allocator/code-path cold start (the ~100-180 ms klt
// outliers the tick windows showed at anchor time). Runs on the acquire
// worker thread, never the presenter.
void prewarmCoarse(const cv::Mat& prevSmall,
                   const std::vector<std::array<float, 4>>& seeds) {
    if (prevSmall.empty() || seeds.empty()) return;
    std::vector<cv::Point2f> pts;
    for (size_t i = 0; i < seeds.size() && i < 8; i++) {
        pts.push_back({seeds[i][2] * KLT_SMALL_SCALE, seeds[i][3] * KLT_SMALL_SCALE});
    }
    std::vector<cv::Point2f> cur, back;
    std::vector<uint8_t> st, bst;
    std::vector<float> err, berr;
    const KltParams klt;
    cv::calcOpticalFlowPyrLK(prevSmall, prevSmall, pts, cur, st, err, klt.win,
                             klt.maxLevel, klt.criteria);
    cv::calcOpticalFlowPyrLK(prevSmall, prevSmall, cur, back, bst, berr, klt.win,
                             klt.maxLevel, klt.criteria);
}

constexpr float QUIET_MEAN_ABS_DIFF = 3.0f;
constexpr double QUIET_WINDOW_MS = 200.0;
constexpr int STILL_LONG_SIDE = 128;

cv::Mat toUprightGray(const uint8_t* rgba, int width, int height, int rotationDegrees) {
    cv::Mat frame(height, width, CV_8UC4, const_cast<uint8_t*>(rgba));
    rotateByDegrees(frame, rotationDegrees);
    cv::Mat gray;
    cv::cvtColor(frame, gray, cv::COLOR_RGBA2GRAY);
    return gray;
}

cv::Mat stillThumbnail(const cv::Mat& gray) {
    const int longSide = std::max(gray.cols, gray.rows);
    if (longSide <= STILL_LONG_SIDE) return gray.clone();
    const double s = static_cast<double>(STILL_LONG_SIDE) / longSide;
    cv::Mat small;
    cv::resize(gray, small, {}, s, s, cv::INTER_AREA);
    return small;
}

// Evenly spaced subsample over quality-ordered pairs (reference: seeds are the
// top inliers, capped; stride keeps viewport coverage instead of a prefix).
std::vector<std::array<float, 4>> pickSeeds(const std::vector<std::array<float, 4>>& pairs) {
    if (pairs.size() <= MAX_SEEDS) return pairs;
    std::vector<std::array<float, 4>> out;
    out.reserve(MAX_SEEDS);
    const float step = static_cast<float>(pairs.size()) / MAX_SEEDS;
    for (int i = 0; i < MAX_SEEDS; i++) {
        out.push_back(pairs[static_cast<size_t>(i * step)]);
    }
    return out;
}

// PROSAC order (best descriptor distance first) + flatten to point pairs.
std::vector<std::array<float, 4>> pairsFromMatches(std::vector<anchor::DescMatch>& matches,
                                                   const anchor::FeatureSet& anchorFs,
                                                   const anchor::FeatureSet& frameFs) {
    std::sort(matches.begin(), matches.end(),
              [](const anchor::DescMatch& a, const anchor::DescMatch& b) {
                  return a.distance < b.distance;
              });
    std::vector<std::array<float, 4>> pairs;
    pairs.reserve(matches.size());
    for (const auto& m : matches) {
        const auto& a = anchorFs.kps[m.anchorIdx];
        const auto& f = frameFs.kps[m.frameIdx];
        pairs.push_back({a.x, a.y, f.x, f.y});
    }
    return pairs;
}

/// homography_is_sane: the anchor frame's corners project finitely, no
/// projected edge exceeds 2x the frame diagonal, opposite edges stay within
/// 3x of each other both axes.
bool homographyIsSane(const hmat::H9& h, int w, int ht) {
    const float corners[4][2] = {{0, 0}, {static_cast<float>(w), 0},
                                 {static_cast<float>(w), static_cast<float>(ht)},
                                 {0, static_cast<float>(ht)}};
    float p[4][2];
    for (int i = 0; i < 4; i++) {
        if (!hmat::project(h, corners[i][0], corners[i][1], p[i][0], p[i][1])) return false;
    }
    const float diag = std::hypot(static_cast<float>(w), static_cast<float>(ht));
    auto edge = [&](int a, int b) { return std::hypot(p[a][0] - p[b][0], p[a][1] - p[b][1]); };
    const float e01 = edge(0, 1), e12 = edge(1, 2), e23 = edge(2, 3), e30 = edge(3, 0);
    if (e01 > 2.0f * diag || e12 > 2.0f * diag || e23 > 2.0f * diag || e30 > 2.0f * diag) return false;
    const float loH = std::min(e01, e23), loV = std::min(e12, e30);
    if (loH <= 1.0f || loV <= 1.0f) return false;
    if (std::max(e01, e23) / loH > 3.0f || std::max(e12, e30) / loV > 3.0f) return false;
    return true;
}

/// homography_delta_is_sane: max corner displacement between the previous
/// pose and the new fit stays under MAX_CORNER_JUMP_PX.
bool deltaIsSane(const hmat::H9& prev, const hmat::H9& next, int w, int ht) {
    const float corners[4][2] = {{0, 0}, {static_cast<float>(w), 0},
                                 {static_cast<float>(w), static_cast<float>(ht)},
                                 {0, static_cast<float>(ht)}};
    for (const auto& c : corners) {
        float ax, ay, bx, by;
        if (!hmat::project(prev, c[0], c[1], ax, ay)) return false;
        if (!hmat::project(next, c[0], c[1], bx, by)) return false;
        if (std::hypot(ax - bx, ay - by) > MAX_CORNER_JUMP_PX) return false;
    }
    return true;
}

} // namespace

// ---------------------------------------------------------------------------
// Stillness gate
// ---------------------------------------------------------------------------

void Engine::updateStillness(const cv::Mat& small) {
    cv::Mat thumb = stillThumbnail(small);
    const auto now = std::chrono::steady_clock::now();
    if (_stillGray.empty() || thumb.size() != _stillGray.size()) {
        _stillGray = std::move(thumb);
        _stillSince = now;
        return;
    }
    cv::Mat diff;
    cv::absdiff(thumb, _stillGray, diff);
    const double mean = cv::mean(diff)[0];
    _stillGray = std::move(thumb);
    if (mean >= QUIET_MEAN_ABS_DIFF) {
        _stillSince = now;
    }
}

bool Engine::quietEnough() const {
    if (_stillGray.empty()) return false;
    const auto now = std::chrono::steady_clock::now();
    return std::chrono::duration<double, std::milli>(now - _stillSince).count() >= QUIET_WINDOW_MS;
}

// ---------------------------------------------------------------------------
// Anchor lifecycle
// ---------------------------------------------------------------------------

void Engine::storeAnchor(const cv::Mat& gray, std::vector<TextLine> lines,
                         int rotationDegrees) {
    anchor::FeatureSet fs = anchor::computeFeatures(gray);
    if (fs.kps.size() < static_cast<size_t>(RELOC_MIN_INLIERS)) {
        LOGE("OCR anchor: %zu features — scene untrackable, running unlocked", fs.kps.size());
        return;
    }

    auto state = std::make_unique<AnchorState>();
    state->gray = gray;
    state->rotation = rotationDegrees;
    state->lines = std::move(lines);

    std::vector<std::array<float, 4>> seeds;
    seeds.reserve(fs.kps.size());
    for (const auto& kp : fs.kps) seeds.push_back({kp.x, kp.y, kp.x, kp.y});

    state->features = std::move(fs);
    state->seeds = pickSeeds(seeds);
    state->prevGray = gray;
    state->prevGraySmall = smallGray(gray);

    _anchor = std::move(state);
    _anchorEpoch++; // pending/inflight worker results for the old anchor die here
    prewarmCoarse(_anchor->prevGraySmall, _anchor->seeds);
}

void Engine::dropAnchor() {
    if (_anchor) {
        // Survive the drop in the re-lock cache: the scene the anchor covered
        // returning into view must snap back instantly, not re-detect.
        auto c = std::make_unique<CachedAnchor>();
        c->rotation = _anchor->rotation;
        c->grayCols = _anchor->gray.cols;
        c->grayRows = _anchor->gray.rows;
        c->features = _anchor->features;
        c->lines = _anchor->lines;
        _anchorCache.push_front(std::move(c));
        while (_anchorCache.size() > ANCHOR_CACHE_MAX) _anchorCache.pop_back();
    }
    _anchor.reset();
    _anchorEpoch++; // worker results for the dropped anchor are discarded
    _lastLiveLines.clear();
    _lastLiveEpoch = -1;
}

// Reference planar_engine.rs try_cached_anchors: while anchorless, every tick
// brute-matches the small LRU of dropped anchors — a scene that wandered out
// (fast pan, occlusion) snaps back the moment it returns, long before the 200
// ms stillness gate would allow a fresh full pipeline.
bool Engine::relockCached(const cv::Mat& gray, int rotationDegrees) {
    if (_anchorCache.empty()) return false;

    anchor::FeatureSet fs = anchor::computeFeatures(gray);
    if (fs.descs.size() < static_cast<size_t>(RELOC_MIN_INLIERS)) return false;

    for (size_t i = 0; i < _anchorCache.size(); i++) {
        const auto& c = _anchorCache[i];
        if (c->rotation != rotationDegrees) continue;
        if (c->grayCols != gray.cols || c->grayRows != gray.rows) continue;

        // Brute Lowe only — no pose prior exists while unlocked.
        std::vector<anchor::DescMatch> matches =
            anchor::matchLowe(c->features.descs, fs.descs, LOWE_LOCKED);
        if (matches.size() < static_cast<size_t>(RELOC_MIN_INLIERS)) continue;

        auto pairs = pairsFromMatches(matches, c->features, fs);

        anchor::TrackFit fit;
        if (!anchor::ransacHomography(pairs, nullptr, RELOC_MIN_INLIERS, fit)) continue;
        if (!homographyIsSane(fit.h, gray.cols, gray.rows)) continue;
        // No delta gate: the scene legitimately moved while unlocked — but a
        // sloppy median residual is still a wrong-scene fit (device evidence:
        // 40+ px "re-locks" that immediately churned the anchor). Same bound
        // as the locked-path gate.
        if (fit.medianResidualPx > GATE_MAX_MEDIAN_RESIDUAL_PX) {
            continue;
        }

        auto state = std::make_unique<AnchorState>();
        state->gray = gray;
        state->rotation = rotationDegrees;
        state->lines = c->lines;
        state->features = c->features;
        state->hView = fit.h;
        state->seeds = pickSeeds(fit.inlierPairs);
        state->prevGray = gray;
        state->prevGraySmall = smallGray(gray);
        _anchor = std::move(state);
        prewarmCoarse(_anchor->prevGraySmall, _anchor->seeds);
        _anchorEpoch++;

        // LRU touch: the hit anchor leads the cache.
        auto entry = std::move(_anchorCache[i]);
        _anchorCache.erase(_anchorCache.begin() + static_cast<long>(i));
        _anchorCache.push_front(std::move(entry));

        // The Kotlin patch store no longer holds THIS anchor's strips (whatever
        // interrupted us re-populated it), so the restored lines must marshal
        // their pixels once: rebadge them into the current erase epoch and let
        // the caller flag this frame as strip-fresh.
        ++_eraseEpoch;
        for (auto& line : _anchor->lines) {
            if (line.erase) line.erase->epoch = _eraseEpoch;
        }

        return true;
    }
    return false;
}

// ---------------------------------------------------------------------------
// Relocalize worker (reference TrackerCompute/live_tracker_pipeline.rs): one
// dedicated thread with a single pending slot. Dispatch while busy drops —
// the next tick retries with fresher state, so no queue ever builds.
// ---------------------------------------------------------------------------

Engine::Engine() {
    _relocThread = std::thread(&Engine::relocLoop, this);
}

Engine::~Engine() {
    {
        std::lock_guard<std::mutex> g(_relocMutex);
        _relocStop = true;
        _relocCv.notify_one();
    }
    if (_relocThread.joinable()) _relocThread.join();
}

void Engine::relocLoop() {
    while (true) {
        RelocRequest req;
        {
            std::unique_lock<std::mutex> g(_relocMutex);
            _relocCv.wait(g, [&] { return _relocRequest.has_value() || _relocStop; });
            if (_relocStop) return;
            req = std::move(*_relocRequest);
            _relocRequest.reset();
        }
        RelocResult res = relocWorkerTick(req);
        {
            std::lock_guard<std::mutex> g(_relocMutex);
            _relocResult = std::move(res);
            _relocInflight = false;
        }
    }
}

/// Feature gather + guided/brute matching + RANSAC fit + the sanitation gates
/// for one relocalize tick. This whole block is pure with respect to the
/// worker's filter state (EKF/EMA/budget) — it reads [req] and the previous
/// fit only; the presenter-visible state machinery lives further up.
std::optional<anchor::TrackFit> Engine::relocFitFor(const RelocRequest& req,
                                                    const hmat::H9& lastH) {
    anchor::FeatureSet fs = anchor::computeFeatures(req.gray);
    if (fs.descs.size() < static_cast<size_t>(RELOC_MIN_INLIERS)) {
        return std::nullopt;
    }

    // Guided matching through the dispatch-time pose as prior: each anchor
    // point only competes against frame features within 30 px of its
    // prediction, so blur-degraded descriptors win their window instead of
    // losing to the global pool (clustered-inlier drift), and inliers spread
    // across the whole anchor. Brute fallback fires only when the prior went
    // stale (few matches = real positions escaped the window).
    std::vector<anchor::DescMatch> matches = anchor::matchGuided(
        req.features, fs, LOWE_LOCKED, req.prior, GUIDED_RADIUS_PX);
    if (matches.size() < GUIDED_BRUTE_FALLBACK) {
        std::vector<anchor::DescMatch> brute =
            anchor::matchLowe(req.features.descs, fs.descs, LOWE_LOCKED);
        if (brute.size() > matches.size()) matches = std::move(brute);
    }
    if (matches.size() < static_cast<size_t>(RELOC_MIN_INLIERS)) {
        return std::nullopt;
    }

    auto pairs = pairsFromMatches(matches, req.features, fs);

    anchor::TrackFit fit;
    if (!anchor::ransacHomography(pairs, &req.prior, RELOC_MIN_INLIERS, fit)) {
        return std::nullopt;
    }

    // Sanitation before the fit can touch the pose.
    if (!homographyIsSane(fit.h, req.anchorW, req.anchorH)) {
        return std::nullopt;
    }
    if (!deltaIsSane(lastH, fit.h, req.anchorW, req.anchorH)) {
        return std::nullopt;
    }
    return fit;
}

Engine::RelocResult Engine::relocWorkerTick(const RelocRequest& req) {
    RelocResult res;
    res.epoch = req.epoch;
    res.frameIdx = req.frameIdx;

    // Epoch change (new anchor): the correction-side filter state re-seeds
    // from the dispatch prior — a stale anchor's fit must never leak through.
    if (req.epoch != _relocWorkerEpoch) {
        _relocWorkerEpoch = req.epoch;
        _relocEkf = std::make_unique<anchor::HomographyEkf>(req.prior);
        _relocInlierEma = 0.0f;
        _relocFreezeBudget = GATE_FREEZE_BUDGET;
        _relocLastH = req.prior;
    }

    auto fitOpt = relocFitFor(req, _relocLastH);
    if (!fitOpt) return res;
    const anchor::TrackFit fit = std::move(*fitOpt);

    // Suspicious fits (thin inliers vs the running EMA, or a wrong-basin
    // median residual) FREEZE the pose for up to GATE_FREEZE_BUDGET ticks —
    // the presenter holds while the coarse path keeps moving off the last
    // accepted correction. Freeze touches nothing; only budget exhaustion
    // counts toward Lost.
    const bool sloppy = fit.medianResidualPx > GATE_MAX_MEDIAN_RESIDUAL_PX;
    const bool suspicious = sloppy ||
        (_relocInlierEma >= GATE_MIN_EMA &&
         fit.inliers < GATE_DROP_RATIO * _relocInlierEma);
    if (suspicious) {
        if (_relocFreezeBudget > 0) {
            _relocFreezeBudget--;
            res.kind = RelocKind::Frozen;
            return res;
        }
        _relocInlierEma = 0.0f;
        _relocFreezeBudget = GATE_FREEZE_BUDGET;
        return res;
    }

    _relocEkf->predict();
    _relocEkf->updatePairs(fit.inlierPairs);
    const hmat::H9& h = _relocEkf->homography();

    _relocLastH = h;
    _relocInlierEma = _relocInlierEma > 0.0f
        ? (1.0f - INLIER_EMA_ALPHA) * _relocInlierEma + INLIER_EMA_ALPHA * fit.inliers
        : static_cast<float>(fit.inliers);
    _relocFreezeBudget = GATE_FREEZE_BUDGET;

    res.kind = RelocKind::Accepted;
    res.h = h;
    res.inlierPairs = fit.inlierPairs;
    return res;
}

// ---------------------------------------------------------------------------
// Coarse KLT step (klt_forward_fit): seeds' view-side points tracked into the
// current frame, forward-backward vetted, similarity delta composed onto the
// pose so the prior's perspective survives sparse-inlier frames.
// ---------------------------------------------------------------------------

// Hold-with-budget: a single bad KLT tick presents the last accepted pose and
// leaves ALL state untouched (seeds/prevGray stay paired) so an in-flight
// reloc verdict can resurrect the defense; only sustained misses kill the
// anchor. Post-acquire pans were instant death otherwise: the snapshot the
// anchor was built from is a full pipeline old (≈0.5 s on-device), and the
// first defense tick cannot bridge the motion accumulated since.
std::vector<TextLine> Engine::coarseHold() {
    _anchor->coarseFailures++;
    if (_anchor->coarseFailures >= COARSE_MAX_FAILURES) {
        dropAnchor();
        return {};
    }
    return projectOverlays();
}

std::vector<TextLine> Engine::coarseTrack(const cv::Mat& gray, const cv::Mat& small) {
    if (!_anchor || _anchor->seeds.size() < 5) {
        // Structural (never started): no pose worth holding.
        dropAnchor();
        return {};
    }

    const KltParams klt;

    // Track on the caller-built half-scale frame (shared with the stillness
    // gate — both were each downsampling the full-res gray per tick).
    if (_anchor->prevGraySmall.size() != small.size()) {
        _anchor->prevGraySmall = smallGray(_anchor->prevGray);
    }

    std::vector<cv::Point2f> prevPts;
    prevPts.reserve(_anchor->seeds.size());
    for (const auto& s : _anchor->seeds) {
        prevPts.push_back({s[2] * KLT_SMALL_SCALE, s[3] * KLT_SMALL_SCALE});
    }

    std::vector<cv::Point2f> curPts, backPts;
    std::vector<uint8_t> status, backStatus;
    std::vector<float> err, backErr;
    cv::calcOpticalFlowPyrLK(_anchor->prevGraySmall, small, prevPts, curPts, status, err,
                             klt.win, klt.maxLevel, klt.criteria);
    cv::calcOpticalFlowPyrLK(small, _anchor->prevGraySmall, curPts, backPts, backStatus, backErr,
                             klt.win, klt.maxLevel, klt.criteria);

    std::vector<std::array<float, 4>> viewPairs; // (prevView, curView) — delta fit input
    std::vector<std::array<float, 4>> rootPairs; // (anchor,   curView) — rescore output
    constexpr float invScale = 1.0f / KLT_SMALL_SCALE;
    for (size_t i = 0; i < prevPts.size(); i++) {
        if (!status[i] || !backStatus[i]) continue;
        if (cv::norm(backPts[i] - prevPts[i]) > KLT_FB_EPS_SMALL_PX) continue;
        const auto& s = _anchor->seeds[i];
        viewPairs.push_back({s[2], s[3], curPts[i].x * invScale, curPts[i].y * invScale});
        rootPairs.push_back({s[0], s[1], curPts[i].x * invScale, curPts[i].y * invScale});
    }
    if (viewPairs.size() < static_cast<size_t>(COARSE_MIN_INLIERS)) {
        return coarseHold();
    }

    hmat::H9 delta;
    if (!anchor::ransacSimilarity(viewPairs, COARSE_MIN_INLIERS, delta)) {
        return coarseHold();
    }
    hmat::H9 newH = hmat::matMul(delta, _anchor->hView);
    if (!hmat::normalize(newH)) {
        return coarseHold();
    }

    // Re-score the composed H over (anchor, curView): drives the lost gate.
    constexpr float rThreshSq = 4.0f * 4.0f; // TrackerConfig::ransac_residual_px
    std::vector<std::array<float, 4>> newSeeds;
    for (const auto& p : rootPairs) {
        float px, py;
        if (!hmat::project(newH, p[0], p[1], px, py)) continue;
        const float dx = px - p[2], dy = py - p[3];
        if (dx * dx + dy * dy <= rThreshSq) {
            newSeeds.push_back({p[0], p[1], p[2], p[3]});
        }
    }
    if (newSeeds.size() < static_cast<size_t>(COARSE_MIN_INLIERS)) {
        return coarseHold();
    }

    // Raw composed pose: the correction path is the filtered one (worker
    // EKF); the coarse pose stays a pure measurement so it never lags. The
    // worker's corrections land pre-woven with the motion since their source
    // frame, so unfiltered here does not step there.
    _anchor->hView = newH;
    _anchor->seeds = pickSeeds(newSeeds);
    _anchor->prevGray = gray;
    _anchor->prevGraySmall = small;
    _anchor->coarseFailures = 0;

    return projectOverlays();
}

// ---------------------------------------------------------------------------
// Projection: canonical overlays → view through the current homography.
// ---------------------------------------------------------------------------

std::vector<TextLine> Engine::projectOverlays() {
    if (!_anchor) return {};
    const hmat::H9& H = _anchor->hView;
    const int fw = _anchor->gray.cols, fh = _anchor->gray.rows;

    // Approximate similarity of the linear part (drives tight rect rescale).
    const float h00 = H[0], h01 = H[1], h10 = H[3], h11 = H[4];
    const float det2 = h00 * h11 - h01 * h10;
    const float scale = det2 > 0.0f ? std::sqrt(det2) : 1.0f;
    // 0 for similarity [[c,-s],[s,c]]; the box angle follows content rotation.
    const float rotDelta = std::atan2(h10 - h01, h00 + h11);

    std::vector<TextLine> out;
    out.reserve(_anchor->lines.size());
    auto proj = [&](float x, float y, ocr::Point& outPt) {
        return hmat::project(H, x, y, outPt.x, outPt.y);
    };
    for (const auto& cl : _anchor->lines) {
        TextLine line = cl;
        bool ok = true;
        for (int p = 0; p < 4 && ok; p++) {
            ok = proj(cl.box.corners[p].x, cl.box.corners[p].y, line.box.corners[p]);
        }
        if (!ok) continue;
        for (size_t k = 0; k < cl.box.contour.size() && ok; k++) {
            ok = proj(cl.box.contour[k].x, cl.box.contour[k].y, line.box.contour[k]);
        }
        if (!ok) continue;

        if (line.erase) {
            for (int p = 0; p < 4 && ok; p++) {
                ok = proj(cl.eraseCorners[p].x, cl.eraseCorners[p].y, line.eraseCorners[p]);
            }
            if (!ok) continue;
        }

        for (OrientedRect* r : {&line.box.tight, &line.box.oriented}) {
            ocr::Point c;
            if (!proj(r->cx, r->cy, c)) { ok = false; break; }
            r->cx = c.x;
            r->cy = c.y;
            r->width *= scale;
            r->height *= scale;
            r->angle += rotDelta;
        }
        if (!ok) continue;

        std::vector<cv::Point> quad(4);
        for (int p = 0; p < 4; p++) {
            quad[p] = {static_cast<int>(std::lround(line.box.corners[p].x)),
                       static_cast<int>(std::lround(line.box.corners[p].y))};
        }
        line.box.aabb = cv::boundingRect(quad) & cv::Rect(0, 0, fw, fh);
        if (line.box.aabb.empty()) continue; // slid out of frame

        out.push_back(std::move(line));
    }

    // Live-tick presentation cache (field evidence: per-frame line marshaling
    // cost 28–55 ms on the GL thread with content up). Content crosses JNI
    // only on _liveContentVersion moves; pose rides the tick homography.
    _lastLiveLines = out;
    if (_builtStripsThisCall) {
        _lastLiveEpoch = _eraseEpoch;
        _liveContentVersion++;
    }
    return out;
}

// ---------------------------------------------------------------------------
// Locked tick: order mirrors the reference process_frame — apply the worker's
// latest correction FIRST (so the weave's ring base exists), then coarse KLT
// this frame, then push the pose + maybe re-dispatch the worker.
// ---------------------------------------------------------------------------

void Engine::dispatchReloc(const cv::Mat& gray, uint64_t frameIdx) {
    using namespace std::chrono;
    std::lock_guard<std::mutex> g(_relocMutex);
    if (_relocInflight) return; // single slot: drop-if-busy, next tick retries
    // Correction cadence cap: jobs measure ~46 ms; ~10 Hz of corrections is
    // plenty against the per-tick KLT pose the corrections feed.
    static constexpr auto RELOC_MIN_INTERVAL = 100ms;
    const auto now = steady_clock::now();
    if (_relocLastDispatch.time_since_epoch().count() > 0 &&
        now - _relocLastDispatch < RELOC_MIN_INTERVAL) return;
    _relocLastDispatch = now;
    RelocRequest req;
    req.epoch = _anchorEpoch;
    req.frameIdx = frameIdx;
    req.gray = gray; // refcounted Mat: the frame lives till the worker finishes
    req.prior = _anchor->hView;
    req.features = _anchor->features; // worker never touches presenter state
    req.anchorW = _anchor->gray.cols;
    req.anchorH = _anchor->gray.rows;
    _relocRequest = std::move(req);
    _relocInflight = true;
    _relocCv.notify_one();
}

void Engine::applyRelocResult() {
    RelocResult res;
    {
        std::lock_guard<std::mutex> g(_relocMutex);
        if (!_relocResult) return;
        res = std::move(*_relocResult);
        _relocResult.reset();
    }
    if (!_anchor || res.epoch != _anchorEpoch) return; // stale anchor: discard

    if (res.kind == RelocKind::Rejected) {
        _anchor->relocFailures++;
        if (_anchor->relocFailures >= RELOC_MAX_FAILURES) {
            LOGE("OCR anchor: relocalize failed %dx — lost", _anchor->relocFailures);
            dropAnchor();
        }
        return; // grace: the coarse path keeps moving
    }
    _anchor->relocFailures = 0;
    if (res.kind == RelocKind::Frozen) return; // hold pose (see worker gate)

    // Accepted. Weave (reference CoarseTracker::apply): compose the motion
    // accumulated since the frame the worker consumed onto the correction.
    hmat::H9 motion = hmat::IDENTITY;
    bool wove = false;
    for (auto it = _anchor->poseRing.rbegin(); it != _anchor->poseRing.rend(); ++it) {
        if (it->first == res.frameIdx) {
            hmat::H9 inv;
            if (hmat::invert(it->second, inv)) {
                motion = hmat::matMul(_anchor->hView, inv);
                wove = true;
            }
            break;
        }
    }
    hmat::H9 woven = wove ? hmat::matMul(motion, res.h) : res.h; // no ring entry: snap
    if (!hmat::normalize(woven)) return;
    _anchor->hView = woven;

    // Seeds: the correction's inlier pairs re-posed by the weave motion.
    std::vector<std::array<float, 4>> pairs;
    pairs.reserve(res.inlierPairs.size());
    for (const auto& p : res.inlierPairs) {
        float vx = p[2], vy = p[3];
        if (wove) {
            float qx, qy;
            if (hmat::project(motion, p[2], p[3], qx, qy)) { vx = qx; vy = qy; }
        }
        pairs.push_back({p[0], p[1], vx, vy});
    }
    _anchor->seeds = pickSeeds(pairs);
    _anchor->coarseFailures = 0; // defense was re-seeded into the current frame
}

std::vector<TextLine> Engine::lockedTick(const cv::Mat& gray, const cv::Mat& small) {
    if (!_anchor) return {};
    const uint64_t idx = _frameSeq++;

    applyRelocResult();
    if (!_anchor) return {};

    auto lines = coarseTrack(gray, small);
    if (!_anchor) return lines;

    _anchor->poseRing.emplace_back(idx, _anchor->hView);
    if (_anchor->poseRing.size() > POSE_RING_CAP) _anchor->poseRing.pop_front();
    dispatchReloc(gray, idx);
    return lines;
}

// ---------------------------------------------------------------------------
// Public entry points
// ---------------------------------------------------------------------------

std::vector<TextLine> Engine::analyzeLive(const cv::Mat& upright,
                                          const std::string& forcedKey,
                                          int rotationDegrees) {
    cv::Mat gray;
    cv::cvtColor(upright, gray, cv::COLOR_RGB2GRAY);
    // One half-scale build per tick feeds both the stillness thumbnail and
    // the coarse KLT path — each was downsampling the full frame itself.
    cv::Mat small = smallGray(gray);
    updateStillness(small);

    if (anchorMismatch(rotationDegrees, gray.size())) {
        dropAnchor();
    }

    if (_anchor) {
        return lockedTick(gray, small);
    }

    // Cached-anchor snap-back first: a scene returning into view re-locks
    // instantly instead of waiting out the stillness gate.
    if (relockCached(gray, rotationDegrees)) {
        _builtStripsThisCall = true; // restored lines re-marshal their strips once
        return projectOverlays();
    }

    if (!quietEnough()) {
        return {};
    }

    auto lines = runFullPipeline(upright, Profile::Live, forcedKey);
    storeAnchor(gray, lines, rotationDegrees);
    // Route through the projector like every other live emission: hView is
    // identity on the fresh anchor, so geometry is unchanged, and the tick
    // presentation cache is populated exactly once per content change.
    return projectOverlays();
}

bool Engine::liveTick(const uint8_t* rgba, int width, int height, int rotationDegrees,
                      hmat::H9& h, uint64_t& epoch, uint64_t& contentVersion) {
    // Scalars out only: line CONTENT rides the cached presentation vector and
    // crosses JNI only when _liveContentVersion moves.
    std::lock_guard<std::mutex> lock(_mutex);
    _builtStripsThisCall = false;
    if (!_anchor) return false;
    cv::Mat gray = toUprightGray(rgba, width, height, rotationDegrees);
    if (anchorMismatch(rotationDegrees, gray.size())) {
        dropAnchor();
        return false;
    }
    cv::Mat small = smallGray(gray);
    updateStillness(small);
    lockedTick(gray, small);
    if (!_anchor) {
        return false;
    }
    h = _anchor->hView;
    epoch = _anchorEpoch;
    contentVersion = _liveContentVersion;
    return true;
}

bool Engine::probeStillness(const uint8_t* rgba, int width, int height,
                            int rotationDegrees) {
    // try_lock: the acquire worker may hold the engine for ~0.5 s; dropping a
    // probe frame is harmless, stalling the presenter is not.
    std::unique_lock<std::mutex> lock(_mutex, std::try_to_lock);
    if (!lock.owns_lock()) return false;
    cv::Mat gray = toUprightGray(rgba, width, height, rotationDegrees);
    updateStillness(smallGray(gray));
    return true;
}

} // namespace ocr
