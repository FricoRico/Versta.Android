//
// Created by Ricardo Snoek on 9/26/25.
//

#include <algorithm>
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <vector>
#include <android/log.h>
#include <ocr-clipper/ocr_clipper.hpp>
#include "include/Log.h"

#if defined(__i386__) || defined(__x86_64__)

#include "neon-sse/NEON_2_SSE.h"

#else

#include <arm_neon.h>
#include <arm_vector_types.h>

#endif

/**
 * Text region metrics extracted during preprocessing
 * Contains colors, font properties, and layout information
 */
struct TextRegionMetrics {
    int backgroundColor;
    int textColor;
    int fontSize;
    int lineHeight;
    int fontWeight;
};

/**
 * PaddleOCR - OCR text detection and recognition
 * Optimized for mobile with minimal memory copies and efficient batch processing
 */
class PaddleOCR {
private:
    int detectSize_ = 320;
    int recognizeSize_ = 640;
    int cropHeight_ = 48;
    int maxCropSize_ = 480;
    float unclipRatio_ = 1.5;
    int maxCandidates_ = 100;
    int threads_ = 1;
    std::string characterType_ = "en";

    mutable cv::Mat cachedFrame_;
    mutable cv::Mat cachedResized_;
    mutable cv::Mat cachedPredictions_;
    mutable cv::Mat cachedBitmap_;
    mutable cv::Mat cachedCropImg_;
    mutable cv::Mat cachedPadded_;
    mutable std::vector<std::vector<std::vector<int>>> boxesCache_;

    static float **matToVec(cv::Mat mat) {
        auto **array = new float *[mat.rows];
        for (int i = 0; i < mat.rows; ++i) {
            array[i] = new float[mat.cols];
        }
        for (int i = 0; i < mat.rows; ++i) {
            for (int j = 0; j < mat.cols; ++j) {
                array[i][j] = mat.at<float>(i, j);
            }
        }

        return array;
    }

    void quickSort(float **s, int l, int r) {
        if (l < r) {
            int i = l, j = r;
            float x = s[l][0];
            float *xp = s[l];
            while (i < j) {
                while (i < j && s[j][0] >= x) {
                    j--;
                }
                if (i < j) {
                    std::swap(s[i++], s[j]);
                }
                while (i < j && s[i][0] < x) {
                    i++;
                }
                if (i < j) {
                    std::swap(s[j--], s[i]);
                }
            }
            s[i] = xp;
            quickSort(s, l, i - 1);
            quickSort(s, i + 1, r);
        }
    }

    void quickSortVector(std::vector<std::vector<int>> &box, int l, int r, int axis) {
        if (l < r) {
            int i = l, j = r;
            int x = box[l][axis];
            std::vector<int> xp(box[l]);
            while (i < j) {
                while (i < j && box[j][axis] >= x) {
                    j--;
                }
                if (i < j) {
                    std::swap(box[i++], box[j]);
                }
                while (i < j && box[i][axis] < x) {
                    i++;
                }
                if (i < j) {
                    std::swap(box[j--], box[i]);
                }
            }
            box[i] = xp;
            quickSortVector(box, l, i - 1, axis);
            quickSortVector(box, i + 1, r, axis);
        }
    }

    float **getMiniBoxes(cv::RotatedRect box, float &ssid) {
        ssid = box.size.width >= box.size.height ? box.size.height : box.size.width;

        cv::Mat points;
        cv::boxPoints(box, points);
        // sorted box points
        auto array = matToVec(points);
        quickSort(array, 0, 3);

        float *idx1, *idx2, *idx3, *idx4;
        if (array[3][1] <= array[2][1]) {
            idx2 = array[3];
            idx3 = array[2];
        } else {
            idx2 = array[2];
            idx3 = array[3];
        }
        if (array[1][1] <= array[0][1]) {
            idx1 = array[1];
            idx4 = array[0];
        } else {
            idx1 = array[0];
            idx4 = array[1];
        }

        array[0] = idx1;
        array[1] = idx2;
        array[2] = idx3;
        array[3] = idx4;

        return array;
    }

    template<class T>
    T clamp(T x, T min, T max) {
        if (x > max) {
            return max;
        }
        if (x < min) {
            return min;
        }
        return x;
    }

    float boxScoreFast(float **boxArray, const cv::Mat &pred) {
        auto array = boxArray;
        int width = pred.cols;
        int height = pred.rows;

        float boxX[4] = {array[0][0], array[1][0], array[2][0], array[3][0]};
        float boxY[4] = {array[0][1], array[1][1], array[2][1], array[3][1]};

        int xmin = clamp(int(std::floorf(*(std::min_element(boxX, boxX + 4)))), 0,
                         width - 1);
        int xmax = clamp(int(std::ceilf(*(std::max_element(boxX, boxX + 4)))), 0,
                         width - 1);
        int ymin = clamp(int(std::floorf(*(std::min_element(boxY, boxY + 4)))), 0,
                         height - 1);
        int ymax = clamp(int(std::ceilf(*(std::max_element(boxY, boxY + 4)))), 0,
                         height - 1);

        cv::Mat mask;
        mask = cv::Mat::zeros(ymax - ymin + 1, xmax - xmin + 1, CV_8UC1);

        cv::Point rootPoint[4];
        rootPoint[0] = cv::Point(int(array[0][0]) - xmin, int(array[0][1]) - ymin);
        rootPoint[1] = cv::Point(int(array[1][0]) - xmin, int(array[1][1]) - ymin);
        rootPoint[2] = cv::Point(int(array[2][0]) - xmin, int(array[2][1]) - ymin);
        rootPoint[3] = cv::Point(int(array[3][0]) - xmin, int(array[3][1]) - ymin);
        const cv::Point *ppt[1] = {rootPoint};
        int npt[] = {4};
        cv::fillPoly(mask, ppt, npt, 1, cv::Scalar(1));

        cv::Mat croppedImg;
        pred(cv::Rect(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1))
                .copyTo(croppedImg);

        auto score = cv::mean(croppedImg, mask)[0];
        return static_cast<float>(score);
    }

    static void getContourArea(float **box, float unclipRatio, float &distance) {
        int ptsNum = 4;
        float area = 0.0f;
        float dist = 0.0f;
        for (int i = 0; i < ptsNum; i++) {
            area += box[i][0] * box[(i + 1) % ptsNum][1] -
                    box[i][1] * box[(i + 1) % ptsNum][0];
            dist += sqrtf((box[i][0] - box[(i + 1) % ptsNum][0]) *
                          (box[i][0] - box[(i + 1) % ptsNum][0]) +
                          (box[i][1] - box[(i + 1) % ptsNum][1]) *
                          (box[i][1] - box[(i + 1) % ptsNum][1]));
        }
        area = fabs(float(area / 2.0));

        distance = area * unclipRatio / dist;
    }

    static cv::RotatedRect unclip(float **box, float unclipRatio, float distance) {
        getContourArea(box, unclipRatio, distance);

        ClipperLib::ClipperOffset offset;
        ClipperLib::Path p;
        p << ClipperLib::IntPoint(int(box[0][0]), int(box[0][1]))
          << ClipperLib::IntPoint(int(box[1][0]), int(box[1][1]))
          << ClipperLib::IntPoint(int(box[2][0]), int(box[2][1]))
          << ClipperLib::IntPoint(int(box[3][0]), int(box[3][1]));
        offset.AddPath(p, ClipperLib::jtRound, ClipperLib::etClosedPolygon);

        ClipperLib::Paths soln;
        offset.Execute(soln, distance);
        std::vector<cv::Point2f> points;

        for (int j = 0; j < soln.size(); j++) {
            for (int i = 0; i < soln[soln.size() - 1].size(); i++) {
                points.emplace_back(soln[j][i].X, soln[j][i].Y);
            }
        }
        cv::RotatedRect res = cv::minAreaRect(points);

        return res;
    }

    __attribute__((always_inline))
    static inline float clampf(float x, float min, float max) {
        if (x > max)
            return max;
        if (x < min)
            return min;
        return x;
    }

    std::vector<std::vector<std::vector<int>>>
    boxesFromBitmap(const cv::Mat &pred, const cv::Mat &bitmap) {
        const int minSize = 3;
        const float boxThresh = 0.5;

        int width = bitmap.cols;
        int height = bitmap.rows;

        std::vector<std::vector<cv::Point>> contours;
        contours.reserve(maxCandidates_);
        std::vector<cv::Vec4i> hierarchy;

        cv::findContours(bitmap, contours, hierarchy, cv::RETR_LIST,cv::CHAIN_APPROX_SIMPLE);

        int numContours = contours.size() >= maxCandidates_ ? maxCandidates_ : static_cast<int>(contours.size());

        std::vector<std::vector<std::vector<int>>> boxes;
        boxes.reserve(numContours);

        for (int i = 0; i < numContours; i++) {
            float ssid;
            cv::RotatedRect box = cv::minAreaRect(contours[i]);
            auto array = getMiniBoxes(box, ssid);

            auto boxForUnclip = array;
            if (ssid < minSize) {
                continue;
            }

            float score;
            score = boxScoreFast(array, pred);
            if (score < boxThresh) {
                continue;
            }

            cv::RotatedRect points = unclip(boxForUnclip, unclipRatio_, 1.0f);

            cv::RotatedRect clipbox = points;
            auto cliparray = getMiniBoxes(clipbox, ssid);

            if (ssid < minSize + 2)
                continue;

            int destWidth = pred.cols;
            int destHeight = pred.rows;
            std::vector<std::vector<int>> intcliparray;

            for (int numPt = 0; numPt < 4; numPt++) {
                std::vector<int> a{int(clampf(roundf(cliparray[numPt][0] / float(width) *
                                                     float(destWidth)),
                                              0, float(destWidth))),
                                   int(clampf(roundf(cliparray[numPt][1] /
                                                     float(height) * float(destHeight)),
                                              0, float(destHeight)))};
                intcliparray.emplace_back(a);
            }
            boxes.emplace_back(std::move(intcliparray));

        }
        return boxes;
    }

    std::vector<std::vector<int>>
    orderPointsClockwise(std::vector<std::vector<int>> pts) {
        std::vector<std::vector<int>> box = pts;
        quickSortVector(box, 0, int(box.size() - 1), 0);
        std::vector<std::vector<int>> leftmost = {box[0], box[1]};
        std::vector<std::vector<int>> rightmost = {box[2], box[3]};

        if (leftmost[0][1] > leftmost[1][1]) {
            std::swap(leftmost[0], leftmost[1]);
        }

        if (rightmost[0][1] > rightmost[1][1]) {
            std::swap(rightmost[0], rightmost[1]);
        }

        std::vector<std::vector<int>> rect = {leftmost[0], rightmost[0], rightmost[1],
                                              leftmost[1]};
        return rect;
    }

    int max(int a, int b) { return a >= b ? a : b; }

    int min(int a, int b) { return a >= b ? b : a; }

    std::vector<std::vector<std::vector<int>>>
    filterTagDetRes(const std::vector<std::vector<std::vector<int>>> &oBoxes,
                    float ratioW, float ratioH, float oriimgW, float oriimgH) {
        std::vector<std::vector<std::vector<int>>> boxes{oBoxes};
        std::vector<std::vector<std::vector<int>>> rootPoints;
        for (int n = 0; n < boxes.size(); n++) {
            boxes[n] = orderPointsClockwise(boxes[n]);
            for (int m = 0; m < boxes[0].size(); m++) {
                boxes[n][m][0] /= ratioW;
                boxes[n][m][1] /= ratioH;

                boxes[n][m][0] = int(min(max(boxes[n][m][0], 0), oriimgW - 1));
                boxes[n][m][1] = int(min(max(boxes[n][m][1], 0), oriimgH - 1));
            }
        }

        for (int n = 0; n < boxes.size(); n++) {
            int rectWidth, rectHeight;
            rectWidth = int(sqrt(pow(boxes[n][0][0] - boxes[n][1][0], 2) +
                                 pow(boxes[n][0][1] - boxes[n][1][1], 2)));
            rectHeight = int(sqrt(pow(boxes[n][0][0] - boxes[n][3][0], 2) +
                                  pow(boxes[n][0][1] - boxes[n][3][1], 2)));
            if (rectWidth <= 10 || rectHeight <= 10)
                continue;
            rootPoints.push_back(boxes[n]);
        }
        return rootPoints;
    }

    static void
    writeFilterBoxesToBuffer(const std::vector<std::vector<std::vector<int>>> &filterBoxes,
                             int *buffer, size_t bufferSize) {
        auto boxCount = int(filterBoxes.size());
        auto requiredSize = 1 + boxCount * 4 * 2 * int(sizeof(int));
        if (bufferSize < requiredSize) {
            throw std::runtime_error("Buffer too small to hold detected boxes");
        }

        buffer[0] = boxCount;
        auto idx = 1;
        for (const auto &box: filterBoxes) {
            for (const auto &point: box) {
                buffer[idx++] = point[0];
                buffer[idx++] = point[1];
            }
        }
    }

    static void resize(cv::Mat &image, int width, int height) {
        if (image.rows == height && image.cols == width) {
            return;
        }

        auto sourceAspectRatio = float(image.cols) / float(image.rows);
        auto targetAspectRatio = float(width) / float(height);

        int targetWidth, targetHeight;
        if (sourceAspectRatio > targetAspectRatio) {
            targetWidth = width;
            targetHeight = int(float(width) / sourceAspectRatio);
        } else {
            targetHeight = height;
            targetWidth = int(float(height) * sourceAspectRatio);
        }

        static cv::Mat resized;
        cv::resize(image, resized, cv::Size(targetWidth, targetHeight));
        image = cv::Mat(height, width, image.type(), cv::Scalar(0.485f, 0.456f, 0.406f));

        int x = (width - targetWidth) / 2;
        int y = (height - targetHeight) / 2;

        resized.copyTo(image(cv::Rect(x, y, targetWidth, targetHeight)));
    }

    static void rotateImg(cv::Mat &img, int angle) {
        if (angle == 0) {
            return;
        } else if (angle == 90) {
            cv::rotate(img, img, cv::ROTATE_90_CLOCKWISE);
        } else if (angle == 180) {
            cv::rotate(img, img, cv::ROTATE_180);
        } else if (angle == 270) {
            cv::rotate(img, img, cv::ROTATE_90_COUNTERCLOCKWISE);
        }
    }

    static void neonMeanScale(const float *din, float *dout, int size,
                              const std::vector<float> &mean,
                              const std::vector<float> &scale) {
        if (mean.size() != 3 || scale.size() != 3) {
            return;
        }

        float32x4_t vmean0 = vdupq_n_f32(mean[0]);
        float32x4_t vmean1 = vdupq_n_f32(mean[1]);
        float32x4_t vmean2 = vdupq_n_f32(mean[2]);
        float32x4_t vscale0 = vdupq_n_f32(scale[0]);
        float32x4_t vscale1 = vdupq_n_f32(scale[1]);
        float32x4_t vscale2 = vdupq_n_f32(scale[2]);

        float *doutC0 = dout;
        float *doutC1 = dout + size;
        float *doutC2 = dout + size * 2;

        int i = 0;
        for (; i < size - 3; i += 4) {
            float32x4x3_t vin3 = vld3q_f32(din);
            float32x4_t vsub0 = vsubq_f32(vin3.val[0], vmean0);
            float32x4_t vsub1 = vsubq_f32(vin3.val[1], vmean1);
            float32x4_t vsub2 = vsubq_f32(vin3.val[2], vmean2);
            float32x4_t vs0 = vmulq_f32(vsub0, vscale0);
            float32x4_t vs1 = vmulq_f32(vsub1, vscale1);
            float32x4_t vs2 = vmulq_f32(vsub2, vscale2);
            vst1q_f32(doutC0, vs0);
            vst1q_f32(doutC1, vs1);
            vst1q_f32(doutC2, vs2);

            din += 12;
            doutC0 += 4;
            doutC1 += 4;
            doutC2 += 4;
        }

        for (; i < size; i++) {
            *(doutC0++) = (*(din++) - mean[0]) * scale[0];
            *(doutC1++) = (*(din++) - mean[1]) * scale[1];
            *(doutC2++) = (*(din++) - mean[2]) * scale[2];
        }
    }

    static std::vector<std::vector<std::vector<int>>>
    bufferToFilterBoxes(const int *buffer, size_t bufferSize) {
        std::vector<std::vector<std::vector<int>>> boxes;
        if (bufferSize < 1) return boxes;

        int boxCount = static_cast<int>(buffer[0]);
        size_t expectedSize = 1 + boxCount * 4 * 2 * sizeof(int);
        if (bufferSize < expectedSize) return boxes;

        int idx = 1;
        for (int b = 0; b < boxCount; ++b) {
            std::vector<std::vector<int>> box;
            for (int p = 0; p < 4; ++p) {
                int x = static_cast<int>(buffer[idx++]);
                int y = static_cast<int>(buffer[idx++]);
                box.push_back({x, y});
            }
            boxes.push_back(box);
        }
        return boxes;
    }

    static cv::Mat getRotateCropImage(const cv::Mat &srcImage,
                                      const std::vector<std::vector<int>> &box) {
        std::vector<std::vector<int>> points = box;

        int xCollect[4] = {box[0][0], box[1][0], box[2][0], box[3][0]};
        int yCollect[4] = {box[0][1], box[1][1], box[2][1], box[3][1]};
        int left = int(*std::min_element(xCollect, xCollect + 4));
        int right = int(*std::max_element(xCollect, xCollect + 4));
        int top = int(*std::min_element(yCollect, yCollect + 4));
        int bottom = int(*std::max_element(yCollect, yCollect + 4));

        cv::Mat crop;
        srcImage(cv::Rect(left, top, right - left, bottom - top)).copyTo(crop);

        for (auto & point : points) {
            point[0] -= left;
            point[1] -= top;
        }

        auto width = int(sqrt(pow(points[0][0] - points[1][0], 2) +
                             pow(points[0][1] - points[1][1], 2)));
        auto height = int(sqrt(pow(points[0][0] - points[3][0], 2) +
                              pow(points[0][1] - points[3][1], 2)));

        cv::Point2f ptsStd[4];
        ptsStd[0] = cv::Point2f(0., 0.);
        ptsStd[1] = cv::Point2f(width, 0.);
        ptsStd[2] = cv::Point2f(width, height);
        ptsStd[3] = cv::Point2f(0.f, height);

        cv::Point2f pointsf[4];
        pointsf[0] = cv::Point2f(points[0][0], points[0][1]);
        pointsf[1] = cv::Point2f(points[1][0], points[1][1]);
        pointsf[2] = cv::Point2f(points[2][0], points[2][1]);
        pointsf[3] = cv::Point2f(points[3][0], points[3][1]);

        cv::Mat M = cv::getPerspectiveTransform(pointsf, ptsStd);

        cv::Mat dstImg;
        cv::warpPerspective(crop, dstImg, M,
                            cv::Size(width, height),
                            cv::BORDER_REPLICATE);

        if (float(dstImg.rows) >= float(dstImg.cols) * 1.5) {
            cv::transpose(dstImg, dstImg);
            cv::flip(dstImg, dstImg, 0);
            return dstImg;
        } else {
            return dstImg;
        }
    }

    cv::Mat aspectRatioResize(const cv::Mat &img, float aspectRatio) {
        auto height = cropHeight_;
        auto width = maxCropSize_;

        if (characterType_ == "ch") {
            width = int(32 * aspectRatio);
        }

        float ratio = float(img.cols) / float(img.rows);
        int resizeW = 0;
        if (ceilf(float(height) * ratio) > float(width))
            resizeW = width;
        else
            resizeW = int(ceilf(float(height) * ratio));
        cv::resize(img, cachedResized_, cv::Size(resizeW, height));
        return cachedResized_;
    }

    [[nodiscard]] cv::Mat padCropToRecShape(const cv::Mat &img) const {
        const int targetHeight = cropHeight_;
        const int targetWidth = maxCropSize_;

        cachedPadded_.create(targetHeight, targetWidth, img.type());
        cachedPadded_.setTo(cv::Scalar(0.5f, 0.5f, 0.5f));
        img.copyTo(cachedPadded_(cv::Rect(0, 0, img.cols, img.rows)));

        return cachedPadded_;
    }

    template<class ForwardIterator>
    size_t argmax(ForwardIterator first, ForwardIterator last) {
        return std::distance(first, std::max_element(first, last));
    }

    static void writeTokensToBuffer(const std::vector<std::vector<int>> &tokens,
                                    const std::vector<float> &scores,
                                    int *buffer, size_t bufferSize) {
        if (tokens.size() != scores.size()) {
            throw std::runtime_error("Tokens and scores size mismatch");
        }

        auto batchSize = tokens.size();

        buffer[0] = static_cast<int>(batchSize);
        buffer += 1;
        bufferSize -= sizeof(int);

        for (size_t i = 0; i < batchSize; ++i) {
            int wordCount = static_cast<int>(tokens[i].size());
            auto requiredSize = (2 + wordCount) * sizeof(int);

            if (bufferSize < requiredSize) {
                throw std::runtime_error(
                        "Buffer too small to hold detected boxes for batch " + std::to_string(i));
            }

            buffer[0] = wordCount;
            buffer[1] = static_cast<int>(scores[i] * 1000);

            int idx = 2;
            for (const auto &word: tokens[i]) {
                buffer[idx++] = word;
            }

            buffer += static_cast<size_t>(requiredSize / sizeof(int));
            bufferSize -= requiredSize;
        }
    }

    static std::pair<cv::Scalar, cv::Scalar> extractColors(const cv::Mat &image) {
        float whRatio = static_cast<float>(image.cols) / image.rows;
        cv::Mat resized;
        cv::resize(image, resized, cv::Size(static_cast<int>(32 * whRatio), 32));

        cv::Mat pixels = resized.reshape(1, resized.rows * resized.cols);
        pixels.convertTo(pixels, CV_32F);

        cv::Mat labels, centers;
        cv::kmeans(pixels, 2, labels,
                   cv::TermCriteria(cv::TermCriteria::EPS + cv::TermCriteria::MAX_ITER, 5, 2.0),
                   2, cv::KMEANS_PP_CENTERS, centers);

        cv::Scalar cluster0(centers.at<float>(0, 0), centers.at<float>(0, 1), centers.at<float>(0, 2));
        cv::Scalar cluster1(centers.at<float>(1, 0), centers.at<float>(1, 1), centers.at<float>(1, 2));

        int textLabel = (cv::countNonZero(labels == 0) < cv::countNonZero(labels == 1)) ? 0 : 1;
        cv::Scalar textColor = (textLabel == 0) ? cluster0 : cluster1;
        cv::Scalar backgroundColor = (textLabel == 0) ? cluster1 : cluster0;

        enhanceContrast(backgroundColor, textColor);

        return {backgroundColor, textColor};
    }

    static void enhanceContrast(cv::Scalar &background, cv::Scalar &text) {
        auto luminance = [](const cv::Scalar &c) { return 0.299f * c[2] + 0.587f * c[1] + 0.114f * c[0]; };
        
        float textLum = luminance(text);
        float bgLum = luminance(background);
        const float factor = 0.3f;

        if (textLum < bgLum) {
            for (int i = 0; i < 3; i++) text[i] = std::max<float>(0.0f, text[i] * (1.0f - factor * 0.3f));
        } else {
            for (int i = 0; i < 3; i++) text[i] = std::min<float>(255.0f, text[i] * (1.0f + factor * 0.5f));
        }

        if (bgLum < textLum) {
            for (int i = 0; i < 3; i++) background[i] = std::max<float>(0.0f, background[i] * (1.0f - factor * 0.3f));
        } else {
            for (int i = 0; i < 3; i++) background[i] = std::min<float>(255.0f, background[i] * (1.0f + factor * 0.5f));
        }
    }

public:
    PaddleOCR(
            int detectSize,
            int recognizeSize,
            int cropHeight,
            int maxCropSize,
            float unclipRatio,
            int maxCandidates,
            int threads
    ) :
            detectSize_(detectSize),
            recognizeSize_(recognizeSize),
            cropHeight_(cropHeight),
            maxCropSize_(maxCropSize),
            unclipRatio_(unclipRatio),
            maxCandidates_(maxCandidates),
            threads_(threads) {
        boxesCache_.reserve(maxCandidates);
    }

    jboolean
    preProcessDetect(JNIEnv *env, jobject input, jobject output, int inputWidth, int inputHeight, int outputRotation) const {
        cv::setNumThreads(threads_);

        auto *inputData = static_cast<uint8_t *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(output));
        if (!inputData || !outputData) return JNI_FALSE;

        const std::vector<float> mean = {0.485f, 0.456f, 0.406f};
        const std::vector<float> scale = {1 / 0.229f, 1 / 0.224f, 1 / 0.225f};

        cv::Mat frame(inputHeight, inputWidth, CV_8UC4, inputData);
        cv::cvtColor(frame, frame, cv::COLOR_RGBA2BGR);

        rotateImg(frame, outputRotation);
        resize(frame, detectSize_, detectSize_);
        frame.convertTo(frame, CV_32FC3, 1.0f / 255.0f);

        const int size = frame.cols * frame.rows;
        neonMeanScale(reinterpret_cast<const float *>(frame.data), outputData, size, mean, scale);

        return JNI_TRUE;
    }

    jboolean
    postProcessDetect(JNIEnv *env, jobject input, jobject output, float threshold, float maxValue) {
        cv::setNumThreads(this->threads_);

        auto *inputData = static_cast<float *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<int *>(env->GetDirectBufferAddress(output));
        if (!inputData || !outputData) return JNI_FALSE;

        cachedPredictions_ = cv::Mat(detectSize_, detectSize_, CV_32F, inputData);

        cachedPredictions_.convertTo(cachedBitmap_, CV_8UC1);

        cv::threshold(cachedBitmap_, cachedBitmap_, threshold, maxValue, cv::THRESH_BINARY);

        boxesCache_ = boxesFromBitmap(cachedPredictions_, cachedBitmap_);

        float ratioWidth = detectSize_ * 1.0f / recognizeSize_;
        float ratioHeight = detectSize_ * 1.0f / recognizeSize_;

        boxesCache_ = filterTagDetRes(boxesCache_, ratioWidth, ratioHeight, recognizeSize_, recognizeSize_);

        writeFilterBoxesToBuffer(boxesCache_, outputData, env->GetDirectBufferCapacity(output));

        return JNI_TRUE;
    }

    jboolean
    preProcessRecognize(JNIEnv *env, jobject origin, jobject input, jobject output, int originWidth,
                        int originHeight, int originRotation) {
        cv::setNumThreads(threads_);

        auto *originData = static_cast<uint8_t *>(env->GetDirectBufferAddress(origin));
        auto *inputData = static_cast<int *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(output));
        if (!originData || !inputData || !outputData)
            throw std::runtime_error("Failed to get direct buffer address.");

        auto boxes = bufferToFilterBoxes(inputData, env->GetDirectBufferCapacity(input));
        if (boxes.empty()) return JNI_FALSE;

        cv::Mat frame(originHeight, originWidth, CV_8UC4, originData);
        cv::cvtColor(frame, frame, cv::COLOR_RGBA2BGR);

        rotateImg(frame, originRotation);
        resize(frame, recognizeSize_, recognizeSize_);
        frame.convertTo(frame, CV_32FC3, 1.0f / 255.0f);

        const std::vector<float> mean = {0.5f, 0.5f, 0.5f};
        const std::vector<float> scale = {1 / 0.5f, 1 / 0.5f, 1 / 0.5f};

        int batchCount = 0;
        int outputIndex = 0;

        for (auto bp = boxes.crbegin(); bp != boxes.crend(); ++bp) {
            const std::vector<std::vector<int>> &box = *bp;
            cachedCropImg_ = getRotateCropImage(frame, box);

            float whRatio = float(cachedCropImg_.cols) / float(cachedCropImg_.rows);
            cachedCropImg_ = aspectRatioResize(cachedCropImg_, whRatio);
            cachedCropImg_ = padCropToRecShape(cachedCropImg_);

            if (cachedCropImg_.cols != maxCropSize_ || cachedCropImg_.rows != 48) {
                throw std::runtime_error("Cropped image has incorrect shape.");
            }

            neonMeanScale(reinterpret_cast<const float *>(cachedCropImg_.data), outputData + outputIndex,
                          cachedCropImg_.cols * cachedCropImg_.rows, mean, scale);
            outputIndex += cachedCropImg_.cols * cachedCropImg_.rows * 3;

            batchCount++;
            if (batchCount >= maxCandidates_) break;
        }

        return JNI_TRUE;
    }

    jboolean postProcessRecognize(JNIEnv *env, jobject outputBuffer, jlongArray outputShape,
                                  jobject tokenBuffer) {
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(outputBuffer));
        auto *tokenData = static_cast<int *>(env->GetDirectBufferAddress(tokenBuffer));
        if (!outputData || !tokenData) return JNI_FALSE;

        auto *shapeArray = env->GetLongArrayElements(outputShape, nullptr);
        int batchSize = static_cast<int>(shapeArray[0]);
        int seqLen = static_cast<int>(shapeArray[1]); // Usually crop width divided by 8, so 320 width -> 40
        int vocabSize = static_cast<int>(shapeArray[2]);
        env->ReleaseLongArrayElements(outputShape, shapeArray, 0);

        std::vector<std::vector<int>> batchTokens;
        std::vector<float> batchScores;

        for (int b = 0; b < batchSize; ++b) {
            const float *batchStart = outputData + (b * seqLen * vocabSize);

            std::vector<int> tokens;
            float score = 0.f;
            int lastIndex = 0;
            int count = 0;

            for (int n = 0; n < seqLen; ++n) {
                const float *start = batchStart + (n * vocabSize);
                const float *end = start + vocabSize;
                int argmaxIndex = int(argmax(start, end));
                float maxValue = *std::max_element(start, end);

                if (argmaxIndex > 0 && !(n > 0 && argmaxIndex == lastIndex)) {
                    score += maxValue;
                    count += 1;
                    tokens.push_back(argmaxIndex);
                }
                lastIndex = argmaxIndex;
            }

            score /= std::max(1.f, static_cast<float>(count));

            batchTokens.push_back(tokens);
            batchScores.push_back(score);
        }

        writeTokensToBuffer(batchTokens, batchScores, tokenData,
                            env->GetDirectBufferCapacity(tokenBuffer));

        return JNI_TRUE;
    }

    static int packColor(const cv::Scalar& bgrColor) {
        return (255 << 24) | 
               (static_cast<int>(bgrColor[2] + 0.5f) << 16) |  // Red
               (static_cast<int>(bgrColor[1] + 0.5f) << 8) |   // Green
               static_cast<int>(bgrColor[0] + 0.5f);             // Blue
    }

    static TextRegionMetrics extractRegionMetrics(const cv::Mat& crop, const std::vector<std::vector<int>>& box) {
        auto [backgroundColor, textColor] = extractColors(crop);
        
        float leftHeight = std::sqrt(std::pow(box[3][0] - box[0][0], 2) + std::pow(box[3][1] - box[0][1], 2));
        float rightHeight = std::sqrt(std::pow(box[2][0] - box[1][0], 2) + std::pow(box[2][1] - box[1][1], 2));
        float boxHeight = (leftHeight + rightHeight) / 2.0f;
        float textImageHeight = static_cast<float>(crop.rows);
        float fontSize = (0.4f * textImageHeight + 0.6f * boxHeight) * 0.8f;
        
        float lineHeight = textImageHeight + fontSize * 0.2f;
        
        cv::Scalar mean, stddev;
        cv::meanStdDev(crop, mean, stddev);
        float variance = stddev[0] * stddev[0];
        int fontWeight = (variance / 3600.0f > 0.66f) ? 1 : 0;
        
        return {
            packColor(backgroundColor),
            packColor(textColor),
            static_cast<int>(fontSize * 100),
            static_cast<int>(lineHeight * 100),
            fontWeight
        };
    }

    jintArray preprocessTextRegions(JNIEnv *env, jobject origin, jobject input, jobject output,
                                     int originWidth, int originHeight, int originRotation) {
        cv::setNumThreads(threads_);

        auto *originData = static_cast<uint8_t *>(env->GetDirectBufferAddress(origin));
        auto *inputData = static_cast<int *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(output));
        
        if (!originData || !inputData || !outputData) {
            throw std::runtime_error("Failed to get direct buffer address");
        }

        auto boxes = bufferToFilterBoxes(inputData, env->GetDirectBufferCapacity(input));
        if (boxes.empty()) {
            return env->NewIntArray(0);
        }

        std::vector<jint> allMetrics;
        allMetrics.reserve(1 + boxes.size() * 5);
        allMetrics.push_back(static_cast<jint>(boxes.size()));

        cv::Mat frame(originHeight, originWidth, CV_8UC4, originData);
        cv::cvtColor(frame, frame, cv::COLOR_RGBA2BGR);
        rotateImg(frame, originRotation);
        resize(frame, recognizeSize_, recognizeSize_);

        const std::vector<float> mean = {0.5f, 0.5f, 0.5f};
        const std::vector<float> scale = {1 / 0.5f, 1 / 0.5f, 1 / 0.5f};

        int batchCount = 0;
        int outputIndex = 0;

        for (auto bp = boxes.crbegin(); bp != boxes.crend() && batchCount < maxCandidates_; ++bp) {
            cachedCropImg_ = getRotateCropImage(frame, *bp);
            
            auto metrics = extractRegionMetrics(cachedCropImg_, *bp);
            allMetrics.push_back(metrics.backgroundColor);
            allMetrics.push_back(metrics.textColor);
            allMetrics.push_back(metrics.fontSize);
            allMetrics.push_back(metrics.lineHeight);
            allMetrics.push_back(metrics.fontWeight);

            cachedCropImg_.convertTo(cachedCropImg_, CV_32FC3, 1.0f / 255.0f);
            float whRatio = float(cachedCropImg_.cols) / float(cachedCropImg_.rows);
            cachedCropImg_ = aspectRatioResize(cachedCropImg_, whRatio);
            cachedCropImg_ = padCropToRecShape(cachedCropImg_);

            if (cachedCropImg_.cols != maxCropSize_ || cachedCropImg_.rows != 48) {
                throw std::runtime_error("Cropped image has incorrect shape");
            }

            neonMeanScale(reinterpret_cast<const float *>(cachedCropImg_.data), 
                         outputData + outputIndex,
                         cachedCropImg_.cols * cachedCropImg_.rows, mean, scale);
            outputIndex += cachedCropImg_.cols * cachedCropImg_.rows * 3;
            batchCount++;
        }

        jintArray result = env->NewIntArray(static_cast<int>(allMetrics.size()));
        if (!result) {
            throw std::runtime_error("Failed to create metrics array");
        }
        env->SetIntArrayRegion(result, 0, static_cast<int>(allMetrics.size()), allMetrics.data());
        return result;
    }
};

std::unordered_map<jlong, std::unique_ptr<PaddleOCR>> paddleOCRInstances;
jlong paddleOCRInstanceCounter = 0;

extern "C" JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_construct(
        JNIEnv *env,
        jobject,
        jint detectSize,
        jint recognizeSize,
        jint cropHeight,
        jint maxCropSize,
        jfloat unclipRatio,
        jint maxCandidates,
        jint threads
) {
    auto paddleOCR = std::make_unique<PaddleOCR>(
            detectSize,
            recognizeSize,
            cropHeight,
            maxCropSize,
            unclipRatio,
            maxCandidates,
            threads
    );

    jlong handle = ++paddleOCRInstanceCounter;
    paddleOCRInstances[handle] = std::move(paddleOCR);
    return handle;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_close(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    if (paddleOCRInstances.erase(handle) > 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_preProcessDetect(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject input,
        jobject output,
        jint inputWidth,
        jint inputHeight,
        jint outputRotation
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return JNI_FALSE;
    }

    return paddleOCR->preProcessDetect(env, input, output, inputWidth, inputHeight, outputRotation);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_postProcessDetect(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject input,
        jobject output,
        jfloat threshold,
        jfloat maxValue
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return 0;
    }

    return paddleOCR->postProcessDetect(env, input, output, threshold, maxValue);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_preProcessRecognize(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject origin,
        jobject input,
        jobject output,
        jint originWidth,
        jint originHeight,
        jint originRotation
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return JNI_FALSE;
    }

    return paddleOCR->preProcessRecognize(env, origin, input, output, originWidth, originHeight, originRotation);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_postProcessRecognize(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject outputBuffer,
        jlongArray outputShape,
        jobject tokenBuffer
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return JNI_FALSE;
    }

    return paddleOCR->postProcessRecognize(env, outputBuffer, outputShape, tokenBuffer);
}

/**
 * JNI export for text region preprocessing with visual metrics extraction
 * Combines recognition preprocessing with color/style extraction in a single pass
 * Eliminates duplicate image processing and memory copies
 */
extern "C"
JNIEXPORT jintArray JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_preprocessTextRegions(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject origin,
        jobject input,
        jobject output,
        jint originWidth,
        jint originHeight,
        jint originRotation
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return nullptr;
    }

    return paddleOCR->preprocessTextRegions(env, origin, input, output, originWidth, originHeight, originRotation);
}
