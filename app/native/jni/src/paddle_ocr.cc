//
// Created by Ricardo Snoek on 9/26/25.
//

#include <algorithm>
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <vector>
#include <unordered_map>
#include <memory>
#include <android/log.h>
#include <ocr-clipper/ocr_clipper.hpp>
#include "include/Log.h"

#if defined(__i386__) || defined(__x86_64__)

#include "neon-sse/NEON_2_SSE.h"

#else

#include <arm_neon.h>
#include <arm_vector_types.h>

#endif

static float **Mat2Vec(cv::Mat mat) {
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

static void quickSort(float **s, int l, int r) {
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

static float **get_mini_boxes(cv::RotatedRect box, float &ssid) {
    ssid = box.size.width >= box.size.height ? box.size.height : box.size.width;

    cv::Mat points;
    cv::boxPoints(box, points);
    // sorted box points
    auto array = Mat2Vec(points);
    quickSort(array, 0, 3);

    float *idx1 = array[0], *idx2 = array[1], *idx3 = array[2], *idx4 = array[3];
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

float box_score_fast(float **box_array, cv::Mat pred) {
    auto array = box_array;
    int width = pred.cols;
    int height = pred.rows;

    float box_x[4] = {array[0][0], array[1][0], array[2][0], array[3][0]};
    float box_y[4] = {array[0][1], array[1][1], array[2][1], array[3][1]};

    int xmin = clamp(int(std::floorf(*(std::min_element(box_x, box_x + 4)))), 0,
                     width - 1);
    int xmax = clamp(int(std::ceilf(*(std::max_element(box_x, box_x + 4)))), 0,
                     width - 1);
    int ymin = clamp(int(std::floorf(*(std::min_element(box_y, box_y + 4)))), 0,
                     height - 1);
    int ymax = clamp(int(std::ceilf(*(std::max_element(box_y, box_y + 4)))), 0,
                     height - 1);

    cv::Mat mask;
    mask = cv::Mat::zeros(ymax - ymin + 1, xmax - xmin + 1, CV_8UC1);

    cv::Point root_point[4];
    root_point[0] = cv::Point(int(array[0][0]) - xmin, int(array[0][1]) - ymin);
    root_point[1] = cv::Point(int(array[1][0]) - xmin, int(array[1][1]) - ymin);
    root_point[2] = cv::Point(int(array[2][0]) - xmin, int(array[2][1]) - ymin);
    root_point[3] = cv::Point(int(array[3][0]) - xmin, int(array[3][1]) - ymin);
    const cv::Point *ppt[1] = {root_point};
    int npt[] = {4};
    cv::fillPoly(mask, ppt, npt, 1, cv::Scalar(1));

    cv::Mat croppedImg;
    pred(cv::Rect(xmin, ymin, xmax - xmin + 1, ymax - ymin + 1))
            .copyTo(croppedImg);

    auto score = cv::mean(croppedImg, mask)[0];
    return score;
}

static void getcontourarea(float **box, float unclip_ratio, float &distance) {
    int pts_num = 4;
    float area = 0.0f;
    float dist = 0.0f;
    for (int i = 0; i < pts_num; i++) {
        area += box[i][0] * box[(i + 1) % pts_num][1] -
                box[i][1] * box[(i + 1) % pts_num][0];
        dist += sqrtf((box[i][0] - box[(i + 1) % pts_num][0]) *
                      (box[i][0] - box[(i + 1) % pts_num][0]) +
                      (box[i][1] - box[(i + 1) % pts_num][1]) *
                      (box[i][1] - box[(i + 1) % pts_num][1]));
    }
    area = fabs(float(area / 2.0));

    distance = area * unclip_ratio / dist;
}

static cv::RotatedRect unclip(float **box) {
    float unclip_ratio = 1.5;
    float distance = 1.0;

    getcontourarea(box, unclip_ratio, distance);

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

static float clampf(float x, float min, float max) {
    if (x > max)
        return max;
    if (x < min)
        return min;
    return x;
}

std::vector<std::vector<std::vector<int>>>
boxes_from_bitmap(const cv::Mat &pred, const cv::Mat &bitmap) {
    const int min_size = 3;
    const int max_candidates = 100;
    const float box_thresh = 0.5;

    int width = bitmap.cols;
    int height = bitmap.rows;

    std::vector<std::vector<cv::Point>> contours;
    std::vector<cv::Vec4i> hierarchy;

    cv::findContours(bitmap, contours, hierarchy, cv::RETR_LIST,
                     cv::CHAIN_APPROX_SIMPLE);

    int num_contours =
            contours.size() >= max_candidates ? max_candidates : contours.size();

    std::vector<std::vector<std::vector<int>>> boxes;

    for (int _i = 0; _i < num_contours; _i++) {
        float ssid;
        cv::RotatedRect box = cv::minAreaRect(contours[_i]);
        auto array = get_mini_boxes(box, ssid);

        auto box_for_unclip = array;
        // end get_mini_box

        if (ssid < min_size) {
            continue;
        }

        float score;
        score = box_score_fast(array, pred);
        // end box_score_fast
        if (score < box_thresh) {
            continue;
        }

        // start for unclip
        cv::RotatedRect points = unclip(box_for_unclip);
        // end for unclip

        cv::RotatedRect clipbox = points;
        auto cliparray = get_mini_boxes(clipbox, ssid);

        if (ssid < min_size + 2)
            continue;

        int dest_width = pred.cols;
        int dest_height = pred.rows;
        std::vector<std::vector<int>> intcliparray;

        for (int num_pt = 0; num_pt < 4; num_pt++) {
            std::vector<int> a{int(clampf(roundf(cliparray[num_pt][0] / float(width) *
                                                 float(dest_width)),
                                          0, float(dest_width))),
                               int(clampf(roundf(cliparray[num_pt][1] /
                                                 float(height) * float(dest_height)),
                                          0, float(dest_height)))};
            intcliparray.emplace_back(std::move(a));
        }
        boxes.emplace_back(std::move(intcliparray));

    } // end for
    return boxes;
}

static void quickSort_vector(std::vector<std::vector<int>> &box, int l, int r,
                             int axis) {
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
        quickSort_vector(box, l, i - 1, axis);
        quickSort_vector(box, i + 1, r, axis);
    }
}

static std::vector<std::vector<int>>
order_points_clockwise(std::vector<std::vector<int>> pts) {
    std::vector<std::vector<int>> box = pts;
    quickSort_vector(box, 0, int(box.size() - 1), 0);
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

int _max(int a, int b) { return a >= b ? a : b; }

int _min(int a, int b) { return a >= b ? b : a; }

std::vector<std::vector<std::vector<int>>>
filter_tag_det_res(const std::vector<std::vector<std::vector<int>>> &o_boxes,
                   float ratio_w, float ratio_h, float oriimg_w, float oriimg_h) {
    std::vector<std::vector<std::vector<int>>> boxes{o_boxes};
    std::vector<std::vector<std::vector<int>>> root_points;
    for (int n = 0; n < boxes.size(); n++) {
        boxes[n] = order_points_clockwise(boxes[n]);
        for (int m = 0; m < boxes[0].size(); m++) {
            boxes[n][m][0] /= ratio_w;
            boxes[n][m][1] /= ratio_h;

            boxes[n][m][0] = int(_min(_max(boxes[n][m][0], 0), oriimg_w - 1));
            boxes[n][m][1] = int(_min(_max(boxes[n][m][1], 0), oriimg_h - 1));
        }
    }

    for (int n = 0; n < boxes.size(); n++) {
        int rect_width, rect_height;
        rect_width = int(sqrt(pow(boxes[n][0][0] - boxes[n][1][0], 2) +
                              pow(boxes[n][0][1] - boxes[n][1][1], 2)));
        rect_height = int(sqrt(pow(boxes[n][0][0] - boxes[n][3][0], 2) +
                               pow(boxes[n][0][1] - boxes[n][3][1], 2)));
        if (rect_width <= 10 || rect_height <= 10)
            continue;
        root_points.push_back(boxes[n]);
    }
    return root_points;
}

void write_filter_boxes_to_buffer(const std::vector<std::vector<std::vector<int>>> &filter_boxes,
                                  int *buffer, size_t buffer_size) {
    int box_count = static_cast<int>(filter_boxes.size());
    int required_size = 1 + box_count * 4 * 2 * sizeof(int);
    if (buffer_size < required_size) {
        throw std::runtime_error("Buffer too small to hold detected boxes");
    }

    buffer[0] = box_count;
    int idx = 1;
    for (const auto &box: filter_boxes) {
        for (const auto &point: box) {
            buffer[idx++] = point[0];
            buffer[idx++] = point[1];
        }
    }
}

// PaddleOCR class for managing OCR operations with configurable properties
class PaddleOCR {
public:
    PaddleOCR(int cropWidth, int threads)
            : cropWidth(cropWidth),
              threads(threads),
              recImageShape({3, 48, cropWidth}) {
        cv::setNumThreads(threads);
    }

    int postProcessDetect(
            JNIEnv *env,
            jobject input,
            jobject output,
            int inputWidth,
            int inputHeight,
            int outputWidth,
            int outputHeight,
            float threshold,
            int maxValue
    ) {
        auto *inputData = static_cast<float *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<int *>(env->GetDirectBufferAddress(output));
        if (!inputData || !outputData) return 0;

        pred = cv::Mat::zeros(inputWidth, inputHeight, CV_32F);
        memcpy(pred.data, inputData, inputWidth * inputHeight * sizeof(float));

        pred.convertTo(bitmap, CV_8UC1);

        cv::threshold(bitmap, bitmap, threshold, maxValue, cv::THRESH_BINARY);

        boxes = boxes_from_bitmap(pred, bitmap);

        float ratioWidth = inputWidth * 1.0f / outputWidth;
        float ratioHeight = inputHeight * 1.0f / outputHeight;

        boxes = filter_tag_det_res(boxes, ratioHeight, ratioWidth, outputWidth, outputHeight);

        write_filter_boxes_to_buffer(boxes, outputData, env->GetDirectBufferCapacity(output));

        return static_cast<int>(boxes.size());
    }

    bool preProcessDetect(
            JNIEnv *env,
            jobject input,
            jobject output,
            jobject debug,
            int inputWidth,
            int inputHeight,
            int outputWidth,
            int outputHeight,
            int outputRotation
    ) {
        auto *inputData = static_cast<uint8_t *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(output));
        auto *debugData = static_cast<float *>(env->GetDirectBufferAddress(debug));
        if (!inputData || !outputData) return false;

        const std::vector<float> mean = {0.485f, 0.456f, 0.406f};
        const std::vector<float> scale = {1 / 0.229f, 1 / 0.224f, 1 / 0.225f};

        frame = cv::Mat(inputHeight, inputWidth, CV_8UC4, inputData);
        cv::cvtColor(frame, frame, cv::COLOR_RGBA2BGR);

        rotate_img(frame, outputRotation);
        resize_img(frame, outputWidth, outputHeight);
        frame.convertTo(frame, CV_32FC3, 1.0f / 255.0f);

        const int size = frame.cols * frame.rows;
        neon_mean_scale(reinterpret_cast<const float *>(frame.data), outputData, size, mean, scale);

        if (debugData) {
            output_to_debug_buffer(outputData, debugData, frame.cols, frame.rows);
        }

        return true;
    }

    bool preProcessRecognize(
            JNIEnv *env,
            jobject origin,
            jobject input,
            jobject output,
            int originWidth,
            int originHeight,
            int originRotation,
            int detectedWidth,
            int detectedHeight
    ) {
        auto *originData = static_cast<uint8_t *>(env->GetDirectBufferAddress(origin));
        auto *inputData = static_cast<int *>(env->GetDirectBufferAddress(input));
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(output));
        if (!originData || !inputData || !outputData)
            throw std::runtime_error("Failed to get direct buffer address.");

        auto boxes = buffer_to_filter_boxes(inputData, env->GetDirectBufferCapacity(input));
        if (boxes.empty()) return false;

        recognizeFrame = cv::Mat(originHeight, originWidth, CV_8UC4, originData);
        cv::cvtColor(recognizeFrame, recognizeFrame, cv::COLOR_RGBA2BGR);

        rotate_img(recognizeFrame, originRotation);
        resize_img(recognizeFrame, detectedHeight, detectedWidth);
        recognizeFrame.convertTo(recognizeFrame, CV_32FC3, 1.0f / 255.0f);

        const std::vector<float> mean = {0.5f, 0.5f, 0.5f};
        const std::vector<float> scale = {1 / 0.5f, 1 / 0.5f, 1 / 0.5f};

        int batchCount = 0;
        int outputIndex = 0;
        int max_candidates = 100;

        for (auto bp = boxes.crbegin(); bp != boxes.crend(); ++bp) {
            const std::vector<std::vector<int>> &box = *bp;
            crop_img = get_rotate_crop_image(recognizeFrame, box);

            float wh_ratio = float(crop_img.cols) / float(crop_img.rows);
            crop_img = crnn_resize_img(crop_img, wh_ratio);
            crop_img = pad_crop_to_rec_shape(crop_img);

            if (crop_img.cols != recImageShape[2] || crop_img.rows != recImageShape[1]) {
                throw std::runtime_error("Cropped image has incorrect shape.");
            }

            neon_mean_scale(reinterpret_cast<const float *>(crop_img.data), outputData + outputIndex,
                            crop_img.cols * crop_img.rows, mean, scale);
            outputIndex += crop_img.cols * crop_img.rows * recImageShape[0];

            batchCount++;
            if (batchCount >= max_candidates) break;
        }

        return true;
    }

    bool postProcessRecognize(
            JNIEnv *env,
            jobject outputBuffer,
            jlongArray outputShape,
            jobject tokenBuffer
    ) {
        auto *outputData = static_cast<float *>(env->GetDirectBufferAddress(outputBuffer));
        auto *tokenData = static_cast<int *>(env->GetDirectBufferAddress(tokenBuffer));
        if (!outputData || !tokenData) return false;

        auto *shapeArray = env->GetLongArrayElements(outputShape, nullptr);
        int batchSize = static_cast<int>(shapeArray[0]);
        int seqLen = static_cast<int>(shapeArray[1]);
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

        write_tokens_to_buffer(batchTokens, batchScores, tokenData,
                               env->GetDirectBufferCapacity(tokenBuffer));

        return true;
    }

    jintArray getPixelColorFromImage(
            JNIEnv *env,
            jobject origin,
            jobject input,
            int originWidth,
            int originHeight,
            int originRotation,
            int detectedWidth,
            int detectedHeight
    ) {
        auto *originData = static_cast<uint8_t *>(env->GetDirectBufferAddress(origin));
        auto *inputData = static_cast<int *>(env->GetDirectBufferAddress(input));
        if (!originData || !inputData) {
            throw std::runtime_error("Failed to get direct buffer address.");
        }

        auto boxes = buffer_to_filter_boxes(inputData, env->GetDirectBufferCapacity(input));
        if (boxes.empty()) {
            throw std::runtime_error("No boxes found in input buffer.");
        }

        std::vector<jint> allColors;
        allColors.reserve(boxes.size() * 2);

        colorFrame = cv::Mat(originHeight, originWidth, CV_8UC4, originData);
        rotate_img(colorFrame, originRotation);
        resize_img(colorFrame, detectedHeight, detectedWidth);

        for (const auto &box: boxes) {
            cv::Mat crop_img = get_rotate_crop_image(colorFrame, box);

            auto [textColor, backgroundColor] = extractColors(crop_img);

            jint bgColor =
                    (255 << 24) | ((int) backgroundColor[0] << 16) | ((int) backgroundColor[1] << 8) |
                    (int) backgroundColor[2];
            jint txtColor = (255 << 24) | ((int) textColor[0] << 16) | ((int) textColor[1] << 8) |
                            (int) textColor[2];

            allColors.push_back(bgColor);
            allColors.push_back(txtColor);
        }

        jintArray result = env->NewIntArray(allColors.size());
        if (!result) {
            throw std::runtime_error("Failed to create color array.");
        }

        env->SetIntArrayRegion(result, 0, allColors.size(), allColors.data());
        return result;
    }

private:
    int cropWidth;
    int threads;
    std::vector<int> recImageShape;

    // Cached matrices to avoid reallocations
    cv::Mat pred;
    cv::Mat bitmap;
    cv::Mat frame;
    cv::Mat recognizeFrame;
    cv::Mat crop_img;
    cv::Mat colorFrame;
    std::vector<std::vector<std::vector<int>>> boxes;

    cv::Mat crnn_resize_img(const cv::Mat &img, float wh_ratio) {
        int imgC = recImageShape[0];
        int imgW = recImageShape[2];
        int imgH = recImageShape[1];

        float ratio = float(img.cols) / float(img.rows);
        int resize_w = 0;
        if (ceilf(imgH * ratio) > imgW)
            resize_w = imgW;
        else
            resize_w = int(ceilf(imgH * ratio));
        
        cv::Mat resize_img;
        cv::resize(img, resize_img, cv::Size(resize_w, imgH));
        return resize_img;
    }

    cv::Mat pad_crop_to_rec_shape(const cv::Mat &img) {
        const int channels = recImageShape[0];
        const int target_height = recImageShape[1];
        const int target_width = recImageShape[2];

        cv::Mat padded = cv::Mat(target_height, target_width, img.type(), cv::Scalar(0.5f, 0.5f, 0.5f));
        img.copyTo(padded(cv::Rect(0, 0, img.cols, img.rows)));

        return padded;
    }

    template<class ForwardIterator>
    static inline size_t argmax(ForwardIterator first, ForwardIterator last) {
        return std::distance(first, std::max_element(first, last));
    }

    static void write_tokens_to_buffer(const std::vector<std::vector<int>> &tokens,
                                const std::vector<float> &scores,
                                int *buffer, size_t buffer_size) {
        if (tokens.size() != scores.size()) {
            throw std::runtime_error("Tokens and scores size mismatch");
        }

        auto batchSize = tokens.size();

        buffer[0] = static_cast<int>(batchSize);
        buffer += 1;
        buffer_size -= sizeof(int);

        for (size_t i = 0; i < batchSize; ++i) {
            int word_count = static_cast<int>(tokens[i].size());
            auto required_size = (2 + word_count) * sizeof(int);

            if (buffer_size < required_size) {
                throw std::runtime_error(
                        "Buffer too small to hold detected boxes for batch " + std::to_string(i));
            }

            buffer[0] = word_count;
            buffer[1] = static_cast<int>(scores[i] * 1000);

            int idx = 2;
            for (const auto &word: tokens[i]) {
                buffer[idx++] = word;
            }

            buffer += static_cast<size_t>(required_size / sizeof(int));
            buffer_size -= required_size;
        }
    }

    static std::vector<std::vector<std::vector<int>>>
    buffer_to_filter_boxes(const int *buffer, size_t buffer_size) {
        std::vector<std::vector<std::vector<int>>> boxes;
        if (buffer_size < 1) return boxes;

        int box_count = static_cast<int>(buffer[0]);
        size_t expected_size = 1 + box_count * 4 * 2 * sizeof(int);
        if (buffer_size < expected_size) return boxes;

        int idx = 1;
        for (int b = 0; b < box_count; ++b) {
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

    static std::pair<cv::Scalar, cv::Scalar> extractColors(const cv::Mat &image) {
        auto wh_ratio = float(image.cols) / float(image.rows);
        auto desired_width = int(64 * wh_ratio);
        auto desired_height = 64;
        cv::Mat resized;
        cv::resize(image, resized, cv::Size(desired_width, desired_height));

        cv::Mat pixels;
        pixels = resized.reshape(1, resized.rows * resized.cols);
        pixels.convertTo(pixels, CV_32F);

        cv::Mat labels, centers;
        cv::kmeans(
                pixels, 2, labels,
                cv::TermCriteria(cv::TermCriteria::EPS + cv::TermCriteria::MAX_ITER, 10, 1.0),
                2, cv::KMEANS_PP_CENTERS, centers
        );

        cv::Scalar smallColors, bigColors;
        smallColors = cv::Scalar(centers.at<float>(0, 0), centers.at<float>(0, 1),
                                 centers.at<float>(0, 2));
        bigColors = cv::Scalar(centers.at<float>(1, 0), centers.at<float>(1, 1),
                               centers.at<float>(1, 2));

        int textLabel = (cv::countNonZero(labels == 0) < cv::countNonZero(labels == 1)) ? 0 : 1;
        cv::Scalar backgroundColor, textColor;
        backgroundColor = (textLabel == 0) ? smallColors : bigColors;
        textColor = (textLabel == 0) ? bigColors : smallColors;

        auto calculateLuminance = [](const cv::Scalar &color) {
            return 0.299f * color[2] + 0.587f * color[1] + 0.114f * color[0];
        };

        float textLuminance = calculateLuminance(textColor);
        float bgLuminance = calculateLuminance(backgroundColor);

        float contrastFactor = 0.3f;

        if (textLuminance < bgLuminance) {
            textColor[0] = std::max<float>(0.0f, textColor[0] * (1.0f - contrastFactor * 0.3f));
            textColor[1] = std::max<float>(0.0f, textColor[1] * (1.0f - contrastFactor * 0.3f));
            textColor[2] = std::max<float>(0.0f, textColor[2] * (1.0f - contrastFactor * 0.3f));
        } else {
            textColor[0] = std::min<float>(255.0f, textColor[0] * (1.0f + contrastFactor * 0.5f));
            textColor[1] = std::min<float>(255.0f, textColor[1] * (1.0f + contrastFactor * 0.5f));
            textColor[2] = std::min<float>(255.0f, textColor[2] * (1.0f + contrastFactor * 0.5f));
        }

        if (bgLuminance < textLuminance) {
            backgroundColor[0] = std::max<float>(0.0f, backgroundColor[0] * (1.0f - contrastFactor * 0.3f));
            backgroundColor[1] = std::max<float>(0.0f, backgroundColor[1] * (1.0f - contrastFactor * 0.3f));
            backgroundColor[2] = std::max<float>(0.0f, backgroundColor[2] * (1.0f - contrastFactor * 0.3f));
        } else {
            backgroundColor[0] = std::min<float>(255.0f, backgroundColor[0] * (1.0f + contrastFactor * 0.5f));
            backgroundColor[1] = std::min<float>(255.0f, backgroundColor[1] * (1.0f + contrastFactor * 0.5f));
            backgroundColor[2] = std::min<float>(255.0f, backgroundColor[2] * (1.0f + contrastFactor * 0.5f));
        }

        return {backgroundColor, textColor};
    }
};

// Global instance management similar to BeamSearch
std::unordered_map<jlong, std::unique_ptr<PaddleOCR>> paddleOCRInstances;
jlong paddleOCRInstanceCounter = 0;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_construct(
        JNIEnv *env,
        jobject,
        jint cropWidth,
        jint threads
) {
    auto paddleOCR = std::make_unique<PaddleOCR>(cropWidth, threads);
    jlong handle = ++paddleOCRInstanceCounter;
    paddleOCRInstances[handle] = std::move(paddleOCR);
    return handle;
}

JNIEXPORT jboolean JNICALL
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

JNIEXPORT jint JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_postProcessDetect(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject input,
        jobject output,
        jint inputWidth,
        jint inputHeight,
        jint outputWidth,
        jint outputHeight,
        jfloat threshold,
        jint maxValue
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return 0;
    }

    return paddleOCR->postProcessDetect(
            env, input, output,
            inputWidth, inputHeight,
            outputWidth, outputHeight,
            threshold, maxValue
    );
}

extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_preProcessDetect(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject input,
        jobject output,
        jobject debug,
        jint inputWidth,
        jint inputHeight,
        jint outputWidth,
        jint outputHeight,
        jint outputRotation
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return JNI_FALSE;
    }

    return paddleOCR->preProcessDetect(
            env, input, output, debug,
            inputWidth, inputHeight,
            outputWidth, outputHeight,
            outputRotation
    ) ? JNI_TRUE : JNI_FALSE;
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
        jint originRotation,
        jint detectedWidth,
        jint detectedHeight
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return JNI_FALSE;
    }

    return paddleOCR->preProcessRecognize(
            env, origin, input, output,
            originWidth, originHeight,
            originRotation,
            detectedWidth, detectedHeight
    ) ? JNI_TRUE : JNI_FALSE;
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

    return paddleOCR->postProcessRecognize(
            env, outputBuffer, outputShape, tokenBuffer
    ) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_app_versta_translate_bridge_inference_PaddleOCR_getPixelColorFromImage(
        JNIEnv *env,
        jobject,
        jlong handle,
        jobject origin,
        jobject input,
        jint originWidth,
        jint originHeight,
        jint originRotation,
        jint detectedWidth,
        jint detectedHeight
) {
    auto paddleOCR = paddleOCRInstances[handle].get();
    if (!paddleOCR) {
        return nullptr;
    }

    return paddleOCR->getPixelColorFromImage(
            env, origin, input,
            originWidth, originHeight,
            originRotation,
            detectedWidth, detectedHeight
    );
}

#ifdef __cplusplus
}
#endif
