//
// Homography math for the anchor tracker: Hartley-normalized DLT homography,
// affine (6-DoF) and similarity (4-DoF) fits over point correspondences, plus
// 3x3 compose/project/invert. Port of translator-core/homography.rs.
//

#include <cmath>

#include "include/ocr_pipeline.h"

namespace ocr {
namespace hmat {

H9 matMul(const H9& a, const H9& b) {
    H9 out{};
    for (int i = 0; i < 3; i++) {
        for (int j = 0; j < 3; j++) {
            out[i * 3 + j] =
                a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
        }
    }
    return out;
}

bool normalize(H9& h) {
    if (!std::isfinite(h[8]) || std::fabs(h[8]) < 1e-9f) return false;
    for (auto& v : h) v /= h[8];
    return true; // h[8] / h[8] == exactly 1.0f
}

bool project(const H9& h, float x, float y, float& qx, float& qy) {
    const float w = h[6] * x + h[7] * y + 1.0f;
    if (!std::isfinite(w) || std::fabs(w) < 1e-9f) return false;
    qx = (h[0] * x + h[1] * y + h[2]) / w;
    qy = (h[3] * x + h[4] * y + h[5]) / w;
    return true;
}

bool invert(const H9& h, H9& out) {
    const float a = h[0], b = h[1], c = h[2], d = h[3], e = h[4], f = h[5],
                g = h[6], i = h[7], k = h[8];
    const float A = e * k - f * i, B = -(d * k - f * g), C = d * i - e * g;
    const float det = a * A + b * B + c * C;
    if (!std::isfinite(det) || std::fabs(det) < 1e-12f) return false;
    const float inv = 1.0f / det;
    out = {A * inv, -(b * k - c * i) * inv, (b * f - c * e) * inv,
           B * inv, (a * k - c * g) * inv, -(a * f - c * d) * inv,
           C * inv, -(a * i - b * g) * inv, (a * e - b * d) * inv};
    return true;
}

template <int N>
static bool solveNxN(double (&a)[N][N], double (&b)[N], double (&x)[N]) {
    for (int col = 0; col < N; col++) {
        int piv = col;
        double pivAbs = std::fabs(a[col][col]);
        for (int r = col + 1; r < N; r++) {
            const double v = std::fabs(a[r][col]);
            if (v > pivAbs) { pivAbs = v; piv = r; }
        }
        if (pivAbs < 1e-9) return false;
        if (piv != col) {
            for (int j = 0; j < N; j++) std::swap(a[col][j], a[piv][j]);
            std::swap(b[col], b[piv]);
        }
        const double inv = 1.0 / a[col][col];
        for (int j = 0; j < N; j++) a[col][j] *= inv;
        b[col] *= inv;
        for (int r = 0; r < N; r++) {
            if (r == col) continue;
            const double factor = a[r][col];
            if (factor == 0.0) continue;
            for (int j = 0; j < N; j++) a[r][j] -= factor * a[col][j];
            b[r] -= factor * b[col];
        }
    }
    for (int i = 0; i < N; i++) x[i] = b[i];
    return true;
}

struct Norm2D {
    float mx = 0, my = 0, k = 1;
};

/// Hartley normalization stats: centroid + sqrt(2)/mean-radius scale.
static bool norm2d(const std::vector<std::array<float, 4>>& pairs, int cols, Norm2D& out) {
    const float n = pairs.size();
    float& mx = out.mx; float& my = out.my;
    for (const auto& p : pairs) { mx += p[cols == 0 ? 0 : 2]; my += p[cols == 0 ? 1 : 3]; }
    mx /= n; my /= n;
    float s = 0;
    for (const auto& p : pairs) {
        const float dx = p[cols == 0 ? 0 : 2] - mx, dy = p[cols == 0 ? 1 : 3] - my;
        s += std::sqrt(dx * dx + dy * dy);
    }
    s /= n;
    if (s <= 1e-3f) return false;
    out.k = std::sqrt(2.0f) / s;
    return true;
}

bool fitHomography(const std::vector<std::array<float, 4>>& pairs, H9& out) {
    if (pairs.size() < 4) return false;
    Norm2D np, nq;
    if (!norm2d(pairs, 0, np) || !norm2d(pairs, 1, nq)) return false;

    double ata[8][8] = {}, atb[8] = {}, x[8];
    for (const auto& p : pairs) {
        const double px = (p[0] - np.mx) * np.k, py = (p[1] - np.my) * np.k;
        const double qx = (p[2] - nq.mx) * nq.k, qy = (p[3] - nq.my) * nq.k;
        const double r1[8] = {px, py, 1, 0, 0, 0, -px * qx, -py * qx};
        const double r2[8] = {0, 0, 0, px, py, 1, -px * qy, -py * qy};
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) ata[i][j] += r1[i] * r1[j] + r2[i] * r2[j];
            atb[i] += r1[i] * qx + r2[i] * qy;
        }
    }
    if (!solveNxN<8>(ata, atb, x)) return false;
    H9 hn = {static_cast<float>(x[0]), static_cast<float>(x[1]), static_cast<float>(x[2]),
             static_cast<float>(x[3]), static_cast<float>(x[4]), static_cast<float>(x[5]),
             static_cast<float>(x[6]), static_cast<float>(x[7]), 1.0f};
    const H9 tp = {np.k, 0, -np.k * np.mx, 0, np.k, -np.k * np.my, 0, 0, 1};
    const H9 tqInv = {1 / nq.k, 0, nq.mx, 0, 1 / nq.k, nq.my, 0, 0, 1};
    out = matMul(tqInv, matMul(hn, tp));
    for (float v : out) if (!std::isfinite(v)) return false;
    return true;
}

bool fitAffine(const std::vector<std::array<float, 4>>& pairs, H9& out) {
    if (pairs.size() < 3) return false;
    Norm2D np, nq;
    if (!norm2d(pairs, 0, np) || !norm2d(pairs, 1, nq)) return false;

    double ata[6][6] = {}, atb[6] = {}, x[6];
    for (const auto& p : pairs) {
        const double px = (p[0] - np.mx) * np.k, py = (p[1] - np.my) * np.k;
        const double qx = (p[2] - nq.mx) * nq.k, qy = (p[3] - nq.my) * nq.k;
        const double r1[6] = {px, py, 1, 0, 0, 0};
        const double r2[6] = {0, 0, 0, px, py, 1};
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < 6; j++) ata[i][j] += r1[i] * r1[j] + r2[i] * r2[j];
            atb[i] += r1[i] * qx + r2[i] * qy;
        }
    }
    if (!solveNxN<6>(ata, atb, x)) return false;
    H9 an = {static_cast<float>(x[0]), static_cast<float>(x[1]), static_cast<float>(x[2]),
             static_cast<float>(x[3]), static_cast<float>(x[4]), static_cast<float>(x[5]),
             0, 0, 1};
    const H9 tp = {np.k, 0, -np.k * np.mx, 0, np.k, -np.k * np.my, 0, 0, 1};
    const H9 tqInv = {1 / nq.k, 0, nq.mx, 0, 1 / nq.k, nq.my, 0, 0, 1};
    out = matMul(tqInv, matMul(an, tp));
    out[6] = 0; out[7] = 0; out[8] = 1; // gauge/bit-exact bottom row
    for (int i = 0; i < 6; i++) if (!std::isfinite(out[i])) return false;
    return true;
}

bool fitSimilarity(const std::vector<std::array<float, 4>>& pairs, H9& out) {
    if (pairs.size() < 2) return false;
    const float n = pairs.size();
    float mpx = 0, mpy = 0, mqx = 0, mqy = 0;
    for (const auto& p : pairs) { mpx += p[0]; mpy += p[1]; mqx += p[2]; mqy += p[3]; }
    mpx /= n; mpy /= n; mqx /= n; mqy /= n;
    double re = 0, im = 0, den = 0;
    for (const auto& p : pairs) {
        const double dpx = p[0] - mpx, dpy = p[1] - mpy;
        const double dqx = p[2] - mqx, dqy = p[3] - mqy;
        re += dqx * dpx + dqy * dpy;
        im += dqy * dpx - dqx * dpy;
        den += dpx * dpx + dpy * dpy;
    }
    if (den < 1e-6) return false;
    const float sc = static_cast<float>(re / den), ss = static_cast<float>(im / den);
    out = {sc, -ss, mqx - (sc * mpx - ss * mpy),
           ss, sc, mqy - (ss * mpx + sc * mpy),
           0, 0, 1};
    for (float v : out) if (!std::isfinite(v)) return false;
    return true;
}

} // namespace hmat
} // namespace ocr
