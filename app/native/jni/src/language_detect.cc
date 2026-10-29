#include <jni.h>
#include <compact_lang_det.h>

#include <cstring>
#include <memory>
#include <mutex>
#include <unordered_map>

// LanguageDetect registry.
//
// construct/close run on the main thread (LanguageViewModel init/onCleared)
// while detectLanguage runs on Dispatchers.Default from the debounced
// auto-detect collector, so the registry takes the same hardened shape as
// the OCR engine registry (ocr/engine.cc): shared_ptr entries behind a
// mutex, and close() moves the entry out under the lock and destroys it
// outside it, deferring destruction to the last in-flight caller instead of
// freeing the instance mid-detect.

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

namespace {
std::mutex languageDetectRegistryMutex;
std::unordered_map<jlong, std::shared_ptr<LanguageDetect>> languageDetectInstances;
jlong languageDetectInstanceCounter = 0;

// Guarded handle lookup; unlike map::operator[], never grows the table on
// garbage handles. The shared_ptr copy is the lifetime contract: an erased
// handle keeps its LanguageDetect alive until the in-flight call returns —
// destruction runs on the last ref's thread, never mid-call.
static std::shared_ptr<LanguageDetect> languageDetectFor(jlong handle) {
    std::lock_guard<std::mutex> lock(languageDetectRegistryMutex);
    auto it = languageDetectInstances.find(handle);
    return it == languageDetectInstances.end() ? nullptr : it->second;
}
} // namespace

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_utils_LanguageDetect_construct(
        JNIEnv *env,
        jobject
) {
    auto languageDetect = std::make_shared<LanguageDetect>();
    std::lock_guard<std::mutex> lock(languageDetectRegistryMutex);
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
    auto languageDetect = languageDetectFor(handle);
    if (!languageDetect) {
        return nullptr;
    }

    jclass languageDetectionResultClass = env->FindClass("app/versta/translate/bridge/utils/LanguageDetectResult");
    if (!languageDetectionResultClass) {
        return nullptr;
    }
    jmethodID languageDetectionResultInit = env->GetMethodID(languageDetectionResultClass, "<init>","(Ljava/lang/String;ZI)V");
    if (!languageDetectionResultInit) {
        return nullptr;
    }

    const char *nativeText = env->GetStringUTFChars(text, nullptr);
    if (!nativeText) {
        return nullptr;
    }

    DetectionResult detectionResult;
    try {
        detectionResult = languageDetect->detectLanguage(nativeText);
    } catch (...) {
        env->ReleaseStringUTFChars(text, nativeText);
        return nullptr;
    }
    env->ReleaseStringUTFChars(text, nativeText);

    // detectionResult.language points into CLD2's static code table, so it
    // stays valid after the input string is released.
    jstring language = env->NewStringUTF(detectionResult.language);

    return env->NewObject(languageDetectionResultClass,
                          languageDetectionResultInit,
                          language,
                          detectionResult.isReliable,
                          detectionResult.confidence);
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_utils_LanguageDetect_close(
        JNIEnv *env,
        jobject,
        jlong handle
) {
    std::shared_ptr<LanguageDetect> languageDetect;
    {
        std::lock_guard<std::mutex> lock(languageDetectRegistryMutex);
        auto it = languageDetectInstances.find(handle);
        if (it == languageDetectInstances.end()) return JNI_FALSE;
        languageDetect = std::move(it->second);
        languageDetectInstances.erase(it);
    }
    // Destroys here unless an in-flight detect still holds a ref — later
    // lookups already miss.
    languageDetect.reset();
    return JNI_TRUE;
}

// Debug hook for the androidTest regression suite: registry occupancy, so a
// stress run can prove every constructed handle was closed. Declared as a
// @JvmStatic companion external in Kotlin because a registry-global query has
// no instance context; it must not be `internal` — the JVM name mangling of
// internal declarations would break the JNI symbol link.
JNIEXPORT jint JNICALL
Java_app_versta_translate_bridge_utils_LanguageDetect_instanceCount(
        JNIEnv *env,
        jclass
) {
    std::lock_guard<std::mutex> lock(languageDetectRegistryMutex);
    return static_cast<jint>(languageDetectInstances.size());
}
#ifdef __cplusplus
}
#endif
