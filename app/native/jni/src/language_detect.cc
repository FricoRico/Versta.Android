#include <jni.h>
#include <compact_lang_det.h>

struct DetectionResult {
    const char *language;
    bool isReliable;
    int confidence;
};

class LanguageDetect {
public:
    DetectionResult detectLanguage(const char *text) {
        bool reliable;
        size_t size = strlen(text);

        int textBytes;
        int prefixBytes;

        CLD2::ExtDetectLanguageSummaryCheckUTF8(
                text,
                int(size),
                true,
                &hints,
                0,
                languages,
                percentages,
                scores,
                nullptr,
                &textBytes,
                &reliable,
                &prefixBytes
        );

        return DetectionResult{
                CLD2::LanguageCode(languages[0]),
                reliable,
                percentages[0]
        };
    }

private:
    const CLD2::CLDHints hints = {nullptr, nullptr, 0, CLD2::UNKNOWN_LANGUAGE};
    CLD2::Language languages[3]{};
    int percentages[3]{};
    double scores[3]{};
};

std::unordered_map<jlong, std::unique_ptr<LanguageDetect>> languageDetectInstances;
jlong languageDetectInstanceCounter = 0;

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_utils_LanguageDetect_construct(
        JNIEnv *env,
        jobject
) {
    auto languageDetect = std::make_unique<LanguageDetect>();
    jlong handle = ++languageDetectInstanceCounter;
    languageDetectInstances[handle] = std::move(languageDetect);
    return handle;
}

JNIEXPORT jobject JNICALL
Java_app_versta_translate_bridge_utils_LanguageDetect_detectLanguage(
        JNIEnv *env,
        jobject,
        jlong handle,
        jstring text) {
    auto languageDetect = languageDetectInstances[handle].get();
    if (!languageDetect) {
        return nullptr;
    }

    const char *nativeText = env->GetStringUTFChars(text, nullptr);

    jclass languageDetectionResultClass = env->FindClass("app/versta/translate/bridge/utils/LanguageDetectResult");
    jmethodID languageDetectionResultInit = env->GetMethodID(languageDetectionResultClass, "<init>","(Ljava/lang/String;ZI)V");

    try {
        auto detectionResult = languageDetect->detectLanguage(nativeText);

        jstring language = env->NewStringUTF(detectionResult.language);
        jboolean reliable = detectionResult.isReliable;
        jint confidence = detectionResult.confidence;

        jobject result = env->NewObject(languageDetectionResultClass,
                                        languageDetectionResultInit,
                                        language,
                                        reliable,
                                        confidence);

        env->ReleaseStringUTFChars(text, nativeText);
        return result;
    } catch (...) {
        return nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_utils_LanguageDetect_close(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    if (languageDetectInstances.erase(handle) > 0) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
#ifdef __cplusplus
}
#endif