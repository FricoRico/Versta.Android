//
// Anchor tracking primitives: FAST-9 corners with subpixel refinement, oriented
// BRIEF-256 descriptors, Lowe-ratio brute matching, PROSAC RANSAC homography
// with prior-penalty scoring + adaptive refit, and the 8-DoF homography EKF.
//
// Ports of translator-tracker (planar_tracker.rs) and translator-core
// (homography_ekf.rs); constants mirror their TrackerConfig/Default impls.
// opencv-mobile ships no calib3d, so all geometry lives in homography.cc.
//

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <limits>
#include <thread>
#include <utility>

#include "include/ocr_pipeline.h"

namespace ocr {
namespace anchor {

// Chunked parallel fan-out over [0, n): fn(lo, hi, chunk) fills its per-worker
// chunk; chunks concatenate in worker order (near-border survivors keep their
// ascending index order).
template <typename T, typename Fn>
std::vector<T> parallelChunks(size_t n, Fn&& fn) {
    const int workers =
        std::max(1, std::min(4, static_cast<int>(std::thread::hardware_concurrency())));
    std::vector<std::vector<T>> chunks(workers);
    std::vector<std::thread> pool;
    pool.reserve(workers);
    for (int wi = 0; wi < workers; wi++) {
        const size_t a = n * wi / workers, b = n * (wi + 1) / workers;
        auto& chunk = chunks[wi];
        pool.emplace_back([&, a, b] { fn(a, b, chunk); });
    }
    for (auto& t : pool) t.join();
    std::vector<T> out;
    for (auto& chunk : chunks) {
        out.insert(out.end(), std::make_move_iterator(chunk.begin()),
                   std::make_move_iterator(chunk.end()));
    }
    return out;
}

using Pair = std::array<float, 4>; // {ax, ay, bx, by} with (ax,ay) -> (bx,by)

namespace {

// ---- TrackerConfig::default() (planar_tracker.rs:175) ----
constexpr int FAST_THRESHOLD = 15;
constexpr int FAST_THRESHOLD_FALLBACK = 7;
constexpr int FAST_MIN_KEYPOINTS = 200;
constexpr int MAX_FEATURES = 500;
constexpr int NMS_RADIUS = 3;
constexpr int KEYPOINT_BORDER = 24;
constexpr int BRIEF_PATCH_RADIUS = 15;

static_assert(BRIEF_PATCH_RADIUS + 1 < KEYPOINT_BORDER,
              "rotated BRIEF samples must stay inside the keypoint border");

constexpr int RANSAC_ITERS = 400;
constexpr float RANSAC_RESIDUAL_PX = 4.0f;
constexpr float PRIOR_PENALTY_PER_PX = 1.0f;
constexpr float PRIOR_PENALTY_DEAD_PX = 8.0f;
constexpr float PRIOR_PENALTY_MAX_INLIERS = 30.0f;
constexpr float PRIOR_SHORT_CIRCUIT_FRAC = 0.9f;

constexpr uint64_t RANSAC_SEED = 0xA5A55A5A3C3CC3C3ull;
constexpr uint64_t PATTERN_SEED = 0x123456789ABCDEF0ull;

// ---- xorshift64 (planar_tracker.rs SmallRng + pattern generator) ----
uint64_t xorshift64(uint64_t x) {
    if (x == 0) x = 0xDEADBEEFCAFEBABEull;
    x ^= x << 13;
    x ^= x >> 7;
    x ^= x << 17;
    return x;
}

struct SmallRng {
    uint64_t state;
    explicit SmallRng(uint64_t seed) : state(seed) {}
    uint32_t nextU32() {
        state = xorshift64(state);
        return static_cast<uint32_t>(state);
    }
};

/// Best/second-best Hamming accumulator for Lowe ratio matching: keeps the
/// two smallest distances and the index of the best so callers can apply the
/// ratio gate without scanning twice.
struct BestSecond {
    int best = std::numeric_limits<int>::max();
    int second = std::numeric_limits<int>::max();
    int bestIdx = 0;
    void add(int d, int idx) {
        if (d < best) {
            second = best;
            best = d;
            bestIdx = idx;
        } else if (d < second) {
            second = d;
        }
    }
    bool accepted(float ratio) const {
        return static_cast<float>(best) < ratio * static_cast<float>(second);
    }
};

// ---- BRIEF sampling pattern, generated once (generate_brief_pattern) ----
struct BriefPair { int8_t ax, ay, bx, by; };

const std::array<BriefPair, 256>& briefPattern() {
    static const std::array<BriefPair, 256> pattern = [] {
        std::array<BriefPair, 256> out{};
        uint64_t state = PATTERN_SEED;
        constexpr uint32_t span = 2 * BRIEF_PATCH_RADIUS + 1; // 31
        auto uniform = [&]() -> int8_t {
            state = xorshift64(state);
            return static_cast<int8_t>(
                static_cast<int32_t>(static_cast<uint32_t>(state) % span) - BRIEF_PATCH_RADIUS);
        };
        for (auto& p : out) {
            p = {uniform(), uniform(), uniform(), uniform()};
        }
        return out;
    }();
    return pattern;
}

// ---------------------------------------------------------------------------
// FAST-9 (detect_fast / fast9_test / has_run / nms_filter / subpixel refine)
// ---------------------------------------------------------------------------

const int8_t CIRCLE[16][2] = {
    {0, -3}, {1, -3}, {2, -2}, {3, -1}, {3, 0}, {3, 1}, {2, 2}, {1, 3},
    {0, 3},  {-1, 3}, {-2, 2}, {-3, 1}, {-3, 0}, {-3, -1}, {-2, -2}, {-1, -3},
};

struct FastPoint {
    float x, y;
    int score;
};

inline bool hasRun(const bool* b) {
    int run = 0, total = 0;
    // 16 circle flags plus the run's first 8 wrapped around the seam.
    for (int i = 0; i < 16 + 8; i++) {
        if (b[i % 16]) {
            run++;
            total = std::max(total, run);
            if (total >= 9) return true;
        } else {
            run = 0;
        }
    }
    return false;
}

inline int fastSadAt(const uint8_t* buf, int w, int x, int y) {
    const int c = buf[static_cast<size_t>(y) * w + x];
    int s = 0;
    for (auto& o : CIRCLE) {
        s += std::abs(buf[static_cast<size_t>(y + o[1]) * w + (x + o[0])] - c);
    }
    return s;
}

void scanRows(const uint8_t* buf, int w, int xFrom, int xTo, int ya, int yb,
              int t, std::vector<FastPoint>& out) {
    for (int y = ya; y < yb; y++) {
        const uint8_t* rowY = buf + static_cast<size_t>(y) * w;
        for (int x = xFrom; x < xTo; x++) {
            const int c = rowY[x];
            const int hi = c + t, lo = c - t;
            // Early reject on the compass points (p1, p5, p9, p13).
            const int p1 = buf[static_cast<size_t>(y - 3) * w + x];
            const int p5 = rowY[x + 3];
            const int p9 = buf[static_cast<size_t>(y + 3) * w + x];
            const int p13 = rowY[x - 3];
            const int nHi = (p1 > hi) + (p5 > hi) + (p9 > hi) + (p13 > hi);
            const int nLo = (p1 < lo) + (p5 < lo) + (p9 < lo) + (p13 < lo);
            if (nHi < 3 && nLo < 3) continue;

            bool bright[16], dark[16];
            int score = 0;
            for (int k = 0; k < 16; k++) {
                const int v = buf[static_cast<size_t>(y + CIRCLE[k][1]) * w + (x + CIRCLE[k][0])];
                bright[k] = v > hi;
                dark[k] = v < lo;
                score += std::abs(v - c);
            }
            if (hasRun(bright) || hasRun(dark)) {
                out.push_back({static_cast<float>(x), static_cast<float>(y), score});
            }
        }
    }
}

std::vector<FastPoint> detectFastRaw(const cv::Mat& gray, int threshold) {
    const int w = gray.cols, h = gray.rows;
    if (w < 2 * KEYPOINT_BORDER + 1 || h < 2 * KEYPOINT_BORDER + 1) return {};

    const int xFrom = KEYPOINT_BORDER, xTo = w - KEYPOINT_BORDER;
    const int ya = KEYPOINT_BORDER, yb = h - KEYPOINT_BORDER;
    const uint8_t* buf = gray.data;

    return parallelChunks<FastPoint>(yb - ya, [&](size_t a, size_t b, auto& chunk) {
        scanRows(buf, w, xFrom, xTo, ya + a, ya + b, threshold, chunk);
    });
}

std::vector<FastPoint> nmsAndRefine(const cv::Mat& gray, std::vector<FastPoint> pts) {
    std::sort(pts.begin(), pts.end(),
              [](const FastPoint& a, const FastPoint& b) { return a.score > b.score; });
    const uint8_t* buf = gray.data;
    const int w = gray.cols, h = gray.rows;
    const float r2 = static_cast<float>(NMS_RADIUS * NMS_RADIUS);

    std::vector<FastPoint> kept;
    kept.reserve(std::min<size_t>(MAX_FEATURES, pts.size()));
    for (auto& kp : pts) {
        bool tooClose = false;
        for (auto& o : kept) {
            const float dx = o.x - kp.x, dy = o.y - kp.y;
            if (dx * dx + dy * dy <= r2) { tooClose = true; break; }
        }
        if (tooClose) continue;

        // Subpixel: parabola peak on the SAD response across the 4 cardinal
        // neighbours, clamped to ±0.5 px (refine_keypoint_subpixel).
        const int kx = static_cast<int>(kp.x), ky = static_cast<int>(kp.y);
        if (kx >= 4 && kx < w - 4 && ky >= 4 && ky < h - 4) {
            const int sC = fastSadAt(buf, w, kx, ky);
            const int sL = fastSadAt(buf, w, kx - 1, ky);
            const int sR = fastSadAt(buf, w, kx + 1, ky);
            const int sU = fastSadAt(buf, w, kx, ky - 1);
            const int sD = fastSadAt(buf, w, kx, ky + 1);
            const float denomX = static_cast<float>(sL + sR - 2 * sC);
            const float denomY = static_cast<float>(sU + sD - 2 * sC);
            if (denomX < -1e-3f) {
                kp.x += std::clamp(0.5f * static_cast<float>(sL - sR) / denomX, -0.5f, 0.5f);
            }
            if (denomY < -1e-3f) {
                kp.y += std::clamp(0.5f * static_cast<float>(sU - sD) / denomY, -0.5f, 0.5f);
            }
        }

        kept.push_back(kp);
        if (kept.size() >= static_cast<size_t>(MAX_FEATURES)) break;
    }
    return kept;
}

// ---------------------------------------------------------------------------
// Oriented BRIEF-256 (patch_orientation / describe_brief)
// ---------------------------------------------------------------------------

float patchOrientation(const uint8_t* buf, int w, int cx, int cy) {
    const int r = BRIEF_PATCH_RADIUS, rSq = r * r;
    int m10 = 0, m01 = 0;
    for (int dy = -r; dy <= r; dy++) {
        const uint8_t* row = buf + static_cast<size_t>(cy + dy) * w;
        for (int dx = -r; dx <= r; dx++) {
            if (dx * dx + dy * dy > rSq) continue;
            const int v = row[cx + dx];
            m10 += dx * v;
            m01 += dy * v;
        }
    }
    if (m10 == 0 && m01 == 0) return 0.0f;
    return std::atan2(static_cast<float>(m01), static_cast<float>(m10));
}

// 3x3 box blur at integer offset (bounds guaranteed by KEYPOINT_BORDER).
inline uint8_t blur9(const uint8_t* buf, int w, int cx, int cy, int dx, int dy) {
    const int x = cx + dx, y = cy + dy;
    uint32_t acc = 0;
    for (int oy = -1; oy <= 1; oy++) {
        const uint8_t* row = buf + static_cast<size_t>(y + oy) * w + x;
        acc += row[-1] + row[0] + row[1];
    }
    return static_cast<uint8_t>(acc / 9);
}

// ---------------------------------------------------------------------------
// Descriptor matching (match_descriptors: brute best/second + Lowe ratio)
// ---------------------------------------------------------------------------

inline int hamming(const Descriptor& a, const Descriptor& b) {
    uint64_t wa[BRIEF_BYTES / 8], wb[BRIEF_BYTES / 8];
    std::memcpy(wa, a.data(), BRIEF_BYTES);
    std::memcpy(wb, b.data(), BRIEF_BYTES);
    int d = 0;
    for (size_t k = 0; k < BRIEF_BYTES / 8; k++) {
        d += __builtin_popcountll(wa[k] ^ wb[k]);
    }
    return d;
}

// ---------------------------------------------------------------------------
// RANSAC scoring helpers
// ---------------------------------------------------------------------------

// AABB corners of the anchor side of the pairs (pairs_anchor_aabb_corners).
std::array<std::array<float, 2>, 4> anchorAabbCorners(const std::vector<Pair>& pairs) {
    float minX = std::numeric_limits<float>::infinity();
    float minY = std::numeric_limits<float>::infinity();
    float maxX = -std::numeric_limits<float>::infinity();
    float maxY = -std::numeric_limits<float>::infinity();
    for (const auto& p : pairs) {
        minX = std::min(minX, p[0]);
        minY = std::min(minY, p[1]);
        maxX = std::max(maxX, p[0]);
        maxY = std::max(maxY, p[1]);
    }
    if (!std::isfinite(minX) || !std::isfinite(maxX)) {
        return {{{0, 0}, {0, 0}, {0, 0}, {0, 0}}};
    }
    return {{{minX, minY}, {maxX, minY}, {maxX, maxY}, {minX, maxY}}};
}

float maxCornerDeltaPx(const hmat::H9& a, const hmat::H9& b,
                       const std::array<std::array<float, 2>, 4>& corners) {
    float max = 0;
    for (const auto& c : corners) {
        float ax, ay, bx, by;
        if (!hmat::project(a, c[0], c[1], ax, ay)) continue;
        if (!hmat::project(b, c[0], c[1], bx, by)) continue;
        const float dx = ax - bx, dy = ay - by;
        max = std::max(max, std::sqrt(dx * dx + dy * dy));
    }
    return max;
}

float adjustedInlierScore(size_t inliers, const hmat::H9& h, const hmat::H9* prior,
                          const std::array<std::array<float, 2>, 4>& corners) {
    const float raw = static_cast<float>(inliers);
    if (!prior) return raw;
    const float delta = maxCornerDeltaPx(h, *prior, corners);
    const float aboveDead = std::max(0.0f, delta - PRIOR_PENALTY_DEAD_PX);
    const float penalty = std::min(PRIOR_PENALTY_PER_PX * aboveDead, PRIOR_PENALTY_MAX_INLIERS);
    return raw - penalty;
}

float medianResidual(const hmat::H9& h, const std::vector<Pair>& pairs) {
    std::vector<float> residuals;
    residuals.reserve(pairs.size());
    for (const auto& p : pairs) {
        float px, py;
        if (!hmat::project(h, p[0], p[1], px, py)) continue;
        const float dx = px - p[2], dy = py - p[3];
        residuals.push_back(std::sqrt(dx * dx + dy * dy));
    }
    if (residuals.empty()) return std::numeric_limits<float>::infinity();
    std::sort(residuals.begin(), residuals.end());
    return residuals[residuals.size() / 2];
}

} // namespace

// ---------------------------------------------------------------------------
// Public: feature extraction (compute_frame_features = FAST + fallback + BRIEF)
// ---------------------------------------------------------------------------

FeatureSet computeFeatures(const cv::Mat& gray) {
    std::vector<FastPoint> pts = detectFastRaw(gray, FAST_THRESHOLD);
    static_assert(FAST_THRESHOLD_FALLBACK < FAST_THRESHOLD,
                  "the fallback threshold must be lower than the primary");
    if (pts.size() < static_cast<size_t>(FAST_MIN_KEYPOINTS)) {
        std::vector<FastPoint> retry = detectFastRaw(gray, FAST_THRESHOLD_FALLBACK);
        if (retry.size() > pts.size()) pts = std::move(retry);
    }
    pts = nmsAndRefine(gray, std::move(pts));

    const int w = gray.cols, h = gray.rows;
    const uint8_t* buf = gray.data;
    const auto& pattern = briefPattern();
    const size_t n = pts.size();

    // describe_brief: per-keypoint orientation + rotated pattern sampling.
    // Per-worker chunks cover disjoint ascending index ranges, so concatenating
    // them in worker order preserves index order among near-border survivors.
    using Out = std::pair<cv::Point2f, Descriptor>;
    auto flat = parallelChunks<Out>(n, [&](size_t a, size_t b, std::vector<Out>& chunk) {
            for (size_t i = a; i < b; i++) {
                const auto& kp = pts[i];
                const int cx = static_cast<int>(std::lround(kp.x));
                const int cy = static_cast<int>(std::lround(kp.y));
                if (cx < KEYPOINT_BORDER || cy < KEYPOINT_BORDER ||
                    cx >= w - KEYPOINT_BORDER || cy >= h - KEYPOINT_BORDER) {
                    continue;
                }
                const float angle = patchOrientation(buf, w, cx, cy);
                const float sinA = std::sin(angle), cosA = std::cos(angle);
                Descriptor bytes{};
                for (int k = 0; k < 256; k++) {
                    const auto& p = pattern[k];
                    const int rax = static_cast<int>(std::lround(cosA * p.ax - sinA * p.ay));
                    const int ray = static_cast<int>(std::lround(sinA * p.ax + cosA * p.ay));
                    const int rbx = static_cast<int>(std::lround(cosA * p.bx - sinA * p.by));
                    const int rby = static_cast<int>(std::lround(sinA * p.bx + cosA * p.by));
                    if (blur9(buf, w, cx, cy, rax, ray) < blur9(buf, w, cx, cy, rbx, rby)) {
                        bytes[k / 8] |= static_cast<uint8_t>(1u << (k % 8));
                    }
                }
                chunk.emplace_back(cv::Point2f(kp.x, kp.y), bytes);
            }
    });

    FeatureSet out;
    for (auto& o : flat) {
        out.kps.push_back(o.first);
        out.descs.push_back(o.second);
    }
    return out;
}

std::vector<DescMatch> matchLowe(const std::vector<Descriptor>& anchorDescs,
                                 const std::vector<Descriptor>& frameDescs, float ratio) {
    if (frameDescs.size() < 2 || anchorDescs.empty()) return {};
    const size_t n = anchorDescs.size();
    return parallelChunks<DescMatch>(n, [&](size_t a, size_t b, std::vector<DescMatch>& chunk) {
            for (size_t ai = a; ai < b; ai++) {
                BestSecond bs;
                for (size_t fi = 0; fi < frameDescs.size(); fi++) {
                    bs.add(hamming(anchorDescs[ai], frameDescs[fi]),
                           static_cast<int>(fi));
                }
                if (bs.accepted(ratio)) {
                    chunk.push_back({static_cast<int>(ai), bs.bestIdx, bs.best});
                }
            }
    });
}

std::vector<DescMatch> matchGuided(const FeatureSet& anchor, const FeatureSet& frame,
                                   float ratio, const hmat::H9& prior, float radiusPx) {
    // Absolute gate for single-candidate windows (reference
    // SINGLE_CANDIDATE_HAMMING_THRESHOLD): random BRIEF-256 distance is
    // ~128 bits, same-patch < 40, so 60 bits is well inside the safe zone.
    constexpr int SINGLE_CANDIDATE_HAMMING = 60;

    if (frame.descs.size() < 2 || anchor.descs.empty()) return {};
    const float rSq = radiusPx * radiusPx;
    const size_t n = anchor.descs.size();
    return parallelChunks<DescMatch>(n, [&](size_t a, size_t b, std::vector<DescMatch>& chunk) {
            for (size_t ai = a; ai < b; ai++) {
                float px, py;
                if (!hmat::project(prior, anchor.kps[ai].x, anchor.kps[ai].y, px, py)) continue;
                BestSecond bs;
                int windowCount = 0;
                for (size_t fi = 0; fi < frame.descs.size(); fi++) {
                    const float dx = frame.kps[fi].x - px;
                    const float dy = frame.kps[fi].y - py;
                    if (dx * dx + dy * dy > rSq) continue;
                    windowCount++;
                    bs.add(hamming(anchor.descs[ai], frame.descs[fi]),
                           static_cast<int>(fi));
                }
                if (windowCount == 0) continue;
                if (windowCount == 1) {
                    if (bs.best < SINGLE_CANDIDATE_HAMMING) {
                        chunk.push_back({static_cast<int>(ai), bs.bestIdx, bs.best});
                    }
                    continue;
                }
                if (bs.accepted(ratio)) {
                    chunk.push_back({static_cast<int>(ai), bs.bestIdx, bs.best});
                }
            }
    });
}

// ---------------------------------------------------------------------------
// Public: PROSAC RANSAC (ransac_homography_with_prior / ransac_similarity)
// ---------------------------------------------------------------------------

/// Index set of pairs within rThresh of the homography (projection failures
/// silently skip — those points never get a safe image-space comparison).
static std::vector<size_t> collectInliers(const hmat::H9& h,
                                          const std::vector<Pair>& pairs,
                                          float rThreshSq) {
    std::vector<size_t> idx;
    for (size_t i = 0; i < pairs.size(); i++) {
        float px, py;
        if (!hmat::project(h, pairs[i][0], pairs[i][1], px, py)) continue;
        const float dx = px - pairs[i][2], dy = py - pairs[i][3];
        if (dx * dx + dy * dy <= rThreshSq) idx.push_back(i);
    }
    return idx;
}

/// Materialize the pair list at the given index set (prospective inliers).
static std::vector<Pair> gatherPairs(const std::vector<Pair>& pairs,
                                     const std::vector<size_t>& idx) {
    std::vector<Pair> out;
    out.reserve(idx.size());
    for (auto i : idx) out.push_back(pairs[i]);
    return out;
}

/// Adaptive re-fit by inlier count: full homography >= 30 inliers, affine
/// >= 15, else similarity (sparse-inlier homography is under-constrained and
/// jitters). Packs the result on success.
static bool refitAdaptive(std::vector<Pair> inlierPairs, TrackFit& out) {
    hmat::H9 refined;
    bool ok;
    if (inlierPairs.size() >= 30) {
        ok = hmat::fitHomography(inlierPairs, refined);
    } else if (inlierPairs.size() >= 15) {
        ok = hmat::fitAffine(inlierPairs, refined);
    } else {
        ok = hmat::fitSimilarity(inlierPairs, refined);
    }
    if (!ok) return false;

    out.h = refined;
    out.inliers = static_cast<int>(inlierPairs.size());
    out.medianResidualPx = medianResidual(refined, inlierPairs);
    out.inlierPairs = std::move(inlierPairs);
    return true;
}

bool ransacHomography(const std::vector<Pair>& pairs, const hmat::H9* prior,
                      int minInliers, TrackFit& out) {
    if (pairs.size() < 4) return false;
    SmallRng rng(RANSAC_SEED);
    hmat::H9 bestH{};
    bool haveBest = false;
    std::vector<size_t> bestIdx, priorIdx;
    float bestScore = -std::numeric_limits<float>::infinity();
    const float rThreshSq = RANSAC_RESIDUAL_PX * RANSAC_RESIDUAL_PX;
    const size_t n = pairs.size();
    const int priorMin = std::max(minInliers / 2, 1);
    const auto corners = anchorAabbCorners(pairs);

    // Prior support gate: only trust the prior when current matches still back
    // it (min_inliers/2); a stale prior must not seed the search.
    hmat::H9 priorH{};
    bool havePrior = false;
    if (prior) {
        priorH = *prior;
        priorIdx = collectInliers(priorH, pairs, rThreshSq);
        if (static_cast<int>(priorIdx.size()) >= priorMin) {
            havePrior = true;
            haveBest = true;
            bestScore = static_cast<float>(priorIdx.size());
            bestIdx = priorIdx;
            bestH = priorH;
        }
    }
    const hmat::H9* priorForScoring = havePrior ? &priorH : nullptr;

    // PROSAC: sample within a growing top-quality prefix — pairs arrive sorted
    // by descriptor-match distance (best first).
    const int quarter = std::max(RANSAC_ITERS / 4, 1);
    for (int t = 0; t < RANSAC_ITERS; t++) {
        const int phase = std::min(t / quarter, 3);
        size_t cap;
        switch (phase) {
            case 0: cap = std::min(n, std::max(n / 4, static_cast<size_t>(8))); break;
            case 1: cap = std::min(n, std::max(n / 2, static_cast<size_t>(8))); break;
            case 2: cap = std::min(n, std::max(3 * n / 4, static_cast<size_t>(8))); break;
            default: cap = n; break;
        }
        std::vector<Pair> sample(4);
        size_t used[4];
        for (int k = 0; k < 4; k++) {
            for (;;) {
                const size_t c = rng.nextU32() % cap;
                bool seen = false;
                for (int j = 0; j < k; j++) {
                    if (used[j] == c) { seen = true; break; }
                }
                if (!seen) {
                    used[k] = c;
                    sample[k] = pairs[c];
                    break;
                }
            }
        }
        hmat::H9 h;
        if (!hmat::fitHomography(sample, h)) continue;

        std::vector<size_t> idx = collectInliers(h, pairs, rThreshSq);
        if (idx.empty()) continue;

        const float score = adjustedInlierScore(idx.size(), h, priorForScoring, corners);
        if (score > bestScore) {
            bestScore = score;
            bestIdx = idx;
            bestH = h;
            haveBest = true;
        }
    }

    // Prior-as-seed short-circuit: refit the prior when it explains at least
    // PRIOR_SHORT_CIRCUIT_FRAC of the best sample's support.
    if (havePrior && !bestIdx.empty()) {
        const float frac =
            static_cast<float>(priorIdx.size()) / static_cast<float>(bestIdx.size());
        if (frac >= PRIOR_SHORT_CIRCUIT_FRAC) {
            bestIdx = priorIdx;
            bestH = priorH;
        }
    }
    if (!haveBest || static_cast<int>(bestIdx.size()) < minInliers) return false;

    std::vector<Pair> inlierPairs = gatherPairs(pairs, bestIdx);
    return refitAdaptive(std::move(inlierPairs), out);
}

bool ransacSimilarity(const std::vector<Pair>& pairs, int minInliers, hmat::H9& out) {
    if (pairs.size() < 2) return false;
    SmallRng rng(RANSAC_SEED);
    const float rThreshSq = RANSAC_RESIDUAL_PX * RANSAC_RESIDUAL_PX;
    const size_t n = pairs.size();

    std::vector<size_t> bestIdx;
    for (int t = 0; t < RANSAC_ITERS; t++) {
        size_t i0 = rng.nextU32() % n;
        size_t i1 = rng.nextU32() % n;
        while (i1 == i0) i1 = rng.nextU32() % n;
        const std::vector<Pair> sample{pairs[i0], pairs[i1]};
        hmat::H9 h;
        if (!hmat::fitSimilarity(sample, h)) continue;
        std::vector<size_t> idx = collectInliers(h, pairs, rThreshSq);
        if (idx.size() > bestIdx.size()) bestIdx = std::move(idx);
    }
    if (static_cast<int>(bestIdx.size()) < minInliers) return false;

    std::vector<Pair> inliers = gatherPairs(pairs, bestIdx);
    return hmat::fitSimilarity(inliers, out);
}

// ---------------------------------------------------------------------------
// Public: homography EKF (translator-core/homography_ekf.rs)
// ---------------------------------------------------------------------------

constexpr double EKF_Q[8] = {1.0e-4, 1.0e-4, 4.0, 1.0e-4, 1.0e-4, 4.0, 1.0e-9, 1.0e-9};
constexpr double EKF_P0[8] = {1.0e-2, 1.0e-2, 1.0e2, 1.0e-2, 1.0e-2, 1.0e2, 1.0e-7, 1.0e-7};
constexpr double EKF_R = 4.0;

static bool canonicalise(const hmat::H9& h, hmat::H9& out) {
    const float s = h[8];
    if (!std::isfinite(s) || std::fabs(s) < 1e-9f) return false;
    const float inv = 1.0f / s;
    for (int i = 0; i < 8; i++) out[i] = h[i] * inv;
    out[8] = 1.0f;
    for (int i = 0; i < 9; i++) {
        if (!std::isfinite(out[i])) return false;
    }
    return true;
}

HomographyEkf::HomographyEkf(const hmat::H9& h) {
    reset(h);
}

void HomographyEkf::reset(const hmat::H9& h) {
    if (!canonicalise(h, _h)) {
        // Degenerate gauge: fall back to identity rather than dropping the filter.
        _h = hmat::IDENTITY;
    }
    std::memset(_p, 0, sizeof(_p));
    for (int i = 0; i < 8; i++) _p[i][i] = EKF_P0[i];
}

void HomographyEkf::predict() {
    for (int i = 0; i < 8; i++) _p[i][i] += EKF_Q[i];
}

void HomographyEkf::updatePairs(const std::vector<std::array<float, 4>>& pairs) {
    for (const auto& pr : pairs) {
        const double x = pr[0], y = pr[1];
        const double h0 = _h[0], h1 = _h[1], h2 = _h[2];
        const double h3 = _h[3], h4 = _h[4], h5 = _h[5];
        const double h6 = _h[6], h7 = _h[7];
        const double w = h6 * x + h7 * y + 1.0;
        if (!std::isfinite(w) || std::fabs(w) < 1e-9) continue;
        const double invW = 1.0 / w;
        const double u = (h0 * x + h1 * y + h2) * invW;
        const double v = (h3 * x + h4 * y + h5) * invW;

        // d(u)/dh and d(v)/dh (analytic, from projective division).
        const double ju[8] = {x * invW, y * invW, invW, 0, 0, 0,
                              -x * u * invW, -y * u * invW};
        const double jv[8] = {0, 0, 0, x * invW, y * invW, invW,
                              -x * v * invW, -y * v * invW};

        // PJT (8x2) and S = J P J^T + R (2x2).
        double pjt[8][2];
        for (int i = 0; i < 8; i++) {
            double s0 = 0, s1 = 0;
            for (int k = 0; k < 8; k++) {
                s0 += _p[i][k] * ju[k];
                s1 += _p[i][k] * jv[k];
            }
            pjt[i][0] = s0;
            pjt[i][1] = s1;
        }
        double s00 = EKF_R, s01 = 0, s11 = EKF_R;
        for (int k = 0; k < 8; k++) {
            s00 += ju[k] * pjt[k][0];
            s01 += ju[k] * pjt[k][1];
            s11 += jv[k] * pjt[k][1];
        }
        const double det = s00 * s11 - s01 * s01;
        if (!std::isfinite(det) || det <= 0) continue;
        const double invDet = 1.0 / det;
        const double sInv00 = s11 * invDet, sInv11 = s00 * invDet;
        const double sInv01 = -s01 * invDet;

        // K = P J^T S^-1 (8x2).
        double kMat[8][2];
        for (int i = 0; i < 8; i++) {
            kMat[i][0] = pjt[i][0] * sInv00 + pjt[i][1] * sInv01;
            kMat[i][1] = pjt[i][0] * sInv01 + pjt[i][1] * sInv11;
        }

        const double yu = pr[2] - u;
        const double yv = pr[3] - v;

        bool ok = true;
        for (int i = 0; i < 8; i++) {
            const double dh = kMat[i][0] * yu + kMat[i][1] * yv;
            const double nv = static_cast<double>(_h[i]) + dh;
            if (!std::isfinite(nv)) { ok = false; break; }
            _h[i] = static_cast<float>(nv);
        }
        if (!ok) continue;
        // _h[8] is the gauge anchor: exactly 1.

        // P <- (I - K J) P, symmetrised.
        double kj[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                kj[i][j] = kMat[i][0] * ju[j] + kMat[i][1] * jv[j];
            }
        }
        double np[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                double s = _p[i][j];
                for (int k = 0; k < 8; k++) s -= kj[i][k] * _p[k][j];
                np[i][j] = s;
            }
        }
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                const double avg = 0.5 * (np[i][j] + np[j][i]);
                np[i][j] = avg;
                np[j][i] = avg;
            }
        }
        std::memcpy(_p, np, sizeof(_p));
    }
}

} // namespace anchor
} // namespace ocr
