//
// Text erasure + background reconstruction: per line, the padded render
// region is sampled off the canonical frame and the ink replaced in place —
// the reference's "matte + background_field" path. The mask is the UNION of
// every line's matte alpha projected into frame space (stacked lines erase
// each other's strokes inside their padding; without the union a later strip
// composites a neighbour's untouched glyphs back over an erased neighbour).
//
// Fill = coarse block-median colour field (color_matting.rs
// background_field): 10px cells take per-channel medians of unmasked pixels,
// empty cells inherit the nearest populated cell via multi-source BFS, the
// grid is bilinear-upsampled. Linear-time throughout; robust in dense text —
// the reference measured it above a directional inpaint there.
//
// Matte-less lines (no ink model, Otsu-fallback colors) get a distance mask:
// strip pixels farther than FALLBACK_DIST from the sampled paper count as ink.
//
// Patch alpha feathers to 0 over `max(0.25*height, 2)` px at the edges so a
// slightly-off projection slides scene-matching content, never an ink edge.

#include <algorithm>
#include <atomic>
#include <numeric>
#include <cmath>
#include <deque>
#include <thread>

#include "include/ocr_pipeline.h"
#include "include/parallel.h"

namespace ocr {

namespace {

// Reference: color_matting.rs mat_strip_for_detection padding — matched
// verbatim. The tall vertical pad overlaps neighbouring lines by design: the
// strip mask below samples the frame-space union of ALL lines' ink, so the
// neighbour's glyphs inside the pad get erased here too instead of peeking
// out above/below the strip.
constexpr float PAD_X_FRAC = 0.15f;
constexpr float PAD_X_MIN = 4.0f;
constexpr float PAD_Y_FRAC = 0.75f;
constexpr float PAD_Y_MIN = 8.0f;

// Reference: fill_radius(oriented.height). Ours runs wider: the matte band
// is 48 rows over a ~2.4x-kernel box (reference ~1.2x), so the soft skirt
// where the culled alpha falls below INK_CUT still holds visible ink.
constexpr uint8_t UNION_CUT = 24;
constexpr float FILL_RADIUS_FRAC = 0.10f;
constexpr int FILL_RADIUS_MIN = 2;
constexpr int FILL_RADIUS_MAX = 8;

// background_field: cell size and minimum samples for a cell median.
constexpr int BG_BLOCK = 10;
constexpr int BG_CELL_MIN_PX = 4;

// Matte-free ink test: RGB euclidean distance from the line's paper colour.
constexpr float FALLBACK_DIST = 48.0f;

// Graphics kill-switch: a masked strip pixel counts as artwork (not
// typography) when its scene colour is far from BOTH the line's paper and
// ink AND saturated. Loose ink distance first: only pixels nowhere near any
// ink hue qualify, so coloured pens never escape.
constexpr float GRAPHIC_PAPER_DIST = 72.0f;
constexpr float GRAPHIC_INK_DIST = 120.0f;
constexpr int GRAPHIC_SAT_CUT = 50; // max-min channel spread

/// Affine map placing a rect-(width,height) oriented box's content at strip
/// pixel (x,y): frame = center + R(angle) . (x - sw/2, y - sh/2); dst→src for
/// the per-pixel samplers below. Do NOT re-express as warpAffine — the affine
/// warp path silently samples garbage in this pipeline (see the file header).
cv::Matx23f stripToFrame(const OrientedRect& o, int sw, int sh) {
    const float ca = std::cos(o.angle), sa = std::sin(o.angle);
    return {
        ca, -sa, o.cx - (ca * sw / 2.0f - sa * sh / 2.0f),
        sa,  ca, o.cy - (sa * sw / 2.0f + ca * sh / 2.0f),
    };
}

/// Per-pixel projection of one line's matte mask into the union (reference
/// color_matting.rs project_box_ink — no warpAffine; walks the region AABB,
/// samples the mask nearest at pixel centres).
void projectMaskIntoUnion(const cv::Mat& mask, const OrientedRect& region,
                          const cv::Rect& roi, cv::Mat& unionMask) {
    const float ca = std::cos(region.angle), sa = std::sin(region.angle);
    const float hw = region.width / 2.0f, hh = region.height / 2.0f;
    const int mw = mask.cols, mh = mask.rows;
    for (int py = roi.y; py < roi.y + roi.height; py++) {
        auto* urow = unionMask.ptr<uint8_t>(py);
        for (int px = roi.x; px < roi.x + roi.width; px++) {
            const float dx = px + 0.5f - region.cx;
            const float dy = py + 0.5f - region.cy;
            const float u = dx * ca + dy * sa;
            const float v = -dx * sa + dy * ca;
            if (std::fabs(u) > hw || std::fabs(v) > hh) continue;
            const int mx = static_cast<int>(((u + hw) / region.width) * mw);
            const int my = static_cast<int>(((v + hh) / region.height) * mh);
            if (mx < 0 || mx >= mw || my < 0 || my >= mh) continue;
            if (mask.at<uint8_t>(my, mx)) urow[px] = 255;
        }
    }
}

cv::Rect regionAabb(const OrientedRect& r, const cv::Size& frame) {
    Point corners[4];
    rectCorners(r.cx, r.cy, r.angle, r.width, r.height, 0, 0, corners);
    std::vector<cv::Point> pts(4);
    for (int i = 0; i < 4; i++) pts[i] = {(int)std::lround(corners[i].x), (int)std::lround(corners[i].y)};
    return cv::boundingRect(pts) & cv::Rect({0, 0}, frame);
}

uint8_t channelMedian(std::vector<uint8_t>& v) {
    std::nth_element(v.begin(), v.begin() + v.size() / 2, v.end());
    return v[v.size() / 2];
}

/// Block-median colour field over the mask's holes (reference
/// background_field, BG_BLOCK cells + BFS nearest-fill + bilinear upsample).
cv::Mat backgroundField(const cv::Mat& rgb, const cv::Mat& mask) {
    const int cols = rgb.cols, rows = rgb.rows;
    const int gw = (cols + BG_BLOCK - 1) / BG_BLOCK;
    const int gh = (rows + BG_BLOCK - 1) / BG_BLOCK;

    cv::Mat grid(gh, gw, CV_32FC3, cv::Scalar(0, 0, 0));
    cv::Mat populated(gh, gw, CV_8UC1, cv::Scalar(0));

    for (int gy = 0; gy < gh; gy++) {
        for (int gx = 0; gx < gw; gx++) {
            const int x0 = gx * BG_BLOCK, x1 = std::min(x0 + BG_BLOCK, cols);
            const int y0 = gy * BG_BLOCK, y1 = std::min(y0 + BG_BLOCK, rows);
            std::vector<uint8_t> rs, gs, bs;
            for (int y = y0; y < y1; y++) {
                const auto* row = rgb.ptr<cv::Vec3b>(y);
                const auto* mrow = mask.ptr<uint8_t>(y);
                for (int x = x0; x < x1; x++) {
                    if (mrow[x]) continue;
                    rs.push_back(row[x][0]);
                    gs.push_back(row[x][1]);
                    bs.push_back(row[x][2]);
                }
            }
            if (rs.size() < BG_CELL_MIN_PX) continue;
            grid.at<cv::Vec3f>(gy, gx) = {
                static_cast<float>(channelMedian(rs)),
                static_cast<float>(channelMedian(gs)),
                static_cast<float>(channelMedian(bs))};
            populated.at<uint8_t>(gy, gx) = 1;
        }
    }

    if (cv::countNonZero(populated) == 0) {
        // All-ink strip: uniform whole-strip median (reference fallback).
        std::vector<uint8_t> rs, gs, bs;
        rs.reserve(cols * rows); gs.reserve(cols * rows); bs.reserve(cols * rows);
        for (int y = 0; y < rows; y++) {
            const auto* row = rgb.ptr<cv::Vec3b>(y);
            for (int x = 0; x < cols; x++) {
                rs.push_back(row[x][0]); gs.push_back(row[x][1]); bs.push_back(row[x][2]);
            }
        }
        grid.setTo(cv::Scalar(channelMedian(rs), channelMedian(gs), channelMedian(bs)));
        populated.setTo(1);
    } else if (cv::countNonZero(populated) < gw * gh) {
        // Multi-source BFS: an empty cell takes the value of the populated
        // cell that reaches it first (nearest in 4-neigh grid space).
        std::deque<cv::Point> queue;
        for (int gy = 0; gy < gh; gy++) {
            for (int gx = 0; gx < gw; gx++) {
                if (populated.at<uint8_t>(gy, gx)) queue.push_back({gx, gy});
            }
        }
        const int dirs[4][2] = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.empty()) {
            const cv::Point p = queue.front();
            queue.pop_front();
            for (const auto& d : dirs) {
                const int nx = p.x + d[0], ny = p.y + d[1];
                if (nx < 0 || ny < 0 || nx >= gw || ny >= gh) continue;
                if (populated.at<uint8_t>(ny, nx)) continue;
                grid.at<cv::Vec3f>(ny, nx) = grid.at<cv::Vec3f>(p.y, p.x);
                populated.at<uint8_t>(ny, nx) = 1;
                queue.push_back({nx, ny});
            }
        }
    }

    cv::Mat field;
    cv::resize(grid, field, {cols, rows}, 0, 0, cv::INTER_LINEAR);
    return field;
}

/// Feather ramp: 255 inside, linearly to 0 within [feather] px of the border.
cv::Mat edgeFeather(int sw, int sh, float feather) {
    cv::Mat inner(sh, sw, CV_8UC1, cv::Scalar(255));
    inner.row(0).setTo(0);
    inner.row(sh - 1).setTo(0);
    inner.col(0).setTo(0);
    inner.col(sw - 1).setTo(0);
    cv::Mat dist;
    cv::distanceTransform(inner, dist, cv::DIST_L2, 3);
    cv::Mat alpha(sh, sw, CV_8UC1);
    for (int y = 0; y < sh; y++) {
        const float* drow = dist.ptr<float>(y);
        auto* arow = alpha.ptr<uint8_t>(y);
        for (int x = 0; x < sw; x++) {
            arow[x] = static_cast<uint8_t>(std::clamp(drow[x] / feather, 0.0f, 1.0f) * 255.0f);
        }
    }
    return alpha;
}

} // namespace

void Engine::runErase(const cv::Mat& upright, std::vector<TextLine>& lines,
                      const std::vector<DewarpedStrip>& strips) {
    if (lines.empty()) return;
    ++_eraseEpoch;
    _builtStripsThisCall = true;

    // Union ink mask in frame space: every line's matte projected through its
    // render quad (the [quads] silhouette built just below).
    const size_t workers = std::min<size_t>(4, lines.size());

    // Render quads computed ONCE and reused by both the union projection and
    // the per-line workers: the matte alpha (measured on the contour-dewarp
    // strip) is stretched onto the quad the overlay typesets into, so the
    // mask covers exactly the ink the drawn text replaces. Projecting through
    // the strip's dewarp region instead let the wanders of the quadratic
    // spine-fit (up to ~20 px off the snapped tight rect on unrectified live
    // frames) leave glyph tops/start ink unerased.
    std::vector<OrientedRect> quads(lines.size());
    for (size_t i = 0; i < lines.size(); i++) {
        const OrientedRect& t = lines[i].box.tight;
        const float bandH = std::max(t.height, 1.0f);
        const float expandDist = renderExpandDistance(bandH, detPoolCompensationPx());
        OrientedRect o = {
            t.cx, t.cy,
            t.width + 2.0f * expandDist,
            bandH + 2.0f * expandDist,
            t.angle
        };
        o.width = std::max(o.width, 2.0f);
        o.height = std::max(o.height, 2.0f);
        quads[i] = o;
    }

    // band-region affine, so overlapping strip pads erase across lines.
    cv::Mat unionMask = cv::Mat::zeros(upright.rows, upright.cols, CV_8UC1);
    size_t li = 0;
    for (const auto& line : lines) {
        const size_t myIdx = li++;
        if (!line.matte || line.matte->alpha.empty()) continue;
        if (line.stripIdx < 0 || line.stripIdx >= static_cast<int>(strips.size())) continue;
        // Mask projection region: the tight-anchored glyph band (the same
        // envelope the matte strip and contour dewarp share — see
        // stripBandRegion). Binding to the strip's own quadratic-spine region
        // instead let the spine wander (~20 px off on unrectified live
        // frames) and leave glyph tops / line-start ink unerased; binding to
        // the short render quad (tight + expand margin only) vertically
        // squashes the mask onto glyph cores and smears ink outside it.
        const OrientedRect region = stripBandRegion(line.box.tight);

        // Matte alpha lives in canonical-strip space (48 x w); un-rotate into
        // the dewarp band's native orientation.
        cv::Mat mask48(48, line.matte->w, CV_8UC1,
                       const_cast<uint8_t*>(line.matte->alpha.data()));
        cv::Mat mask = rot90(mask48, (4 - line.matte->rot) % 4);
        cv::threshold(mask, mask, UNION_CUT, 255, cv::THRESH_BINARY);

        const cv::Rect roi = regionAabb(region, upright.size());
        if (roi.empty()) continue;
        projectMaskIntoUnion(mask, region, roi, unionMask);
    }

    parallelFor(workers, lines.size(), [&](size_t, size_t i) {
        auto& line = lines[i];
        const OrientedRect& o = quads[i];

        const float padX = std::max(PAD_X_FRAC * o.width, PAD_X_MIN);
        const float padY = std::max(PAD_Y_FRAC * o.height, PAD_Y_MIN);
        const int sw = std::clamp(static_cast<int>(std::lround(o.width + 2 * padX)), 8, 4096);
        const int sh = std::clamp(static_cast<int>(std::lround(o.height + 2 * padY)), 8, 1024);

        const cv::Matx23f m = stripToFrame(o, sw, sh);

        // BORDER_REPLICATE semantics via clamped bilinear taps:
        // border pixels smear into the strip, but the mask pass below
        // marks every out-of-frame sample as ink, so the fill
        // reconstructs those pixels (reference behaviour) and the
        // smear only feeds the field's medians. Explicit per-pixel
        // loop — the warpAffine path sampled garbage in this build
        // (same failure as the union mask; see projectMaskIntoUnion).
        cv::Mat stripRgb(sh, sw, CV_8UC3);
        {
            const int fw = upright.cols, fh = upright.rows;
            for (int y = 0; y < sh; y++) {
                auto* srow = stripRgb.ptr<cv::Vec3b>(y);
                for (int x = 0; x < sw; x++) {
                    const float fx = m(0, 0) * x + m(0, 1) * y + m(0, 2);
                    const float fy = m(1, 0) * x + m(1, 1) * y + m(1, 2);
                    const float cx = std::clamp(fx, 0.0f, static_cast<float>(fw - 1));
                    const float cy = std::clamp(fy, 0.0f, static_cast<float>(fh - 1));
                    const int x0 = std::min(static_cast<int>(cx), fw - 1);
                    const int y0 = std::min(static_cast<int>(cy), fh - 1);
                    const int x1 = std::min(x0 + 1, fw - 1);
                    const int y1 = std::min(y0 + 1, fh - 1);
                    const float ax = cx - x0, ay = cy - y0;
                    const auto* r0 = upright.ptr<cv::Vec3b>(y0);
                    const auto* r1 = upright.ptr<cv::Vec3b>(y1);
                    for (int c = 0; c < 3; c++) {
                        const float v00 = r0[x0][c], v01 = r0[x1][c];
                        const float v10 = r1[x0][c], v11 = r1[x1][c];
                        srow[x][c] = static_cast<uint8_t>(std::lround(
                            v00 + (v01 - v00) * ax + (v10 - v00) * ay +
                            (v00 - v01 - v10 + v11) * ax * ay));
                    }
                }
            }
        }

        // Strip mask from the union: per-pixel nearest sampling (the
        // warpAffine call here read zeros even where the union visibly
        // carried ink — same math, no black box).
        cv::Mat stripMask(sh, sw, CV_8UC1);
        {
            const int fw = unionMask.cols, fh = unionMask.rows;
            for (int y = 0; y < sh; y++) {
                auto* mrow = stripMask.ptr<uint8_t>(y);
                for (int x = 0; x < sw; x++) {
                    const float fx = m(0, 0) * x + m(0, 1) * y + m(0, 2);
                    const float fy = m(1, 0) * x + m(1, 1) * y + m(1, 2);
                    const int fxi = static_cast<int>(std::lround(fx));
                    const int fyi = static_cast<int>(std::lround(fy));
                    if (fxi < 0 || fyi < 0 || fxi >= fw || fyi >= fh) {
                        // Out-of-frame: masked (reference), never
                        // trusted with sampled content.
                        mrow[x] = 255;
                        continue;
                    }
                    mrow[x] = unionMask.at<uint8_t>(fyi, fxi);
                }
            }
        }

        if (!line.matte) {
            // Matte-free: ink = strip pixels far from the paper colour.
            stripMask = cv::Mat::zeros(sh, sw, CV_8UC1);
            const float pr = (line.bgColor >> 16) & 0xFF;
            const float pg = (line.bgColor >> 8) & 0xFF;
            const float pb = line.bgColor & 0xFF;
            for (int y = 0; y < sh; y++) {
                const auto* row = stripRgb.ptr<cv::Vec3b>(y);
                auto* mrow = stripMask.ptr<uint8_t>(y);
                for (int x = 0; x < sw; x++) {
                    const float dr = row[x][0] - pr;
                    const float dg = row[x][1] - pg;
                    const float db = row[x][2] - pb;
                    if (dr * dr + dg * dg + db * db > FALLBACK_DIST * FALLBACK_DIST) {
                        mrow[x] = 255;
                    }
                }
            }
        }

        // Graphics kill-switch: the matte fires on any crisp
        // ink-like edge, so saturated illustration strokes (emblem
        // guilloche, plaques, photos) crossing the band land in the
        // union. Drop artwork pixels BEFORE the rim dilate — the
        // dilate then grows only from surviving glyph cores and
        // still reclaims the glyphs' antialiased rims where they sit
        // on the graphic. Freed pixels stay visible in the composite
        // and contribute to the field, so text printed ON a graphic
        // fills with the graphic's own colour instead of the paper
        // median.
        {
            const float pr = (line.bgColor >> 16) & 0xFF;
            const float pg = (line.bgColor >> 8) & 0xFF;
            const float pb = line.bgColor & 0xFF;
            const float ir = (line.fgColor >> 16) & 0xFF;
            const float ig = (line.fgColor >> 8) & 0xFF;
            const float ib = line.fgColor & 0xFF;
            for (int y = 0; y < sh; y++) {
                const auto* row = stripRgb.ptr<cv::Vec3b>(y);
                auto* mrow = stripMask.ptr<uint8_t>(y);
                for (int x = 0; x < sw; x++) {
                    if (!mrow[x]) continue;
                    const int lo = std::min({row[x][0], row[x][1], row[x][2]});
                    const int hi = std::max({row[x][0], row[x][1], row[x][2]});
                    if (hi - lo <= GRAPHIC_SAT_CUT) continue;
                    const float dp0 = row[x][0] - pr;
                    const float dp1 = row[x][1] - pg;
                    const float dp2 = row[x][2] - pb;
                    if (dp0 * dp0 + dp1 * dp1 + dp2 * dp2 <
                        GRAPHIC_PAPER_DIST * GRAPHIC_PAPER_DIST) continue;
                    const float di0 = row[x][0] - ir;
                    const float di1 = row[x][1] - ig;
                    const float di2 = row[x][2] - ib;
                    if (di0 * di0 + di1 * di1 + di2 * di2 <
                        GRAPHIC_INK_DIST * GRAPHIC_INK_DIST) continue;
                    mrow[x] = 0;
                }
            }
        }

        // Rim kill: the matte edge sits inside the ink's antialiased
        // rim; dilate replaces the rim too so no ghost outline remains.
        const int r = std::clamp(static_cast<int>(std::lround(FILL_RADIUS_FRAC * o.height)),
                                 FILL_RADIUS_MIN, FILL_RADIUS_MAX);
        cv::dilate(stripMask, stripMask,
                   cv::getStructuringElement(cv::MORPH_RECT, {2 * r + 1, 2 * r + 1}));

        const cv::Mat field = backgroundField(stripRgb, stripMask);
        const float feather = std::max(0.25f * sh, 2.0f);
        const cv::Mat alpha = edgeFeather(sw, sh, feather);

        auto strip = std::make_shared<ErasedStrip>();
        strip->w = sw;
        strip->h = sh;
        strip->epoch = _eraseEpoch;
        rectCorners(o.cx, o.cy, o.angle, o.width, o.height, padX, padY, line.eraseCorners);
        strip->rgba.resize(static_cast<size_t>(sw) * sh * 4);
        for (int y = 0; y < sh; y++) {
            const auto* row = stripRgb.ptr<cv::Vec3b>(y);
            const auto* frow = field.ptr<cv::Vec3f>(y);
            const auto* mrow = stripMask.ptr<uint8_t>(y);
            const auto* arow = alpha.ptr<uint8_t>(y);
            uint8_t* out = strip->rgba.data() + static_cast<size_t>(y) * sw * 4;
            for (int x = 0; x < sw; x++) {
                const cv::Vec3b& px = mrow[x] ? cv::Vec3b(
                    static_cast<uint8_t>(frow[x][0]),
                    static_cast<uint8_t>(frow[x][1]),
                    static_cast<uint8_t>(frow[x][2])) : row[x];
                // Device-probed channel order: byte0 must be R for the
                // Bitmap to display true colors (packing [B,G,R,A]
                // rendered R/B-swapped on both emulator and phone), and
                // Canvas composites premultiplied — fold A in here or
                // straight rims add full-strength RGB at A≈0 (halo).
                const uint16_t a = arow[x];
                out[x * 4 + 0] = static_cast<uint8_t>(px[0] * a / 255);
                out[x * 4 + 1] = static_cast<uint8_t>(px[1] * a / 255);
                out[x * 4 + 2] = static_cast<uint8_t>(px[2] * a / 255);
                out[x * 4 + 3] = static_cast<uint8_t>(a);
            }
        }
        line.erase = std::move(strip);
    });
}

} // namespace ocr
