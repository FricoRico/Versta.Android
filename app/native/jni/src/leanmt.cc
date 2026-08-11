#include "leanmt.hh"

#include <jni.h>

#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

// JNI bindings for leanmt's blocking translation service.
// Package root: app.versta.translate.bridge.leanmt
//
// The model table stores a shared_ptr because leanmt::Blocking::translate
// consumes a leanmt::Ptr<Model> (std::shared_ptr<Model>): translate() copies
// the pointer before releasing the table lock, so a model stays alive while
// a translation is in flight even if destroy() runs on another thread. The
// reverse never holds: a translation never outlives its service handle.

struct LeanmtModelHandle {
    std::shared_ptr<leanmt::Model> model;
};

struct LeanmtServiceHandle {
    std::unique_ptr<leanmt::Blocking> service;
};

// Guards both tables: create, destroy and lookup run on different threads.
std::mutex leanmtModelInstancesMutex;
std::unordered_map<jlong, std::unique_ptr<LeanmtModelHandle>> leanmtModelInstances;
jlong leanmtModelInstanceCounter = 0;

std::mutex leanmtServiceInstancesMutex;
std::unordered_map<jlong, std::unique_ptr<LeanmtServiceHandle>> leanmtServiceInstances;
jlong leanmtServiceInstanceCounter = 0;

static std::shared_ptr<leanmt::Model> findModel(jlong handle) {
    std::lock_guard<std::mutex> lock(leanmtModelInstancesMutex);
    auto it = leanmtModelInstances.find(handle);
    return it != leanmtModelInstances.end() ? it->second->model : nullptr;
}

static leanmt::Blocking *findService(jlong handle) {
    std::lock_guard<std::mutex> lock(leanmtServiceInstancesMutex);
    auto it = leanmtServiceInstances.find(handle);
    return it != leanmtServiceInstances.end() ? it->second->service.get() : nullptr;
}

extern "C" {

static std::string jstr(JNIEnv *env, jstring s) {
    if (s == nullptr) {
        return "";
    }
    const char *cstr = env->GetStringUTFChars(s, nullptr);
    std::string out(cstr != nullptr ? cstr : "");
    if (cstr != nullptr) {
        env->ReleaseStringUTFChars(s, cstr);
    }
    return out;
}


// ---------------------------------------------------------------------------
// LeanmtModel
// ---------------------------------------------------------------------------


JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_leanmt_LeanmtModel_create(
        JNIEnv *env,
        jobject,
        jlong encoderLayers,
        jlong decoderLayers,
        jlong feedForwardDepth,
        jlong numHeads,
        jstring model,
        jstring vocabulary,
        jstring targetVocabulary,
        jstring shortlist
) {
    leanmt::Model::Config config;
    config.encoder_layers = static_cast<size_t>(encoderLayers);
    config.decoder_layers = static_cast<size_t>(decoderLayers);
    config.feed_forward_depth = static_cast<size_t>(feedForwardDepth);
    config.num_heads = static_cast<size_t>(numHeads);

    leanmt::Package<std::string> package;
    package.model = jstr(env, model);
    package.vocabulary = jstr(env, vocabulary);
    package.target_vocabulary = jstr(env, targetVocabulary);
    package.shortlist = jstr(env, shortlist);

    auto handle = std::make_unique<LeanmtModelHandle>();
    try {
        handle->model = std::make_shared<leanmt::Model>(config, package);
    } catch (...) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(leanmtModelInstancesMutex);
    jlong id = ++leanmtModelInstanceCounter;
    leanmtModelInstances[id] = std::move(handle);
    return id;
}


JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_leanmt_LeanmtModel_destroy(
        JNIEnv *,
        jobject,
        jlong handle
) {
    // An in-flight translate keeps the model alive via its shared_ptr copy
    // (design note above); the erase only ends Kotlin's reach.
    std::unique_ptr<LeanmtModelHandle> model;
    {
        std::lock_guard<std::mutex> lock(leanmtModelInstancesMutex);
        auto it = leanmtModelInstances.find(handle);
        if (it == leanmtModelInstances.end()) {
            return;
        }
        model = std::move(it->second);
        leanmtModelInstances.erase(it);
    }  // frees here, outside the lock
}


// ---------------------------------------------------------------------------
// LeanmtService
// ---------------------------------------------------------------------------


JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_leanmt_Leanmt_create(
        JNIEnv *,
        jobject,
        jlong cacheSize
) {
    leanmt::Config config;
    config.cache_size = static_cast<size_t>(cacheSize);

    auto handle = std::make_unique<LeanmtServiceHandle>();
    try {
        handle->service = std::make_unique<leanmt::Blocking>(config);
    } catch (...) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(leanmtServiceInstancesMutex);
    jlong id = ++leanmtServiceInstanceCounter;
    leanmtServiceInstances[id] = std::move(handle);
    return id;
}


JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_leanmt_Leanmt_destroy(
        JNIEnv *,
        jobject,
        jlong handle
) {
    std::unique_ptr<LeanmtServiceHandle> service;
    {
        std::lock_guard<std::mutex> lock(leanmtServiceInstancesMutex);
        auto it = leanmtServiceInstances.find(handle);
        if (it == leanmtServiceInstances.end()) {
            return;
        }
        service = std::move(it->second);
        leanmtServiceInstances.erase(it);
    }  // frees here, outside the lock
}


JNIEXPORT jobjectArray JNICALL
Java_app_versta_translate_bridge_leanmt_Leanmt_translate(
        JNIEnv *env,
        jobject,
        jlong handle,
        jlong modelHandle,
        jobjectArray texts,
        jlong maxBeamWidth,
        jlong maxSequenceLength
) {
    leanmt::Blocking *service = findService(handle);
    std::shared_ptr<leanmt::Model> model = findModel(modelHandle);
    if (service == nullptr || model == nullptr) {
        return nullptr;
    }

    std::vector<std::string> sources;
    jsize count = env->GetArrayLength(texts);
    for (int i = 0; i < count; ++i) {
        jobject element = env->GetObjectArrayElement(texts, i);
        if (element != nullptr) {
            sources.push_back(jstr(env, static_cast<jstring>(element)));
            env->DeleteLocalRef(element);  // keeps local refs bounded for any input size
        }
    }

    leanmt::Options options;
    options.max_beam_width = static_cast<size_t>(maxBeamWidth);
    options.max_sequence_length = static_cast<size_t>(maxSequenceLength);

    leanmt::Responses responses;
    try {
        responses = service->translate(model, std::move(sources), options);
    } catch (...) {
        return nullptr;
    }

    jclass string = env->FindClass("java/lang/String");
    jobjectArray targets = env->NewObjectArray(
            static_cast<jsize>(responses.size()), string, nullptr);
    if (targets == nullptr) {
        return nullptr;
    }

    for (size_t i = 0; i < responses.size(); ++i) {
        jstring target = env->NewStringUTF(responses[i].target.text.c_str());
        env->SetObjectArrayElement(targets, static_cast<jsize>(i), target);
        env->DeleteLocalRef(target);
    }

    return targets;
}

}  // extern "C"
