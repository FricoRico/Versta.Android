//
// Created by Ricardo Snoek on 1/11/26.
//
// OCR Text Analyzer - Extracts visual properties from detected text regions
// Separate from PaddleOCR to maintain clean separation of concerns
//
#include <algorithm>
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <vector>
#include <unordered_map>
#include <memory>
#include <android/log.h>
#include "include/Log.h"
/**
 * Font weight estimation based on edge density
 */
enum class FontWeight : int {
    REGULAR = 0,
    BOLD = 1
};
/**
 * Text metrics for a single text region
 */
struct TextMetrics {
    cv::Scalar backgroundColor;
    cv::Scalar textColor;
    float fontSize;
    float lineHeight;
    FontWeight fontWeight;
};
class OcrTextAnalyzer {
private:
    int threads_ = 1;
    float boldThreshold_ = 0.20f;
    mutable cv::Mat cachedResized_;
    mutable cv::Mat cachedPixels_;
    mutable cv::Mat cachedLabels_;
    mutable cv::Mat cachedCenters_;
    
    // Combined extraction: gets colors and font weight in one pass
    // Uses 16x16 k-means (4x faster than 32x32) with single resize operation
    struct TextVisualMetrics {
        cv::Scalar backgroundColor;
        cv::Scalar textColor;
        FontWeight fontWeight;
    };
    
    TextVisualMetrics extractVisualMetrics(const cv::Mat &image) const {
        // Use smaller 16x16 size for speed (4x faster than 32x32)
        cv::resize(image, cachedResized_, cv::Size(16, 16));
        
        // Extract font weight from the resized image (avoids second resize)
        FontWeight weight = extractFontWeightFromResized(cachedResized_);
        
        // K-means on 16x16 is ~7ms (vs 30ms for 32x32)
        cachedPixels_ = cachedResized_.reshape(1, cachedResized_.rows * cachedResized_.cols);
        cachedPixels_.convertTo(cachedPixels_, CV_32F);
        cv::kmeans(
            cachedPixels_, 2, cachedLabels_,
            cv::TermCriteria(cv::TermCriteria::EPS + cv::TermCriteria::MAX_ITER, 5, 2.0),
            2, cv::KMEANS_PP_CENTERS, cachedCenters_
        );
        
        cv::Scalar color0(
            cachedCenters_.at<float>(0, 0),
            cachedCenters_.at<float>(0, 1),
            cachedCenters_.at<float>(0, 2)
        );
        cv::Scalar color1(
            cachedCenters_.at<float>(1, 0),
            cachedCenters_.at<float>(1, 1),
            cachedCenters_.at<float>(1, 2)
        );
        
        int textLabel = (cv::countNonZero(cachedLabels_ == 0) < cv::countNonZero(cachedLabels_ == 1)) ? 0 : 1;
        cv::Scalar backgroundColor = (textLabel == 0) ? color1 : color0;
        cv::Scalar textColor = (textLabel == 0) ? color0 : color1;
        enhanceContrast(backgroundColor, textColor);
        
        return {backgroundColor, textColor, weight};
    }
    
    // Font weight extraction that works on already-resized image
    FontWeight extractFontWeightFromResized(const cv::Mat &resizedImage) const {
        if (resizedImage.empty()) {
            return FontWeight::REGULAR;
        }
        
        cv::Scalar mean, stddev;
        cv::meanStdDev(resizedImage, mean, stddev);
        
        float variance = stddev[0] * stddev[0];
        float normalizedVariance = variance / 3600.0f;
        
        return (normalizedVariance > 0.66f) ? FontWeight::BOLD : FontWeight::REGULAR;
    }
    static void enhanceContrast(cv::Scalar &backgroundColor, cv::Scalar &textColor) {
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
    }
    static float extractFontSize(const std::vector<std::vector<int>> &box, const cv::Mat &textImage) {
        // Calculate the height of the bounding box from the four corner points
        // Use the average of the two vertical edges for better accuracy with perspective
        float leftHeight = sqrt(pow(box[3][0] - box[0][0], 2) + pow(box[3][1] - box[0][1], 2));
        float rightHeight = sqrt(pow(box[2][0] - box[1][0], 2) + pow(box[2][1] - box[1][1], 2));
        float boxHeight = (leftHeight + rightHeight) / 2.0f;
        
        // If we have the actual text image, use it for better font size estimation
        if (!textImage.empty()) {
            // For single-line text, the text image height is a good estimate of font size
            // This works better than just using the bounding box height
            float textBasedFontSize = static_cast<float>(textImage.rows);
            
            // ADJUSTED: Use more weight on box height to reduce overall font sizes
            // More conservative approach: 60% box-based, 40% text-based
            // This provides better sizing that's less likely to be too large
            float weightedFontSize = 0.4f * textBasedFontSize + 0.6f * boxHeight;
            
            // ADDITIONAL: Apply a scaling factor to make fonts more conservative
            // Reduce by 20% to make fonts more appropriately sized
            return weightedFontSize * 0.8f;
        }
        
        // Fallback to box height if text image is not available
        // Also apply conservative scaling here
        return boxHeight * 0.8f;
    }
    
    static float extractLineHeight(const std::vector<std::vector<int>> &box, const cv::Mat &textImage) {
        // Line height estimation based on text content and bounding box
        // This helps with proper text layout and multi-line text handling
        
        float fontSize = extractFontSize(box, textImage);
        
        // If we have text image, use its height as line height estimate
        // Text image height typically represents the line height well
        if (!textImage.empty()) {
            float textImageHeight = static_cast<float>(textImage.rows);
            
            // Line height should be at least font size, but can be larger for spacing
            // Use text image height but ensure it's reasonable relative to font size
            float estimatedLineHeight = textImageHeight;
            
            // Add some line spacing (20% of font size) for better readability
            float lineSpacing = fontSize * 0.2f;
            
            return estimatedLineHeight + lineSpacing;
        }
        
        // Fallback: use font size with standard line spacing (1.2x font size)
        // This is a common typographic convention
        return fontSize * 1.2f;
    }


    FontWeight extractFontWeight(const cv::Mat &image) const {
        // Ultra-conservative approach: use only basic image statistics
        // Avoid all potentially problematic OpenCV operations
        
        if (image.empty() || image.cols <= 0 || image.rows <= 0) {
            return FontWeight::REGULAR; // Default to regular if invalid input
        }
        
        // Simple heuristic: use image brightness variance as bold indicator
        // Bold text typically has higher contrast/variance than regular text
        
        cv::Scalar mean, stddev;
        try {
            // Use meanStdDev on the original image (most robust OpenCV operation)
            cv::meanStdDev(image, mean, stddev);
            
            // Calculate brightness variance (proxy for contrast)
            float variance = stddev[0] * stddev[0]; // Variance = stddev^2
            
            // Normalize variance to 0-1 range based on typical values
            // For 8-bit images, stddev typically ranges 0-60, variance 0-3600
            float normalizedVariance = variance / 3600.0f;
            
            // Adjusted threshold: higher variance required for bold
            // This reduces false positives while still catching true bold text
            return (normalizedVariance > 0.66f) ? FontWeight::BOLD : FontWeight::REGULAR;
            
        } catch (...) {
            // If anything fails, default to regular
            return FontWeight::REGULAR;
        }
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
        left = std::max(0, left);
        top = std::max(0, top);
        right = std::min(srcImage.cols, right);
        bottom = std::min(srcImage.rows, bottom);
        if (right <= left || bottom <= top) {
            return cv::Mat();
        }
        cv::Mat crop;
        srcImage(cv::Rect(left, top, right - left, bottom - top)).copyTo(crop);
        for (auto &point : points) {
            point[0] -= left;
            point[1] -= top;
        }
        auto width = int(sqrt(pow(points[0][0] - points[1][0], 2) +
                              pow(points[0][1] - points[1][1], 2)));
        auto height = int(sqrt(pow(points[0][0] - points[3][0], 2) +
                               pow(points[0][1] - points[3][1], 2)));
        if (width <= 0 || height <= 0) {
            return cv::Mat();
        }
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
        }
        return dstImg;
    }
    static void resizeWithAspect(cv::Mat &image, int targetWidth, int targetHeight) {
        if (image.rows == targetHeight && image.cols == targetWidth) {
            return;
        }
        float sourceAspect = float(image.cols) / float(image.rows);
        float targetAspect = float(targetWidth) / float(targetHeight);
        int newWidth, newHeight;
        if (sourceAspect > targetAspect) {
            newWidth = targetWidth;
            newHeight = int(float(targetWidth) / sourceAspect);
        } else {
            newHeight = targetHeight;
            newWidth = int(float(targetHeight) * sourceAspect);
        }
        cv::Mat resized;
        cv::resize(image, resized, cv::Size(newWidth, newHeight));
        image = cv::Mat(targetHeight, targetWidth, image.type(), cv::Scalar(128, 128, 128));
        int x = (targetWidth - newWidth) / 2;
        int y = (targetHeight - newHeight) / 2;
        resized.copyTo(image(cv::Rect(x, y, newWidth, newHeight)));
    }
public:
    explicit OcrTextAnalyzer(int threads = 1, float boldThreshold = 0.20f)
        : threads_(threads), boldThreshold_(boldThreshold) {
        cv::setNumThreads(threads_);
    }
    void setBoldThreshold(float threshold) {
        boldThreshold_ = threshold;
    }
    float getBoldThreshold() const {
        return boldThreshold_;
    }
    static std::vector<std::vector<std::vector<int>>>
    bufferToBoxes(const int *buffer, size_t bufferCapacity) {
        std::vector<std::vector<std::vector<int>>> boxes;
        if (bufferCapacity < sizeof(int)) {
            return boxes;
        }
        int count = buffer[0];
        boxes.reserve(count);
        int idx = 1;
        for (int i = 0; i < count; ++i) {
            std::vector<std::vector<int>> box(4);
            for (int j = 0; j < 4; ++j) {
                box[j] = {buffer[idx], buffer[idx + 1]};
                idx += 2;
            }
            boxes.push_back(box);
        }
        return boxes;
    }
    std::vector<TextMetrics> analyzeTextRegions(
        const cv::Mat &sourceImage,
        const std::vector<std::vector<std::vector<int>>> &boxes,
        int recognizeSize
    ) const {
        std::vector<TextMetrics> results;
        results.reserve(boxes.size());
        auto frame = sourceImage.clone();
        resizeWithAspect(frame, recognizeSize, recognizeSize);

        for (const auto &box : boxes) {
            TextMetrics metrics;

            auto cropImg = getRotateCropImage(frame, box);
            
            // Combined extraction: colors + font weight in one pass with single resize
            // 16x16 k-means (~7ms) instead of 32x32 (~30ms)
            auto visualMetrics = extractVisualMetrics(cropImg);
            metrics.backgroundColor = visualMetrics.backgroundColor;
            metrics.textColor = visualMetrics.textColor;
            metrics.fontWeight = visualMetrics.fontWeight;

            // Extract font size from both bounding box and text image for better accuracy
            metrics.fontSize = extractFontSize(box, cropImg);
            
            // NEW: Extract line height for better text layout
            metrics.lineHeight = extractLineHeight(box, cropImg);

            results.push_back(metrics);
        }
        return results;
    }
};
std::unordered_map<jlong, std::unique_ptr<OcrTextAnalyzer>> ocrTextAnalyzerInstances;
jlong ocrTextAnalyzerInstanceCounter = 0;
extern "C" JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_inference_OcrTextAnalyzer_construct(
    JNIEnv *env,
    jobject,
    jint threads,
    jfloat boldThreshold
) {
    auto analyzer = std::make_unique<OcrTextAnalyzer>(threads, boldThreshold);
    jlong handle = ++ocrTextAnalyzerInstanceCounter;
    ocrTextAnalyzerInstances[handle] = std::move(analyzer);
    return handle;
}
extern "C" JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_inference_OcrTextAnalyzer_close(
    JNIEnv *env,
    jobject,
    jlong handle
) {
    auto it = ocrTextAnalyzerInstances.find(handle);
    if (it != ocrTextAnalyzerInstances.end()) {
        ocrTextAnalyzerInstances.erase(it);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_inference_OcrTextAnalyzer_setBoldThreshold(
    JNIEnv *env,
    jobject,
    jlong handle,
    jfloat threshold
) {
    auto it = ocrTextAnalyzerInstances.find(handle);
    if (it != ocrTextAnalyzerInstances.end()) {
        it->second->setBoldThreshold(threshold);
    }
}
extern "C" JNIEXPORT jfloat JNICALL
Java_app_versta_translate_bridge_inference_OcrTextAnalyzer_getBoldThreshold(
    JNIEnv *env,
    jobject,
    jlong handle
) {
    auto it = ocrTextAnalyzerInstances.find(handle);
    if (it != ocrTextAnalyzerInstances.end()) {
        return it->second->getBoldThreshold();
    }
    return 0.22f;
}
extern "C" JNIEXPORT jintArray JNICALL
Java_app_versta_translate_bridge_inference_OcrTextAnalyzer_analyzeTextRegions(
    JNIEnv *env,
    jobject,
    jlong handle,
    jobject imageBuffer,
    jobject boxesBuffer,
    jint imageWidth,
    jint imageHeight,
    jint rotation,
    jint recognizeSize
) {
    auto it = ocrTextAnalyzerInstances.find(handle);
    if (it == ocrTextAnalyzerInstances.end()) {
        return nullptr;
    }
    auto *imageData = static_cast<uint8_t *>(env->GetDirectBufferAddress(imageBuffer));
    auto *boxesData = static_cast<int *>(env->GetDirectBufferAddress(boxesBuffer));
    if (!imageData || !boxesData) {
        return nullptr;
    }
    cv::Mat frame(imageHeight, imageWidth, CV_8UC4, imageData);
    if (rotation == 90) cv::rotate(frame, frame, cv::ROTATE_90_CLOCKWISE);
    else if (rotation == 180) cv::rotate(frame, frame, cv::ROTATE_180);
    else if (rotation == 270) cv::rotate(frame, frame, cv::ROTATE_90_COUNTERCLOCKWISE);
    cv::cvtColor(frame, frame, cv::COLOR_RGBA2BGR);
    auto boxes = OcrTextAnalyzer::bufferToBoxes(
        boxesData, 
        env->GetDirectBufferCapacity(boxesBuffer)
    );
    if (boxes.empty()) {
        return env->NewIntArray(0);
    }
    auto metrics = it->second->analyzeTextRegions(frame, boxes, recognizeSize);
    std::vector<jint> results;
    results.reserve(metrics.size() * 4);
    for (const auto &m : metrics) {
        jint bgColor = (255 << 24) | 
                       (static_cast<int>(m.backgroundColor[2]) << 16) |
                       (static_cast<int>(m.backgroundColor[1]) << 8) |
                       static_cast<int>(m.backgroundColor[0]);
        jint txtColor = (255 << 24) |
                        (static_cast<int>(m.textColor[2]) << 16) |
                        (static_cast<int>(m.textColor[1]) << 8) |
                        static_cast<int>(m.textColor[0]);
        jint fontSizeInt = static_cast<jint>(m.fontSize * 100);
        jint lineHeightInt = static_cast<jint>(m.lineHeight * 100); // NEW: Add line height
        jint fontWeightInt = static_cast<jint>(m.fontWeight);
        results.push_back(bgColor);
        results.push_back(txtColor);
        results.push_back(fontSizeInt);
        results.push_back(lineHeightInt); // NEW: Include line height in results
        results.push_back(fontWeightInt);
    }
    jintArray result = env->NewIntArray(static_cast<int>(results.size()));
    if (result) {
        env->SetIntArrayRegion(result, 0, static_cast<int>(results.size()), results.data());
    }
    return result;
}
