//
// Glyph-matte typography: per-line bold flag, fg color from the ink model's F
// head (alpha^2-weighted pooling under the strokes), and bg color = the paper
// the reference paints back after erasing the ink — p75 over the *strip's own
// pixels* clear of the matte. The model exposes four NAMED outputs (matte,
// weight, foreground, background) — fetched by name, never "largest": B is
// never painted; like the reference it only feeds the closed-form alpha
// re-solve kill-switch (background texels the matte grabbed get pulled down)
// and the measured-ink polarity guard.
//
// Source: translator-ocr/ppocr.rs (ink_strips_from, pooled_color),
// translator-raster/color_matting.rs (background_field, fill_radius) and
// text_metrics.rs (ink/bold constants).
//

#include <algorithm>
#include <cmath>
#include <cstring>
#include <numeric>
#include <thread>

#include <MNN/Tensor.hpp>

#include "include/ocr_pipeline.h"
#include "include/parallel.h"

namespace ocr {

static float rgbNormLut[256];
static bool rgbNormInitialized = [] {
    for (int v = 0; v < 256; v++) rgbNormLut[v] = v / 255.0f;
    return true;
}();

static inline float sigmoid(float x) {
    return 1.0f / (1.0f + std::exp(-x));
}

/// The shipped glyph-matte model has FOUR named outputs (matte, weight,
/// foreground, background) — never pick "the largest", Model::run's
/// largest-tensor rule exists for the single-useful-output det/rec models.
/// Fetches by name; weight/foreground/background degrade to empty vectors.
struct MatteOutputs {
    std::vector<float> matte;      // [1,1,48,W] — required
    std::vector<float> weight;     // [1,1,48,W] — stroke boldness
    std::vector<float> foreground; // [1,3,48,W] — ink RGB
    std::vector<float> background; // [1,3,48,W] — paper RGB under ink
};

static bool matteRun(Model* model, Model::Session* session,
                     const std::vector<float>& input, int sessW, MatteOutputs& mo) {
    auto* net = model->interpreter.get();
    auto* liveInput = net->getSessionInput(session->session, nullptr);
    net->resizeTensor(liveInput, {1, 3, 48, sessW});
    net->resizeSession(session->session);
    liveInput = net->getSessionInput(session->session, nullptr);
    MNN::Tensor host(liveInput, MNN::Tensor::CAFFE);
    ::memcpy(host.host<float>(), input.data(), input.size() * sizeof(float));
    liveInput->copyFromHostTensor(&host);
    net->runSession(session->session);

    auto fetch = [&](const char* name, std::vector<float>& out) {
        MNN::Tensor* t = net->getSessionOutput(session->session, name);
        if (!t || t->elementSize() <= 0) { out.clear(); return false; }
        MNN::Tensor hostT(t, MNN::Tensor::CAFFE);
        t->copyToHostTensor(&hostT);
        const auto shape = hostT.shape();
        if (shape.size() < 4 || shape[2] != 48 || shape[3] != sessW) {
            out.clear();
            return false;
        }
        const float* src = hostT.host<float>();
        if (!src) { out.clear(); return false; }
        out.assign(src, src + hostT.elementSize());
        return true;
    };

    if (!fetch("matte", mo.matte)) return false;
    fetch("weight", mo.weight);
    fetch("foreground", mo.foreground);
    fetch("background", mo.background);
    return mo.matte.size() == static_cast<size_t>(48) * sessW;
}

/// Luma-quantile polarity fallback: glyphs sit on the opposite side of the
/// 25th/75th-percentile split relative to the band.
static void polarityColors(const cv::Mat& strip, int& fg, int& bg) {
    cv::Mat gray;
    cv::cvtColor(strip, gray, cv::COLOR_RGB2GRAY);
    std::vector<uint8_t> flat(gray.datastart, gray.dataend);
    if (flat.empty()) { fg = 0xFF000000; bg = 0xFFFFFFFF; return; }
    const size_t q25 = flat.size() / 4, q75 = flat.size() * 3 / 4;
    std::nth_element(flat.begin(), flat.begin() + q25, flat.end());
    const int lo = flat[q25];
    std::nth_element(flat.begin(), flat.begin() + q75, flat.end());
    const int hi = flat[q75];
    // Dark ink on light background is more common in world images.
    fg = 0xFF000000 | (lo << 16) | (lo << 8) | lo;
    bg = 0xFF000000 | (hi << 16) | (hi << 8) | hi;
}

/// Sampled ink/paper colors without the matte model: Otsu splits the band's
/// gray ramp into dark vs. light, per-channel means give tinted live overlay
/// colors (~1ms per line instead of the matte model's stills budget).
/// Polarity-neutral: ink is whichever side the glyphs plausibly are. Text
/// strips normally hold less ink than paper, but bold headlines can exceed
/// half coverage — a strongly dark whole-strip median disambiguates (white
/// ink on a dark poster vs. a dark bold headline).
static void sampledColors(const cv::Mat& strip, int& fg, int& bg) {
    cv::Mat gray;
    cv::cvtColor(strip, gray, cv::COLOR_RGB2GRAY);

    cv::Mat dark;
    cv::threshold(gray, dark, 0, 255, cv::THRESH_BINARY_INV | cv::THRESH_OTSU);

    const int total = gray.cols * gray.rows;
    const int darkCount = cv::countNonZero(dark);
    if (darkCount == 0 || darkCount == total) {
        polarityColors(strip, fg, bg);
        return;
    }

    std::vector<uint8_t> flat(gray.datastart, gray.dataend);
    std::nth_element(flat.begin(), flat.begin() + flat.size() / 2, flat.end());
    const int median = flat[flat.size() / 2];
    const double darkFrac = static_cast<double>(darkCount) / total;

    cv::Mat ink;
    if (darkFrac <= 0.5) {
        // Dark-ink minority on brighter paper (the common case).
        if (darkFrac < 0.02) { polarityColors(strip, fg, bg); return; }
        ink = dark;
    } else if (median < 96) {
        // Dark majority on a dark strip: white/bright ink on dark paper.
        cv::bitwise_not(dark, ink);
        const int inkCount = total - darkCount;
        if (inkCount < total * 0.02) { polarityColors(strip, fg, bg); return; }
    } else {
        // Dark majority on a bright strip: ink-dominant dark headline.
        ink = dark;
    }

    const cv::Scalar inkMean = cv::mean(strip, ink);
    const cv::Scalar paperMean = cv::mean(strip, 255 - ink);
    auto pack = [](const cv::Scalar& s) {
        return 0xFF000000
            | (static_cast<int>(std::lround(std::clamp(s[0], 0.0, 255.0))) << 16)
            | (static_cast<int>(std::lround(std::clamp(s[1], 0.0, 255.0))) << 8)
            | static_cast<int>(std::lround(std::clamp(s[2], 0.0, 255.0)));
    };
    fg = pack(inkMean);
    bg = pack(paperMean);
}

/// Paper color from the strip's own pixels: per-channel medians over texels
/// clear of the ink matte, with the paper mask eroded by fill_radius so halo
/// texels near strokes never pollute the estimate (reference:
/// background_field — cell medians of non-ink pixels; we need one flat color
/// per line for the quad renderer instead of a raster field).
static int paperColor(const cv::Mat& strip, const uint8_t* alpha, int aw, int ah) {
    cv::Mat alphaMat(ah, aw, CV_8UC1, const_cast<uint8_t*>(alpha));
    cv::Mat paper;
    cv::resize(alphaMat, paper, {strip.cols, strip.rows}, 0, 0, cv::INTER_LINEAR);
    cv::threshold(paper, paper, INK_CUT - 1, 255, cv::THRESH_BINARY_INV);

    // Erode by fill_radius: ink texels + stroke halo drop out of the paper set.
    const int r = std::clamp(static_cast<int>(std::lround(strip.rows * 0.06)), 1, 6);
    cv::Mat eroded;
    cv::erode(paper, eroded, cv::getStructuringElement(cv::MORPH_RECT, {2 * r + 1, 2 * r + 1}));
    cv::Mat mask = cv::countNonZero(eroded) >= 4 ? eroded : paper;

    // Per-channel histograms → medians.
    int hist[3][256] = {};
    int count = 0;
    for (int y = 0; y < strip.rows; y++) {
        const auto* row = strip.ptr<cv::Vec3b>(y);
        const auto* mrow = mask.ptr<uint8_t>(y);
        for (int x = 0; x < strip.cols; x++) {
            if (!mrow[x]) continue;
            hist[0][row[x][0]]++;
            hist[1][row[x][1]]++;
            hist[2][row[x][2]]++;
            count++;
        }
    }
    if (count < 4) {
        // Ink-dominant strips (bold headlines, thin kernel bands with tiny
        // dewarps): paper sampling has nothing to pool — the whole-strip median
        // lands mid-gray ink. The Otsu splitter splits the strip by luma and
        // takes its bright-side mean, which is paper even at 90% ink coverage
        // (keeps bold lines on white paper from going gray).
        int fg, bg;
        sampledColors(strip, fg, bg);
        return bg;
    }

    // Paper highlight, not paper middle: a flat-quad fill should track the
    // paper's light side — the median gets dragged down by antialiased stroke
    // edges and texture noise that survive the erosion. p75 stays shadow-aware
    // (genuinely dark paper still reads dark) but no longer reads as muddy
    // "darkened white". (Per-pixel the reference inpaints; we need one color.)
    int rgb[3] = {0, 0, 0};
    for (int c = 0; c < 3; c++) {
        int acc = 0;
        const int target = count * 3 / 4;
        for (int v = 0; v < 256; v++) {
            acc += hist[c][v];
            if (acc > target) { rgb[c] = v; break; }
        }
    }
    return 0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
}

static float luma8Of(int argb) {
    return ((argb >> 16 & 0xFF) * 299 + ((argb >> 8) & 0xFF) * 587 + (argb & 0xFF) * 114) / 1000.0f;
}

/// Matte session width bucket for a natural 48-high strip of width [natW]:
/// 16-wide pool rounding, capped at MATTE_MAX_WIDTH (the sort key and the
/// real run width must agree, or MNN rebuilds its resize chain mid-pass).
static int matteSessionWidth(int natW) {
    return std::clamp(((std::max(16, natW / 16 * 16) + 15) / 16) * 16, 16,
                      MATTE_MAX_WIDTH);
}

/// Normalizes one weighted channel mean (0..65535 float space) to its byte.
static int channelByte(double v) {
    return static_cast<int>(std::lround(std::clamp(v, 0.0, 1.0) * 255.0));
}

/// Measured ink colour: the QUARTILE of the core pool set FURTHEST from the
/// paper. A saturated matte dilutes a plain core mean with paper texels; thin
/// strokes dilute even the far half, so only the top-distance quartile
/// restores the true ink hue (and polarity — white-on-dark measures bright).
/// [maxDistSqOut] receives the pool's max paper distance — the fog gate uses
/// it to detect "camera never resolved the ink".
static int measuredInkColor(const cv::Mat& resized, const std::vector<uint32_t>& texels,
                            int sessW, int bgColor, float& maxDistSqOut) {
    const float br = (bgColor >> 16 & 0xFF), bgC = (bgColor >> 8 & 0xFF),
                bb = (bgColor & 0xFF);
    std::vector<std::pair<float, const uint8_t*>> dist(texels.size());
    float maxDistSq = 0.0f;
    for (size_t t = 0; t < texels.size(); t++) {
        const int off = texels[t];
        const uint8_t* px = reinterpret_cast<const uint8_t*>(
            resized.ptr<cv::Vec3b>(off / sessW) + (off % sessW));
        const float dr = px[0] - br, dg = px[1] - bgC, db = px[2] - bb;
        dist[t] = {dr * dr + dg * dg + db * db, px};
        if (dist[t].first > maxDistSq) maxDistSq = dist[t].first;
    }
    maxDistSqOut = maxDistSq;
    std::nth_element(dist.begin(), dist.begin() + dist.size() * 3 / 4, dist.end());
    double sum[3] = {0, 0, 0};
    const size_t far = dist.size() * 3 / 4;
    for (size_t t = far; t < dist.size(); t++) {
        sum[0] += dist[t].second[0];
        sum[1] += dist[t].second[1];
        sum[2] += dist[t].second[2];
    }
    const size_t nFar = dist.size() - far;
    return 0xFF000000
        | (channelByte(sum[0] / nFar / 255.0) << 16)
        | (channelByte(sum[1] / nFar / 255.0) << 8)
        | channelByte(sum[2] / nFar / 255.0);
}

/// The model's foreground head pooled over the core texels (alpha² weight).
/// Returns -1 when the head disagrees with nothing (no usable weight) — int
/// can't hold that flag since ARGB colors carry the sign bit.
static long modelInkColor(const MatteOutputs& mo, const std::vector<float>& alphaF,
                          const std::vector<uint32_t>& texels, int mPlane) {
    double fgW = 0, fgSum[3] = {0, 0, 0};
    for (const uint32_t off : texels) {
        const double w = static_cast<double>(alphaF[off]) * alphaF[off];
        fgW += w;
        for (int c = 0; c < 3; c++) {
            fgSum[c] += w * sigmoid(mo.foreground[c * mPlane + off]);
        }
    }
    if (fgW <= 0) return -1;
    return 0xFF000000L
        | (channelByte(fgSum[0] / fgW) << 16)
        | (channelByte(fgSum[1] / fgW) << 8)
        | channelByte(fgSum[2] / fgW);
}

void Engine::applyGlyphMatte(const cv::Mat& upright, std::vector<TextLine>& lines,
                             const std::vector<DewarpedStrip>& strips, int canonical) {
    if (lines.empty()) return;
    const float thicknessPad = std::max(0.0f, _detStride - 1.0f) * _detScale;

    // Matte strips dewarp from the TIGHT band (never the contour cache): the
    // alpha they produce is projected back through the tight-anchored band in
    // runErase, so matte content and union mask must share that frame — the
    // contour spine wanders off the tight rect on unrectified live frames and
    // its alpha otherwise lands pixels off the mask (ink-smear ghosts).
    auto stripFor = [&](const TextLine& line, bool rotate) -> cv::Mat {
        const OrientedRect band = stripBandRegion(line.box.tight);
        const int outH = std::clamp(static_cast<int>(std::lround(band.height)),
                                    STRIP_MIN_HEIGHT, STRIP_MAX_HEIGHT);
        const int outW = std::clamp(static_cast<int>(std::lround(band.width)),
                                    STRIP_MIN_WIDTH, STRIP_MAX_WIDTH);
        cv::Mat strip;
        warpRect(upright, band, outW, outH, strip);
        if (rotate) strip = rot90(strip, stripRotation(line.box, canonical));
        return strip;
    };

    // The ink model runs whenever installed, in both profiles — the reference
    // keeps color matting enabled live (ENABLE_COLOR_MATTING=true), gated to
    // acquire-rate passes rather than per-frame. Sampled colors cover setups
    // without the model.
    if (!_matte) {
        parallelFor(std::min<size_t>(4, lines.size()), lines.size(), [&](size_t, size_t i) {
            const cv::Mat strip = stripFor(lines[i], false);
            if (strip.empty()) return;
            sampledColors(strip, lines[i].fgColor, lines[i].bgColor);
        });
        return;
    }

    // Process lines sorted by matte input width: MNN skips a session's resize
    // rebuild when the next shape matches the last, and equal-width neighbours
    // on the same session make that hit nearly every run.
    auto matteWidthOf = [&](size_t li) {
        // Tight-band strip dims (see stripFor).
        const OrientedRect band = stripBandRegion(lines[li].box.tight);
        const int rot = stripRotation(lines[li].box, canonical);
        const int w = rot % 2 == 0 ? static_cast<int>(std::lround(band.width))
                                   : static_cast<int>(std::lround(band.height));
        const int h = rot % 2 == 0 ? static_cast<int>(std::lround(band.height))
                                   : static_cast<int>(std::lround(band.width));
        const int natW = std::max(1, static_cast<int>(std::lround(
            w * 48.0 / std::max(h, 1))));
        return matteSessionWidth(natW);
    };
    std::vector<size_t> order(lines.size());
    std::iota(order.begin(), order.end(), 0);
    std::sort(order.begin(), order.end(), [&](size_t a, size_t b) {
        return matteWidthOf(a) < matteWidthOf(b);
    });

    parallelFor(std::min<size_t>(_matteSessions.size(), lines.size()), order.size(),
                [&](size_t wi, size_t ord) {
        auto* session = _matteSessions[wi].get();
                const size_t i = order[ord];
                auto& line = lines[i];

        cv::Mat strip = stripFor(line, true);
        if (strip.empty()) return;

                // Matte input is aspect-preserving: width scaled by 48/stripH
                // (reference ink_strips: content_w = stripW * H / stripH,
                // next POOL_MULTIPLE). Feeding natural-pixel width instead
                // (~2.4x wider on dewarped strips) doubles conv cost AND
                // distorts glyphs 2.4x off the trained geometry.
                const int natW = std::max(1, static_cast<int>(
                    std::lround(strip.cols * 48.0 / strip.rows)));
                // Wide paragraph strips dominate the matte budget (the int8
                // conv is ~300-500 ms/run at sessW=1024 on x86): the
                // MATTE_MAX_WIDTH cap halves that while leaving stroke
                // fidelity.
                const int sessW = matteSessionWidth(natW);

                cv::Mat resized;
                cv::resize(strip, resized, {sessW, 48}, 0, 0, cv::INTER_AREA);
                std::vector<float> input(3 * 48 * sessW);
                const int plane = 48 * sessW;
                for (int y = 0; y < 48; y++) {
                    const auto* row = resized.ptr<cv::Vec3b>(y);
                    const int base = y * sessW;
                    for (int x = 0; x < sessW; x++) {
                        const int idx = base + x;
                        input[idx] = rgbNormLut[row[x][0]];
                        input[plane + idx] = rgbNormLut[row[x][1]];
                        input[2 * plane + idx] = rgbNormLut[row[x][2]];
                    }
                }

                MatteOutputs mo;
        if (!matteRun(_matte.get(), session, input, sessW, mo)) {
            polarityColors(strip, line.fgColor, line.bgColor);
            return;
        }
                const int mPlane = 48 * sessW;
                const bool hasBold = mo.weight.size() == static_cast<size_t>(mPlane);
                const bool hasColor =
                    mo.foreground.size() == 3u * static_cast<size_t>(mPlane) &&
                    mo.background.size() == 3u * static_cast<size_t>(mPlane);

                std::vector<float> alphaF(mPlane, 0.0f), boldF(mPlane, 0.0f);
                double aMean = 0;
                float aPeak = 0;
                for (int o = 0; o < mPlane; o++) {
                    const float a = sigmoid(mo.matte[o]);
                    alphaF[o] = a;
                    aMean += a;
                    aPeak = std::max(aPeak, a);
                    if (hasBold) boldF[o] = sigmoid(mo.weight[o]);
                }
                aMean /= std::max(mPlane, 1);

                // Closed-form alpha re-solve kill-switch (reference FBA fusion
                // step): a matte texel that grabbed background projects the
                // observed pixel onto the predicted F-B colour line near zero
                // — pull those down so paper pixels never count as ink.
                if (hasColor) {
                    std::vector<uint8_t> fU8(3 * mPlane), bU8(3 * mPlane);
                    for (int c = 0; c < 3; c++) {
                        for (int o = 0; o < mPlane; o++) {
                            fU8[c * mPlane + o] = static_cast<uint8_t>(std::lround(
                                std::clamp(sigmoid(mo.foreground[c * mPlane + o]), 0.0f, 1.0f) * 255.0f));
                            bU8[c * mPlane + o] = static_cast<uint8_t>(std::lround(
                                std::clamp(sigmoid(mo.background[c * mPlane + o]), 0.0f, 1.0f) * 255.0f));
                        }
                    }
                    constexpr int MIN_FB_DIST2 = 32 * 32;
                    constexpr float RESOLVE_KILL = 102.0f / 255.0f;
                    for (int y = 0; y < 48; y++) {
                        const auto* pixRow = resized.ptr<cv::Vec3b>(y);
                        for (int x = 0; x < sessW; x++) {
                            const int o = y * sessW + x;
                            const auto& p = pixRow[x];
                            int dot = 0, n2 = 0;
                            for (int c = 0; c < 3; c++) {
                                const int d = static_cast<int>(fU8[c * mPlane + o])
                                            - static_cast<int>(bU8[c * mPlane + o]);
                                dot += (static_cast<int>(p[c]) - static_cast<int>(bU8[c * mPlane + o])) * d;
                                n2 += d * d;
                            }
                            if (n2 < MIN_FB_DIST2) continue;
                            const float a = std::min(std::max(
                                static_cast<float>(dot) / n2, 0.0f), 1.0f);
                            if (a < RESOLVE_KILL && a < alphaF[o]) { alphaF[o] = a; }
                        }
                    }
                }

                // Ink typography and the paper mask both run on
                // the killed alpha — one place builds it. The erasure stage
                // consumes the same alpha through line.matte.
                std::vector<uint8_t> alpha8(mPlane);
                for (int o = 0; o < mPlane; o++) {
                    alpha8[o] = static_cast<uint8_t>(std::lround(
                        std::clamp(alphaF[o], 0.0f, 1.0f) * 255.0f));
                }
                {
                    auto mask = std::make_shared<MatteMask>();
                    mask->alpha = alpha8;
                    mask->w = sessW;
                    mask->rot = stripRotation(line.box, canonical);
                    line.matte = std::move(mask);
                }

                // Stroke-core pooling (reference text_metrics stroke_core_cut):
                // the cut is peak-relative, so faint and saturated mattes both
                // pool over "solid ink". Health gates on the core alone; the
                // weight head (bold) pools over the same set.
                const float core = std::max(aPeak * 0.6f, INK_CUT / 255.0f);
                std::vector<uint32_t> texels;
                double boldSum = 0;
                for (int y = 0; y < 48; y++) {
                    const int base = y * sessW;
                    for (int x = 0; x < sessW; x++) {
                        const int off = base + x;
                        if (alphaF[off] < core) continue;
                        texels.push_back(static_cast<uint32_t>(off));
                        if (hasBold) boldSum += boldF[off];
                    }
                }
                if (texels.size() < INK_BOLD_MIN_PX) {
                    polarityColors(strip, line.fgColor, line.bgColor);
                    return;
                }
                const float boldScore = hasBold
                    ? static_cast<float>(boldSum / texels.size()) : 0.0f;
                line.bold = boldScore >= MODEL_BOLD_THRESHOLD;

                // No recentring onto the matte's x-height interval (the
                // reference's measure_line is deliberately not ported): our
                // renderer centres the baseline in the render band, and the
                // matte band excludes the descender allowance the inflate
                // already bakes in — recentring offsets text ~half a band off
                // its own paper.

                // Paper under the glyphs: real strip pixels, never the model's
                // B prediction (reference: background_field rebuilds paper from
                // the original pixels after erasing ink via the matte).
                line.bgColor = paperColor(strip, alpha8.data(), sessW, 48);

                // Measured ink colour (far-quartile de-dilution) is the
                // primary; the model's F head may override it only in the fog
                // gate below — its prediction mutes value and chroma toward
                // dark mid-tones on coloured ink (field evidence: bright red
                // read #6a4c28 while the camera saw #ba3d35).
                float maxDistSq = 0.0f;
                const int measured = measuredInkColor(resized, texels, sessW,
                                                      line.bgColor, maxDistSq);
                const long modelColor = hasColor
                    ? modelInkColor(mo, alphaF, texels, mPlane)
                    : -1;

                // The head only answers the fog case — every core texel
                // still essentially on the paper means the camera never
                // resolved the ink, so there is no measurement to trust.
                const bool fog = maxDistSq < 32.0f * 32.0f;
                if (fog && modelColor >= 0) {
                    // Adopt the model unless it inverts against even the weak
                    // measurement (|lInk − lBg| tiny ≈ lamp flicker, not text).
                    const float lBg = luma8Of(line.bgColor);
                    const float lFg = luma8Of(static_cast<int>(modelColor));
                    const float lInk = luma8Of(measured);
                    if (std::fabs(lInk - lBg) <= 12.0f ||
                        (lFg - lBg) * (lInk - lBg) >= 0.0f) {
                        line.fgColor = static_cast<int>(modelColor);
                    } else {
                        line.fgColor = measured;
                    }
                } else {
                    line.fgColor = measured;
                }
    });
}

} // namespace ocr
