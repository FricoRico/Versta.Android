//
// OCR geometry: per-box tilt estimation with scene consensus, oriented-box
// construction and the quadratic-spine dewarp.
//
// Sources (translator-ocr/ppocr.rs, translator-raster/text_metrics.rs):
//  - collapse_horizontal_axis / profile_top_bottom / tilt_via_ridge_fit
//  - build_oriented_boxes, build_text_line_strip, fit_tilt_field_consensus
//

#include <algorithm>
#include <cmath>
#include <limits>
#include <numeric>

#include "include/ocr_pipeline.h"

namespace ocr {

static constexpr float TILT_DEADZONE = 0.0175f;
static constexpr float RIDGE_MIN_R2 = 0.5f;
static constexpr int SPINE_MIN_COLUMNS = 16;

cv::Mat rot90(const cv::Mat& image, int times) {
    const int t = ((times % 4) + 4) % 4;
    cv::Mat out = image;
    switch (t) {
        case 1: cv::rotate(image, out, cv::ROTATE_90_CLOCKWISE); break;
        case 2: cv::rotate(image, out, cv::ROTATE_180); break;
        case 3: cv::rotate(image, out, cv::ROTATE_90_COUNTERCLOCKWISE); break;
        default: break;
    }
    return out;
}

/// In-place 90°-step rotation (0/90/180/270 CW); other leaves the frame.
void rotateByDegrees(cv::Mat& image, int degrees) {
    switch (degrees) {
        case 90:  cv::rotate(image, image, cv::ROTATE_90_CLOCKWISE); break;
        case 180: cv::rotate(image, image, cv::ROTATE_180); break;
        case 270: cv::rotate(image, image, cv::ROTATE_90_COUNTERCLOCKWISE); break;
        default: break;
    }
}

int stripRotation(const TextBox& box, int canonical) {
    // Reading rotation placed upright under [canonical]: tight.angle ->
    // canonical by the shortest 90-degree step.
    const float axisDeg = box.tight.angle * 180.0f / static_cast<float>(CV_PI);
    const float step = std::lround((canonical * 90.0f - axisDeg) / 90.0f);
    return ((static_cast<int>(step) % 4) + 4) % 4;
}

bool principalAxisAngle(const std::vector<Point>& contour, float& angle) {
    const float n = static_cast<float>(contour.size());
    if (n < 3) return false;

    float mx = 0, my = 0;
    for (const auto& p : contour) { mx += p.x; my += p.y; }
    mx /= n; my /= n;

    float sxx = 0, syy = 0, sxy = 0;
    for (const auto& p : contour) {
        const float dx = p.x - mx, dy = p.y - my;
        sxx += dx * dx; syy += dy * dy; sxy += dx * dy;
    }

    // Covariance eigen-directions; the reading axis is the major eigenvector,
    // canonicalized to point right (+x) or down (+y).
    const float tr = sxx + syy;
    const float disc = std::sqrt(std::max(0.0f, tr * tr / 4.0f - (sxx * syy - sxy * sxy)));
    const float lambda = tr / 2.0f + disc; // major
    float ux, uy;
    if (std::fabs(sxy) > 1e-9f) {
        ux = lambda - syy;
        uy = sxy;
    } else {
        ux = sxx >= syy ? 1.0f : 0.0f;
        uy = sxx >= syy ? 0.0f : 1.0f;
    }
    const float len = std::hypot(ux, uy);
    if (len < 1e-9f) return false;
    ux /= len; uy /= len;
    if (ux < 0 || (ux == 0 && uy < 0)) { ux = -ux; uy = -uy; }

    angle = std::atan2(uy, ux);
    return std::isfinite(angle);
}

/// Extents of the contour projected onto its principal axes.
static bool pcaExtents(const std::vector<Point>& contour, float angle,
                       float& length, float& width) {
    const float ux = std::cos(angle), uy = std::sin(angle);
    const float vx = -uy, vy = ux;
    float minU = 1e30f, maxU = -1e30f, minV = 1e30f, maxV = -1e30f;
    for (const auto& p : contour) {
        const float u = p.x * ux + p.y * uy;
        const float v = p.x * vx + p.y * vy;
        minU = std::min(minU, u); maxU = std::max(maxU, u);
        minV = std::min(minV, v); maxV = std::max(maxV, v);
    }
    length = maxU - minU;
    width = maxV - minV;
    return length > 0 && width > 0;
}

// ---------------------------------------------------------------------------
// Column profiles
// ---------------------------------------------------------------------------

namespace {

/// Per-column vertical profile of a contour in a frame rotated by [angle] so
/// the reading axis lies along +x (u = along, v = down in that frame).
struct Profiles {
    std::vector<float> d;       // vertical span, per column
    std::vector<float> top;     // first ink v (rotated frame)
    std::vector<float> bottom;  // last ink v (rotated frame)
    int cols = 0;
    float cs = 0, sn = 0;
    float uMin = 0, uMax = 0, vMin = 0, vMax = 0;

    bool valid(int col) const { return top[col] <= bottom[col]; }
};

Profiles buildProfiles(const std::vector<Point>& contour, float angle) {
    Profiles p;
    if (contour.size() < 4) return p;

    p.cs = std::cos(angle);
    p.sn = std::sin(angle);

    float uMin = 1e30f, uMax = -1e30f, vMin = 1e30f, vMax = -1e30f;
    for (const auto& q : contour) {
        const float u = q.x * p.cs + q.y * p.sn;
        const float v = q.y * p.cs - q.x * p.sn;
        uMin = std::min(uMin, u); uMax = std::max(uMax, u);
        vMin = std::min(vMin, v); vMax = std::max(vMax, v);
    }
    p.uMin = uMin; p.uMax = uMax; p.vMin = vMin; p.vMax = vMax;
    p.cols = static_cast<int>(std::lround(uMax - uMin)) + 1;
    const int uSpan = p.cols;
    if (uSpan < 2 || uSpan > 8192) { p.cols = 0; return p; }

    // findContours(CHAIN_APPROX_SIMPLE) returns sparse outline vertices — run
    // profiles computed directly from them are almost empty. Rasterize the
    // filled polygon in the rotated frame and take true per-column spans.
    const int rows = static_cast<int>(std::lround(vMax - vMin)) + 1;
    cv::Mat mask(rows, uSpan, CV_8UC1, cv::Scalar(0));
    {
        std::vector<cv::Point> poly(contour.size());
        for (size_t i = 0; i < contour.size(); i++) {
            const float u = contour[i].x * p.cs + contour[i].y * p.sn;
            const float v = contour[i].y * p.cs - contour[i].x * p.sn;
            poly[i] = {(int)std::lround(u - uMin), (int)std::lround(v - vMin)};
        }
        const std::vector<std::vector<cv::Point>> polys{poly};
        cv::fillPoly(mask, polys, cv::Scalar(255));
    }

    p.top.assign(p.cols, 1e30f);
    p.bottom.assign(p.cols, -1e30f);
    for (int x = 0; x < uSpan; x++) {
        const uint8_t* col = mask.ptr<uint8_t>(0) + x;
        for (int y = 0; y < rows; y++, col += uSpan) {
            if (*col) {
                if (p.top[x] > 1e29f) p.top[x] = vMin + y;
                p.bottom[x] = vMin + y;
            }
        }
    }

    p.d.resize(p.cols);
    for (int i = 0; i < p.cols; i++) {
        p.d[i] = p.valid(i) ? p.bottom[i] - p.top[i] : 0;
    }
    return p;
}

/// Weighted linear fit of the column midpoints *in the rotated frame*;
/// returns slope in (u,v)-space plus R^2 evidence quality.
bool ridgeFit(const Profiles& p, float& slope, float& r2) {
    double sw = 0, sx = 0, sy = 0;
    int n = 0;
    for (int i = 0; i < p.cols; i++) {
        if (!p.valid(i)) continue;
        const double w = std::max(1.0f, p.d[i]);
        const double x = p.uMin + i + 0.5;
        const double y = (p.top[i] + p.bottom[i]) / 2.0;
        sw += w; sx += w * x; sy += w * y;
        n++;
    }
    if (n < 4 || sw <= 0) return false;

    const double mx = sx / sw, my = sy / sw;
    double sxx = 0, sxy = 0, syy = 0;
    for (int i = 0; i < p.cols; i++) {
        if (!p.valid(i)) continue;
        const double w = std::max(1.0f, p.d[i]);
        const double dx = (p.uMin + i + 0.5) - mx;
        const double dy = (p.top[i] + p.bottom[i]) / 2.0 - my;
        sxx += w * dx * dx; sxy += w * dx * dy; syy += w * dy * dy;
    }
    if (sxx < 1e-12 || syy < 1e-12) {
        slope = 0; r2 = sxx > 1e-12 ? 1.0f : 0.0f;
        return sxx > 1e-12;
    }

    const double resid = std::max(0.0, syy - sxy * sxy / sxx);
    slope = static_cast<float>(sxy / sxx);
    r2 = static_cast<float>(1.0 - resid / syy);
    if (std::fabs(slope) > 1.0f) r2 = 0; // rotated frame should be near-flat
    return true;
}

} // namespace

/// Residual tilt of the box against [axisAngle] (its current reading frame)
/// (reference: estimate_tilt + profile_top_bottom).
static float estimateTilt(const TextBox& box, float axisAngle) {
    const Profiles p = buildProfiles(box.contour, axisAngle);
    if (p.cols < 5) return 0;

    float length, width;
    if (!pcaExtents(box.contour, axisAngle, length, width)) return 0;
    const float aspect = length / std::max(width, 1.0f);
    const bool committed = aspect < MIN_LINE_ASPECT;

    float slope, r2;
    if (!ridgeFit(p, slope, r2)) return 0;
    const float rawAngle = std::atan(slope);

    // Vote carries the measured angle only beyond the deadzone; the ridge
    // quality gates short boxes (committed ones keep their raw angle).
    float vote = 0;
    if (std::fabs(rawAngle) >= TILT_DEADZONE &&
        (committed || (r2 >= RIDGE_MIN_R2 && p.cols >= SPINE_MIN_COLUMNS))) {
        vote = rawAngle;
    }
    if (std::fabs(std::sin(vote)) < TILT_KEEP_EVIDENCE && !committed) {
        vote = 0;
    }
    return vote;
}

/// Circular mean (mod pi) over votes in one orientation bucket.
static float fieldMean(const std::vector<std::pair<float, float>>& votes) {
    double s = 0, c = 0;
    for (const auto& [angle, weight] : votes) {
        s += weight * std::sin(2.0 * angle);
        c += weight * std::cos(2.0 * angle);
    }
    if (s == 0 && c == 0) return 0;
    return static_cast<float>(std::atan2(s, c) / 2.0);
}

/// Scene-level tilt consensus, separated into the near-horizontal and
/// near-vertical modal buckets; trusted (very elongated) lines may pull away
/// from the frame within +-10 deg (reference: fit_tilt_field_consensus +
/// clamp_lean_line_angle_to_frame).
void fitTiltFieldConsensus(std::vector<TextBox>& boxes) {
    // Orientation mod-π, bucketing each angle in the near-horizontal or
    // near-vertical half of the circle.
    const auto horizontalBucket = [](float angle) -> bool {
        const float ref = std::fmod(angle, static_cast<float>(CV_PI));
        const float a = ref < 0 ? ref + static_cast<float>(CV_PI) : ref;
        return a < static_cast<float>(CV_PI) / 4.0f ||
               a > 3.0f * static_cast<float>(CV_PI) / 4.0f;
    };
    struct Vote { float angle; float weight; float aspect; };
    std::vector<Vote> votes(boxes.size());
    for (size_t i = 0; i < boxes.size(); i++) {
        float axis = boxes[i].tiltAngle;
        float aspect = 1.0f;
        float length, width;
        if (principalAxisAngle(boxes[i].contour, axis) && pcaExtents(boxes[i].contour, axis, length, width)) {
            aspect = length / std::max(width, 1.0f);
        }
        // tiltAngle already holds the absolute angle (axis + residual vote).
        votes[i] = {boxes[i].tiltAngle, std::clamp(aspect / TRUSTED_LINE_ASPECT, 0.0f, 1.0f), aspect};
    }

    std::vector<std::pair<float, float>> horizontal, vertical;
    for (const auto& v : votes) {
        if (v.weight <= 0) continue;
        if (horizontalBucket(v.angle)) {
            horizontal.emplace_back(v.angle, v.weight);
        } else {
            vertical.emplace_back(v.angle, v.weight);
        }
    }
    const float fieldH = fieldMean(horizontal);
    const float fieldV = fieldMean(vertical);

    for (size_t i = 0; i < boxes.size(); i++) {
        const float angle = votes[i].angle;
        const float field = horizontalBucket(angle) ? fieldH : fieldV;
        if (votes[i].aspect < MIN_LINE_ASPECT) {
            boxes[i].tiltAngle = angle; // committed votes stand alone
            continue;
        }
        float delta = angle - field;
        delta = std::fmod(delta + static_cast<float>(CV_PI), static_cast<float>(CV_PI));
        if (delta > static_cast<float>(CV_PI) / 2.0f) delta -= static_cast<float>(CV_PI); // wrap to ±pi/2

        const bool trusted = votes[i].aspect >= TRUSTED_LINE_ASPECT;
        if (std::fabs(delta) < TILT_KEEP_EVIDENCE ||
            (trusted && std::fabs(delta) < TRUSTED_TILT_DELTA)) {
            boxes[i].tiltAngle = field;
        } else {
            boxes[i].tiltAngle = angle;
        }
    }
}

// ---------------------------------------------------------------------------
// Oriented boxes (reference: build_oriented_boxes)
// ---------------------------------------------------------------------------

void rectCorners(float cx, float cy, float angle, float w, float h,
                 float padX, float padY, Point out[4]) {
    const float c = std::cos(angle), s = std::sin(angle);
    const float ux = c, uy = s;           // reading axis
    const float vx = -s, vy = c;          // down axis
    const float hw = w / 2.0f + padX, hh = h / 2.0f + padY;
    out[0] = {cx - hw * ux - hh * vx, cy - hw * uy - hh * vy};
    out[1] = {cx + hw * ux - hh * vx, cy + hw * uy - hh * vy};
    out[2] = {cx + hw * ux + hh * vx, cy + hw * uy + hh * vy};
    out[3] = {cx - hw * ux + hh * vx, cy - hw * uy + hh * vy};
}

/// Derives tight band, oriented (side-inflated) rect and corners from the
/// contour's columns under its principal axis.
static void buildOriented(TextBox& box, float axisAngle, float poolComp) {
    const Profiles p = buildProfiles(box.contour, axisAngle);
    if (p.cols < 2) return;

    std::vector<float> tops, bottoms;
    float u0 = 1e30f, u1 = -1e30f;
    for (int i = 0; i < p.cols; i++) {
        if (!p.valid(i)) continue;
        tops.push_back(p.top[i]);
        bottoms.push_back(p.bottom[i]);
        u0 = std::min(u0, p.uMin + i * 1.0f);
        u1 = std::max(u1, p.uMin + i + 1.0f);
    }
    if (tops.empty()) return;
    std::sort(tops.begin(), tops.end());
    std::sort(bottoms.begin(), bottoms.end());
    const float topBand = tops[tops.size() / 2];
    const float bottomBand = bottoms[bottoms.size() / 2];

    const float bandH = std::max(bottomBand - topBand, 1.0f);

    // Aspect-independent unclip along the reading axis, plus the detector's
    // stride pooling compensation (same formula as the mask-space box phase).
    const float inflate = detUnclipDistance(bandH) + poolComp;

    const float cx = (p.uMin + p.uMax) / 2.0f;
    const float cy = (topBand + bottomBand) / 2.0f;
    // Back-rotate the band center into image space.
    const float imgCx = cx * p.cs - cy * p.sn;
    const float imgCy = cy * p.cs + cx * p.sn;

    box.oriented = {imgCx, imgCy, (u1 - u0) + 2.0f * inflate, bandH, axisAngle};
    box.tight = {imgCx, imgCy, u1 - u0, bandH, axisAngle};
    rectCorners(imgCx, imgCy, axisAngle, box.tight.width, box.tight.height, 0, 0, box.corners);
}

void Engine::orientBoxes(std::vector<TextBox>& boxes) {
    const float poolComp = detPoolCompensationPx();

    // Initial votes measured against each box's own canonical axis.
    for (auto& box : boxes) {
        float axis;
        if (!principalAxisAngle(box.contour, axis)) continue;
        box.tiltAngle = estimateTilt(box, axis) + axis;
    }

    // Three rounds of consensus + refit; the modal angle absorbs the
    // deadzone-free votes (reference: repeated estimate/consensus loop).
    for (int iter = 0; iter < 3; iter++) {
        fitTiltFieldConsensus(boxes);
        if (iter == 2) break;
        for (auto& box : boxes) {
            float axis;
            if (!principalAxisAngle(box.contour, axis)) continue;
            box.tiltAngle = estimateTilt(box, axis) + axis;
        }
    }

    for (auto& box : boxes) {
        buildOriented(box, box.tiltAngle, poolComp);
        box.referenceAngle = box.tiltAngle;
    }
}

// ---------------------------------------------------------------------------
// Dewarping
// ---------------------------------------------------------------------------

static cv::Mat warpByMaps(const cv::Mat& rgb, const cv::Mat& mapX, const cv::Mat& mapY) {
    cv::Mat out;
    cv::remap(rgb, out, mapX, mapY, cv::INTER_LINEAR, cv::BORDER_REPLICATE);
    return out;
}

/// Affine/perspective strip from an oriented rect (reading order TL->BR).
void warpRect(const cv::Mat& rgb, const OrientedRect& o, int outW, int outH, cv::Mat& out) {
    Point corners[4];
    rectCorners(o.cx, o.cy, o.angle, o.width, o.height, 0, 0, corners);
    cv::Point2f src[4], dst[4] = {{0, 0}, {(float)outW, 0}, {(float)outW, (float)outH}, {0, (float)outH}};
    for (int i = 0; i < 4; i++) src[i] = {corners[i].x, corners[i].y};
    const cv::Mat m = cv::getPerspectiveTransform(src, dst);
    cv::warpPerspective(rgb, out, m, {outW, outH}, cv::INTER_LINEAR, cv::BORDER_REPLICATE);
}

bool dewarpContour(const cv::Mat& rgb, const TextBox& box, float thicknessPad, cv::Mat& out,
                   OrientedRect* regionOut) {
    const auto& contour = box.contour;
    const int n = static_cast<int>(contour.size());
    if (n < 8) return false;

    // 1. PCA on the contour points -> reading axis (u) and perpendicular (v).
    float meanX = 0, meanY = 0;
    for (const auto& p : contour) { meanX += p.x; meanY += p.y; }
    meanX /= n;
    meanY /= n;
    float cxx = 0, cyy = 0, cxy = 0;
    for (const auto& p : contour) {
        const float dx = p.x - meanX, dy = p.y - meanY;
        cxx += dx * dx;
        cyy += dy * dy;
        cxy += dx * dy;
    }
    cxx /= n; cyy /= n; cxy /= n;
    const float trace = cxx + cyy;
    const float det = cxx * cyy - cxy * cxy;
    const float disc = std::sqrt(std::max(trace * trace - 4.0f * det, 0.0f));
    const float lambda1 = (trace + disc) * 0.5f;
    const float lambda2 = (trace - disc) * 0.5f;
    float ux, uy;
    if (std::fabs(cxy) > 1e-6f) {
        ux = lambda1 - cyy;
        uy = cxy;
    } else {
        ux = cxx >= cyy ? 1.0f : 0.0f;
        uy = cxx >= cyy ? 0.0f : 1.0f;
    }
    const float axisLen = std::hypot(ux, uy);
    if (axisLen < 1e-9f) return false;
    ux /= axisLen;
    uy /= axisLen;

    // A near-square contour has no trustworthy principal axis; fall back to the
    // consensus line angle (reference: STRIP_ELONG_MAX fallback to canonical).
    constexpr float STRIP_ELONG_MAX = 0.12f;
    if (lambda2 / std::max(lambda1, 1e-6f) > STRIP_ELONG_MAX) {
        ux = std::cos(box.tight.angle);
        uy = std::sin(box.tight.angle);
    }
    // Sign ambiguity: canonicalize u so it points along the consensus reading
    // direction (+u . (cos tight, sin tight) >= 0).
    const float along = ux * std::cos(box.tight.angle) + uy * std::sin(box.tight.angle);
    if (along < 0.0f) { ux = -ux; uy = -uy; }
    const float vx = -uy, vy = ux;

    // 2. Project contour into the (u, v) frame.
    float uMin = 1e30f, uMax = -1e30f;
    std::vector<float> us(n), vs(n);
    for (int i = 0; i < n; i++) {
        const float dx = contour[i].x - meanX, dy = contour[i].y - meanY;
        us[i] = dx * ux + dy * uy;
        vs[i] = dx * vx + dy * vy;
        uMin = std::min(uMin, us[i]);
        uMax = std::max(uMax, us[i]);
    }
    const float uSpan = uMax - uMin;
    if (uSpan < 8.0f) return false;
    const float uCenter = (uMin + uMax) * 0.5f;
    const float uHalfSpan = uSpan * 0.5f;

    // 3. Quadratic spine through ALL contour points (u normalized to [-1, 1]):
    //    the two edges sit symmetrically at +/-thickness/2 so their single
    //    least-squares quadratic is the centerline directly, even when the line
    //    bows (reference ppocr.rs fit comment).
    double s0 = n, s1 = 0, s2m = 0, s3 = 0, s4 = 0;
    double t0 = 0, t1 = 0, t2 = 0;
    for (int i = 0; i < n; i++) {
        const double un = (us[i] - uCenter) / uHalfSpan;
        const double v = vs[i];
        const double u2 = un * un;
        s1 += un; s2m += u2; s3 += u2 * un; s4 += u2 * u2;
        t0 += v; t1 += un * v; t2 += u2 * v;
    }
    // Solve the 3x3 normal equations m . (a,b,c)^T = (t2,t1,t0).
    double m3[3][4] = {
        {s4, s3, s2m, t2},
        {s3, s2m, s1, t1},
        {s2m, s1, s0, t0},
    };
    for (int col = 0; col < 3; col++) {
        int piv = col;
        for (int r = col + 1; r < 3; r++) {
            if (std::fabs(m3[r][col]) > std::fabs(m3[piv][col])) piv = r;
        }
        if (std::fabs(m3[piv][col]) < 1e-12) return false;
        for (int cI = 0; cI < 4; cI++) std::swap(m3[piv][cI], m3[col][cI]);
        const double d = m3[col][col];
        for (int cI = 0; cI < 4; cI++) m3[col][cI] /= d;
        for (int r = 0; r < 3; r++) {
            if (r == col) continue;
            const double f = m3[r][col];
            for (int cI = 0; cI < 4; cI++) m3[r][cI] -= f * m3[col][cI];
        }
    }
    const double spineA = m3[0][3], spineB = m3[1][3];
    double spineC = m3[2][3];
    auto evalSpine = [&](float un) {
        return static_cast<float>(spineA * un * un + spineB * un + spineC);
    };

    // 4. Band thickness = p05-p95 spread of points about the spine + the mask
    //    deficit; the dup band box below is what the recognizer was trained
    //    with (2.4x margin around glyphs).
    std::vector<float> residuals;
    residuals.reserve(n);
    for (int i = 0; i < n; i++) {
        residuals.push_back(vs[i] - evalSpine((us[i] - uCenter) / uHalfSpan));
    }
    std::sort(residuals.begin(), residuals.end());
    auto percentile = [&](float p) {
        return residuals[static_cast<size_t>(std::lround((n - 1) * p))];
    };
    const float bandThickness = std::max(percentile(0.95f) - percentile(0.05f), 1.0f)
                              + thicknessPad;
    // The p05-p95 band drops the rare descenders (>95th percentile along the
    // line they never register), so a symmetric band clips descender tails.
    // Grow a descender slice below and push the centre down by half of it.
    const float descExtra = bandThickness * STRIP_DESCENDER_VPAD_FRAC;
    const float globalThickness = bandThickness * STRIP_BAND_INFLATE + descExtra;
    spineC += descExtra * 0.5f;
    const float uPad = bandThickness;

    const float paddedUMin = uMin - uPad;
    const float paddedUSpan = uSpan + 2.0f * uPad;
    const int outH = std::clamp(static_cast<int>(std::lround(globalThickness)),
                                STRIP_MIN_HEIGHT, STRIP_MAX_HEIGHT);
    const int outW = std::clamp(static_cast<int>(std::lround(paddedUSpan)),
                                STRIP_MIN_WIDTH, STRIP_MAX_WIDTH);

    cv::Mat mapX(outH, outW, CV_32FC1);
    cv::Mat mapY(outH, outW, CV_32FC1);
    for (int ox = 0; ox < outW; ox++) {
        const float uLocal = paddedUMin + (ox + 0.5f) * paddedUSpan / outW;
        const float spineV = evalSpine((uLocal - uCenter) / uHalfSpan);
        for (int oy = 0; oy < outH; oy++) {
            const float vNorm = (oy + 0.5f) / outH - 0.5f;
            const float vLocal = spineV + vNorm * globalThickness;
            mapX.at<float>(oy, ox) = meanX + uLocal * ux + vLocal * vx;
            mapY.at<float>(oy, ox) = meanY + uLocal * uy + vLocal * vy;
        }
    }

    out = warpByMaps(rgb, mapX, mapY);
    if (regionOut) {
        // Band region in image space: affine hull of the warped band (the
        // quadratic spine's curl is sub-px on printed pages).
        *regionOut = {
            meanX + uCenter * ux + static_cast<float>(spineC) * vx,
            meanY + uCenter * uy + static_cast<float>(spineC) * vy,
            paddedUSpan,
            globalThickness,
            std::atan2(uy, ux),
        };
    }
    return true;
}

void dewarpLine(const cv::Mat& rgb, const TextBox& box, float thicknessPad, cv::Mat& out,
                OrientedRect* regionOut) {
    if (dewarpContour(rgb, box, thicknessPad, out, regionOut)) return;

    // Degenerate contour: affine strip over the same 2.4x + descender band
    // around the kernel band, so the matte stage stays on its trained input
    // distribution either way.
    const float kernel = std::max(box.tight.height, 1.0f);
    const float descExtra = kernel * STRIP_DESCENDER_VPAD_FRAC;
    const float globalThickness = kernel * STRIP_BAND_INFLATE + descExtra;
    OrientedRect o = box.tight;
    o.height = globalThickness;
    // Descender slice lives below the spine centre.
    o.cx += -std::sin(o.angle) * descExtra * 0.5f;
    o.cy += std::cos(o.angle) * descExtra * 0.5f;
    const int outH = std::clamp(static_cast<int>(std::lround(globalThickness)),
                                STRIP_MIN_HEIGHT, STRIP_MAX_HEIGHT);
    const int outW = std::clamp(static_cast<int>(std::lround(outH * o.width / globalThickness)),
                                STRIP_MIN_WIDTH, STRIP_MAX_WIDTH);
    if (regionOut) *regionOut = o;
    warpRect(rgb, o, outW, outH, out);
}
} // namespace ocr
