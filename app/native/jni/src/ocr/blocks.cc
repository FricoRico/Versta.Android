//
// Paragraph block grouping: detected lines merge into blocks, one translation
// unit per block — the Kotlin side wraps the block's translated text back into
// the block's per-line quads instead of shrink-fitting one long string per
// line.
//
// O(n^2) union-find over the reference's live pair predicate
// (translator-core/ocr.rs live_lines_should_merge_in_quadrant +
// group_live_lines_into_blocks_in_quadrant): geometry evaluated in each line's
// reading frame via referenceAngle (u along reading, v down the text stack),
// heading barrier on lines ending in !/?, digits/punct-only measurement tokens
// never merge. Lines are re-sorted so each block's lines are consecutive in
// reading order (blockId asc, v asc).
//

#include <algorithm>
#include <array>
#include <cfloat>
#include <cmath>
#include <numeric>
#include <unordered_map>

#include "include/ocr_pipeline.h"

namespace ocr {

// Pair-merge thresholds (translator-core/ocr.rs live_lines_should_merge_in_quadrant).
constexpr float BLOCK_GAP_MIN_H = -0.75f;   // > this x big height of v-overlap required
constexpr float BLOCK_GAP_MAX_H = 4.25f;    // < this x big height of whitespace allowed
constexpr float BLOCK_CENTER_ALIGN_W = 0.25f; // center-line drift, x max width
constexpr float BLOCK_EDGE_ALIGN_H = 2.0f;    // left/right edge drift, x big height
constexpr float BLOCK_SIMILAR_WIDTH = 1.8f;   // max_w / min_w for edge alignment
constexpr float BLOCK_STRONG_CENTER_W = 0.12f;// strongly centered drift, x max width
constexpr float BLOCK_VERY_CLOSE_H = 1.25f;   // gap for the 2.2x height-ratio exception
constexpr float BLOCK_HEIGHT_RATIO = 1.8f;    // big_h / small_h
constexpr float BLOCK_HEIGHT_RATIO_CENTERED = 2.2f;

namespace {

/// Reading-frame geometry of one line: u along the reading axis, v down the
/// text stack, both through the tight rect's center.
struct LineFrame {
    int quadrant; // 90-degree-snapped reading quadrant; different q never merges
    float u, v, w, h;
};

LineFrame frameOf(const TextLine& line) {
    const float a = line.box.referenceAngle;
    const float c = std::cos(a), s = std::sin(a);
    const float cx = line.box.tight.cx, cy = line.box.tight.cy;
    return {
        static_cast<int>(std::lround(a / (CV_PI / 2.0f)) % 4 + 4) % 4,
        cx * c + cy * s,
        -cx * s + cy * c,
        std::max(line.box.tight.width, 1.0f),
        std::max(line.box.tight.height, 1.0f),
    };
}

/// Digits/punct-only lines (measurements, times, scores) stay singleton
/// blocks — merging them into prose ruins both translation and layout.
bool measurementToken(const TextLine& line) {
    if (line.text.empty()) return false;
    return std::all_of(line.text.begin(), line.text.end(), [](char32_t c) {
        return c == U' ' || (c >= U'0' && c <= U'9') ||
               c == U'.' || c == U',' || c == U':' || c == U';' || c == U'%' ||
               c == U'-' || c == U'+' || c == U'/' || c == U'\u2103';
    });
}

bool headingBarrier(const TextLine& line) {
    // A line ending in ! or ? closes its section: nothing merges across its
    // lower edge (reference: heading barrier rows).
    for (auto it = line.text.rbegin(); it != line.text.rend(); ++it) {
        if (*it == U' ') continue;
        return *it == U'!' || *it == U'?';
    }
    return false;
}

bool shouldMerge(const LineFrame& a, const LineFrame& b) {
    const float bigH = std::max(a.h, b.h);
    const float smallH = std::min(a.h, b.h);
    const float maxW = std::max(a.w, b.w);
    const float minW = std::min(a.w, b.w);

    const float gap = std::abs(b.v - a.v) - (a.h + b.h) * 0.5f;
    if (gap < BLOCK_GAP_MIN_H * bigH || gap > BLOCK_GAP_MAX_H * bigH) return false;

    const float ratio = bigH / smallH;

    const float centerDrift = std::abs(a.u - b.u);
    const bool centerAligned = centerDrift <= maxW * BLOCK_CENTER_ALIGN_W;

    const float edgeTol = bigH * BLOCK_EDGE_ALIGN_H;
    const bool similarWidth = maxW <= minW * BLOCK_SIMILAR_WIDTH;
    const bool leftAligned =
        similarWidth && std::abs((a.u - a.w / 2) - (b.u - b.w / 2)) <= edgeTol;
    const bool rightAligned =
        similarWidth && std::abs((a.u + a.w / 2) - (b.u + b.w / 2)) <= edgeTol;

    const bool stronglyCentered = centerDrift <= maxW * BLOCK_STRONG_CENTER_W;
    const bool veryClose = gap <= bigH * BLOCK_VERY_CLOSE_H;
    const bool heightCompatible =
        ratio <= BLOCK_HEIGHT_RATIO ||
        (ratio <= BLOCK_HEIGHT_RATIO_CENTERED && stronglyCentered && veryClose);
    if (!heightCompatible) return false;

    return centerAligned || leftAligned || rightAligned;
}

struct UnionFind {
    std::vector<int> parent;
    explicit UnionFind(size_t n) : parent(n) { std::iota(parent.begin(), parent.end(), 0); }
    int find(int x) {
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }
        return x;
    }
    void unite(int a, int b) { parent[find(a)] = find(b); }
};

/// Shortest signed deviation a−b wrapped to ±90° (box tilts are 180°-ambiguous).
float shortestTilt(float a, float b) {
    float d = std::fmod(a - b, static_cast<float>(CV_PI));
    if (d > CV_PI / 2.0f) d -= static_cast<float>(CV_PI);
    if (d < -CV_PI / 2.0f) d += static_cast<float>(CV_PI);
    return d;
}

/// One block's rect snap (lines[lo..hi), consecutive after assignBlocks):
/// width-weighted shared angle + shared column centerline (u), each line
/// keeping its own width, stack position (v center) and height — own widths
/// keep the erase envelope per-line-sized AND preserve the perspective
/// foreshortening of far lines on an oblique page. Aborted when any line
/// tilts over the drift gate (skewed object, not one column).
void snapRange(std::vector<TextLine>& lines, size_t lo, size_t hi) {
    constexpr float MAX_DRIFT = 10.0f * static_cast<float>(CV_PI) / 180.0f;

    const float anchor = lines[lo].box.tight.angle;
    double devWSum = 0.0, wSum = 0.0;
    for (size_t k = lo; k < hi; k++) {
        const double w = std::max(lines[k].box.tight.width, 1.0f);
        devWSum += w * shortestTilt(lines[k].box.tight.angle, anchor);
        wSum += w;
    }
    const float angle = anchor + static_cast<float>(devWSum / wSum);
    for (size_t k = lo; k < hi; k++) {
        if (std::fabs(shortestTilt(lines[k].box.tight.angle, angle)) > MAX_DRIFT) return;
    }

    const float ca = std::cos(angle), sa = std::sin(angle);
    std::vector<float> uK(hi - lo);
    std::vector<float> vCenter(hi - lo);
    for (size_t k = lo; k < hi; k++) {
        const OrientedRect& t = lines[k].box.tight;
        const float lca = std::cos(t.angle), lsa = std::sin(t.angle);
        const float hw = t.width / 2.0f, hh = t.height / 2.0f;
        float vLo = FLT_MAX, vHi = -FLT_MAX;
        for (int px : {-1, 1}) {
            for (int py : {-1, 1}) {
                const float x = t.cx + lca * (px * hw) - lsa * (py * hh);
                const float y = t.cy + lsa * (px * hw) + lca * (py * hh);
                const float v = -x * sa + y * ca;
                vLo = std::min(vLo, v);
                vHi = std::max(vHi, v);
            }
        }
        vCenter[k - lo] = (vLo + vHi) / 2.0f;
        // Along-axis anchor: keep the line's own u. Centering short lines on
        // the block's shared axis shifts them sideways off their real ink —
        // left-aligned text ghosts at line starts go with that shift.
        uK[k - lo] = t.cx * ca + t.cy * sa;
    }

    for (size_t k = lo; k < hi; k++) {
        OrientedRect& t = lines[k].box.tight;
        t.cx = uK[k - lo] * ca - vCenter[k - lo] * sa;
        t.cy = uK[k - lo] * sa + vCenter[k - lo] * ca;
        // Own width/height retained: a union width would both blow the erase
        // envelope up into full-width slabs for narrow lines, and erase the
        // legal perspective foreshortening of the far lines on an oblique
        // page — pickups that inherit either show text outside the paper plane.
        t.angle = angle;
    }
}

} // namespace

void Engine::assignBlocks(std::vector<TextLine>& lines) {
    const size_t n = lines.size();
    if (n < 2) {
        for (size_t i = 0; i < n; i++) lines[i].blockId = static_cast<int>(i);
        return;
    }

    std::vector<LineFrame> frames(n);
    for (size_t i = 0; i < n; i++) frames[i] = frameOf(lines[i]);

    UnionFind uf(n);
    for (size_t i = 0; i < n; i++) {
        if (measurementToken(lines[i])) continue;
        for (size_t j = i + 1; j < n; j++) {
            if (measurementToken(lines[j])) continue;
            if (frames[i].quadrant != frames[j].quadrant) continue;
            if (!shouldMerge(frames[i], frames[j])) continue;
            // Heading barrier: the upper line (smaller v) closes its section.
            const TextLine& upper = frames[i].v <= frames[j].v ? lines[i] : lines[j];
            if (headingBarrier(upper)) continue;
            uf.unite(static_cast<int>(i), static_cast<int>(j));
        }
    }

    // Number blocks in reading order: key = block's min (v, u).
    std::unordered_map<int, int> idOf;
    std::vector<std::array<float, 3>> order; // {minV, minU, root}
    order.reserve(n);
    for (size_t i = 0; i < n; i++) {
        const int root = uf.find(static_cast<int>(i));
        auto [it, fresh] = idOf.try_emplace(root, static_cast<int>(order.size()));
        if (fresh) order.push_back({frames[i].v, frames[i].u, static_cast<float>(root)});
        auto& o = order[idOf[root]];
        o[0] = std::min(o[0], frames[i].v);
        o[1] = std::min(o[1], frames[i].u);
    }
    std::sort(order.begin(), order.end());
    std::unordered_map<int, int> blockIdOf;
    for (size_t k = 0; k < order.size(); k++) {
        blockIdOf[static_cast<int>(order[k][2])] = static_cast<int>(k);
    }
    for (size_t i = 0; i < n; i++) {
        lines[i].blockId = blockIdOf[uf.find(static_cast<int>(i))];
    }

    // Consecutive same-block lines, reading order within each block (v asc).
    std::stable_sort(lines.begin(), lines.end(), [](const TextLine& la, const TextLine& lb) {
        if (la.blockId != lb.blockId) return la.blockId < lb.blockId;
        const float a = la.box.referenceAngle, b = lb.box.referenceAngle;
        const float vA = -la.box.tight.cx * std::sin(a) + la.box.tight.cy * std::cos(a);
        const float vB = -lb.box.tight.cx * std::sin(b) + lb.box.tight.cy * std::cos(b);
        if (std::abs(vA - vB) > 1e-3f) return vA < vB;
        return la.box.tight.cx * std::cos(a) + la.box.tight.cy * std::sin(a)
             < lb.box.tight.cx * std::cos(b) + lb.box.tight.cy * std::sin(b);
    });

}

// Canonical block rect snap (reference normalize_block_visuals_rotated_basis,
// minus its width union — that part is only valid in a rectified surface
// space): once per acquire, each block's lines adopt the width-weighted shared
// reading angle and column centerline in canonical space — column-tidy
// geometry WITHOUT the old screen-space rectangle rebuild (Kotlin OcrLineSnap),
// which per-frame erased exactly the projective skew the overlay should keep.
// Own widths/heights survive: the foreshortening of far lines on an oblique
// page is real perspective, and the erase envelope stays per-line-sized.
void Engine::snapBlockTightRects(std::vector<TextLine>& lines) {
    size_t lo = 0;
    while (lo < lines.size()) {
        size_t hi = lo + 1;
        while (hi < lines.size() && lines[hi].blockId == lines[lo].blockId) hi++;
        if (hi - lo >= 2) snapRange(lines, lo, hi);
        lo = hi;
    }
}

} // namespace ocr
