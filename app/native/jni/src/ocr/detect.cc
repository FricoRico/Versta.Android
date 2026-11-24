//
// OCR detection: DB heatmap -> binary mask -> contours -> filtered boxes in
// upright-image pixel space, with contours scaled back for dewarping.
//
// Constants and geometry mirror translator-ocr/ppocr.rs (`detect_with_thresholds`,
// `extract_boxes`).
//

#define LOG_TAG "VerstaOcr"

#include <cmath>

#include "include/Log.h"
#include "include/ocr_pipeline.h"

namespace ocr {

/// u8 -> normalized-f32 per-channel lookup: the input is u8, so
/// (v/255 - mean)/std has only 256 possible outputs per channel.
static float normLut[3][256];
static bool normLutInitialized = [] {
    for (int c = 0; c < 3; c++) {
        for (int v = 0; v < 256; v++) {
            normLut[c][v] = (v / 255.0f - PPOCR_DET_MEAN[c]) / PPOCR_DET_STD[c];
        }
    }
    return true;
}();

struct DetThresholds {
    float boxMinScore;
    int minArea;
};

static DetThresholds thresholdsFor(Profile profile) {
    if (profile == Profile::Live) {
        return {LIVE_DET_BOX_MIN_SCORE, LIVE_DET_MIN_AREA};
    }
    return {DET_BOX_MIN_SCORE, DET_MIN_AREA};
}

/// Resize policy: live frames fit the pixel budget (det scales linearly with
/// pixel count), stills cap at DET_MAX_SIDE. No upscale, and the content dims
/// land on multiples of 32 so only zero-padding to the model's stride is left.
static cv::Mat resizeForDet(const cv::Mat& image, Profile profile, float& scale) {
    const int w = image.cols, h = image.rows;
    scale = 1.0f;

    if (profile == Profile::Live) {
        const double pixels = static_cast<double>(w) * h;
        if (pixels <= DET_LIVE_PIXEL_BUDGET) return image;
        scale = std::sqrt(DET_LIVE_PIXEL_BUDGET / pixels);
    } else {
        const int maxSide = std::max(w, h);
        if (maxSide <= DET_MAX_SIDE) return image;
        scale = DET_MAX_SIDE / static_cast<float>(maxSide);
    }

    const int nw = std::max(1, static_cast<int>(w * scale) / 32 * 32);
    const int nh = std::max(1, static_cast<int>(h * scale) / 32 * 32);
    cv::Mat out;
    cv::resize(image, out, {nw, nh}, 0, 0, cv::INTER_AREA);
    return out;
}

/// ImageNet-normalized NCHW float buffer: content copied into a zero-padded
/// multiple-of-32 canvas (padding carries no text; the mask content region
/// maps back onto the original via content, not padded, dims).
static std::vector<float> nchwForDet(const cv::Mat& image, int padW, int padH) {
    std::vector<float> buffer(3 * padW * padH, 0.0f);
    const int plane = padW * padH;
    const int w = image.cols, h = image.rows;
    for (int y = 0; y < h; y++) {
        const auto* row = image.ptr<cv::Vec3b>(y);
        const int base = y * padW;
        for (int x = 0; x < w; x++) {
            const int idx = base + x;
            buffer[idx] = normLut[0][row[x][0]];
            buffer[plane + idx] = normLut[1][row[x][1]];
            buffer[2 * plane + idx] = normLut[2][row[x][2]];
        }
    }
    return buffer;
}

std::vector<TextBox> Engine::detect(const cv::Mat& upright, Profile profile) {
    const int origW = upright.cols, origH = upright.rows;
    const auto thresholds = thresholdsFor(profile);

    float scale;
    cv::Mat scaled = resizeForDet(upright, profile, scale);
    const int contentW = scaled.cols, contentH = scaled.rows;

    // DBNet requires multiple-of-32 dims; pad up with zeros instead of resampling.
    const int scaledW = (contentW + 31) / 32 * 32;
    const int scaledH = (contentH + 31) / 32 * 32;

    auto input = nchwForDet(scaled, scaledW, scaledH);
    std::vector<int> outShape;
    const auto mask = _detector->run(_detSession->session, input,
                                     {1, 3, scaledH, scaledW}, outShape);
    if (outShape.size() < 4) {
        LOGE("OCR: detector output shape unexpected (%zu dims)", outShape.size());
        return {};
    }

    const int outW = outShape[outShape.size() - 1];
    const int outH = outShape[outShape.size() - 2];

    cv::Mat binary(outH, outW, CV_8UC1);
    for (int i = 0; i < outW * outH; i++) {
        binary.data[i] = mask[i] > DET_SCORE_THRESHOLD ? 255 : 0;
    }

    // The mask may be emitted below input resolution (folded heads); all
    // geometry then works in mask-grid units and only the final scale maps
    // back into the upright image.
    const float strideX = scaledW / static_cast<float>(outW);
    const float strideY = scaledH / static_cast<float>(outH);
    const int contentMaskW = static_cast<int>(std::lround(contentW / strideX));
    const int contentMaskH = static_cast<int>(std::lround(contentH / strideY));
    _detScale = origW / static_cast<float>(std::max(1, contentW));
    const float maskStride = std::sqrt(strideX * strideY);
    const int minAreaMask = std::max(1, static_cast<int>(std::lround(thresholds.minArea / (strideX * strideY))));

    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;
    cv::findContours(binary, contours, hierarchy, cv::RETR_CCOMP, cv::CHAIN_APPROX_SIMPLE);

    // A mask pixel covers a stride^2 block; mapping its index to the block's
    // top-left corner biases every coordinate toward the origin, so recenter.
    const float poolComp = 1.0f - 1.0f / maskStride;
    const float centerOff = poolComp;
    const float scaleX = origW / static_cast<float>(contentMaskW ? contentMaskW : 1);
    const float scaleY = origH / static_cast<float>(contentMaskH ? contentMaskH : 1);

    std::vector<TextBox> boxes;
    for (size_t i = 0; i < contours.size(); i++) {
        // Skip holes / children.
        if (!hierarchy.empty() && hierarchy[i][3] >= 0) continue;
        if (contours[i].size() < 4) continue;

        cv::Rect aabb = cv::boundingRect(contours[i]);
        if (aabb.x >= contentMaskW || aabb.y >= contentMaskH) continue;
        aabb &= cv::Rect(0, 0, contentMaskW, contentMaskH);
        if (aabb.width * aabb.height < minAreaMask) continue;

        // Box-score gate: mean heatmap probability over the contour's binarized
        // interior (cheap approximation of upstream's "slow" polygon mode).
        double scoreSum = 0;
        int scoreN = 0;
        for (int y = aabb.y; y < aabb.y + aabb.height; y++) {
            const int row = y * outW;
            for (int x = aabb.x; x < aabb.x + aabb.width; x++) {
                if (binary.data[row + x] != 0) {
                    scoreSum += mask[row + x];
                    scoreN += 1;
                }
            }
        }
        const float boxScore = scoreN > 0 ? static_cast<float>(scoreSum / scoreN) : 0.0f;
        if (boxScore < thresholds.boxMinScore) continue;

        // DB unclip, aspect-independent: upstream's area*ratio/perimeter depends
        // on the box shape; the long-box limit ratio*t/2 inflates every line by
        // the same proportion of its stroke-band thickness.
        const float thickness = static_cast<float>(std::min(aabb.width, aabb.height));
        const float expandDist = detUnclipDistance(thickness) + poolComp;

        cv::Rect2f expanded(
            std::max(0.0f, aabb.x + centerOff - expandDist),
            std::max(0.0f, aabb.y + centerOff - expandDist),
            aabb.width + 2 * expandDist,
            aabb.height + 2 * expandDist);
        expanded &= cv::Rect2f(0, 0, contentMaskW, contentMaskH);

        TextBox box;
        box.aabb = cv::Rect(
            std::max(0, static_cast<int>(expanded.x * scaleX) - DET_BOX_BORDER),
            std::max(0, static_cast<int>(expanded.y * scaleY) - DET_BOX_BORDER),
            0, 0);
        box.aabb.width = std::min(origW - box.aabb.x,
            static_cast<int>(expanded.width * scaleX) + 2 * DET_BOX_BORDER);
        box.aabb.height = std::min(origH - box.aabb.y,
            static_cast<int>(expanded.height * scaleY) + 2 * DET_BOX_BORDER);
        if (box.aabb.width <= 0 || box.aabb.height <= 0) continue;

        box.score = boxScore;
        box.contour.reserve(contours[i].size());
        for (const auto& p : contours[i]) {
            box.contour.push_back({
                (p.x + centerOff) * scaleX,
                (p.y + centerOff) * scaleY
            });
        }
        for (int c = 0; c < 4; c++) {
            box.corners[c] = {
                static_cast<float>(c == 1 || c == 2 ? box.aabb.x + box.aabb.width : box.aabb.x),
                static_cast<float>(c < 2 ? box.aabb.y : box.aabb.y + box.aabb.height)
            };
        }
        boxes.push_back(std::move(box));
    }

    orientBoxes(boxes);
    return boxes;
}

} // namespace ocr
