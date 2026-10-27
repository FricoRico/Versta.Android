// supertonic.cc — JNI binding for the Supertonic 3 native engine.
//
// Kotlin side: bridge/speech/Supertonic3.kt (seam contract from NEU-70:
// sampleRate / load(models, config, threads) / setStyle(Path?) /
// synthesize(tokens, speed) / close()).
//
// Handle discipline per app/native/AGENTS.md: module-level map keyed by a
// monotonically increasing counter; 0 means invalid; every entry point
// guards on lookup. A handle owns one supertonic::Engine — sessions live and
// die with it, and re-load() on a live handle frees the previous engine
// first. Native calls block; the Kotlin side invokes from a background
// dispatcher. Nothing throws across the boundary: native failures return
// false/null and the Kotlin wrapper surfaces them.
//
// Locking: the map mutex only guards map lookup/insert/erase, never the
// inference itself (a synthesize can run for seconds). Contexts are held by
// shared_ptr, so close() erasing an entry while a call is in flight is safe:
// the engine is destroyed when the last in-flight reference drops. Each
// context serializes its own engine calls on its own mutex.
//
// readFileBytes error strings are logged through Log.h; actionable detail for
// app-level failures travels in the false/null return only.

#include <jni.h>

#include <fstream>
#include <memory>
#include <mutex>
#include <sstream>
#include <unordered_map>

#include "include/Log.h"
#include "supertonic_engine.h"

#define LOG_TAG "Supertonic3"

namespace {

struct SupertonicContext {
    // Serializes this context's engine calls (load/setStyle/synthesize/...).
    std::mutex mutex;
    std::unique_ptr<supertonic::Engine> engine;
};

std::unordered_map<jlong, std::shared_ptr<SupertonicContext>> contexts;
jlong contextCounter = 0;
std::mutex contextsMutex;

// Short-lived map lock only; the returned pointer stays valid even if a
// concurrent close() erases the entry, since close() drops a shared_ptr.
std::shared_ptr<SupertonicContext> lookup(jlong handle) {
    std::lock_guard<std::mutex> lock(contextsMutex);
    auto it = contexts.find(handle);
    return it != contexts.end() ? it->second : nullptr;
}

std::string jstr(JNIEnv* env, jstring s) {
    if (s == nullptr) {
        return "";
    }
    const char* cstr = env->GetStringUTFChars(s, nullptr);
    std::string out(cstr != nullptr ? cstr : "");
    if (cstr != nullptr) {
        env->ReleaseStringUTFChars(s, cstr);
    }
    return out;
}

bool readFileBytes(const std::string& path, std::string* out) {
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) {
        return false;
    }
    auto n = f.tellg();
    if (n < 0) {
        return false;
    }
    f.seekg(0);
    out->resize(static_cast<size_t>(n));
    f.read(out->data(), n);
    return f.good() || f.eof();
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_construct(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(contextsMutex);
    jlong handle = ++contextCounter;
    contexts[handle] = std::make_shared<SupertonicContext>();
    return handle;
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_load(
        JNIEnv* env,
        jobject,
        jlong handle,
        jstring durationPredictor,
        jstring textEncoder,
        jstring vectorEstimator,
        jstring vocoder,
        jstring config,
        jint threads) {
    auto ctx = lookup(handle);
    if (ctx == nullptr) {
        LOGE("load called with unknown handle %lld", static_cast<long long>(handle));
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lock(ctx->mutex);
    try {
        std::string configBytes;
        const auto configPath = jstr(env, config);
        if (!readFileBytes(configPath, &configBytes)) {
            LOGE("cannot read config: %s", configPath.c_str());
            return JNI_FALSE;
        }
        // Re-load on a live handle frees the previous engine with this
        // assignment (sessions die before the interpreters, inside ~Engine).
        ctx->engine = supertonic::Engine::load(jstr(env, durationPredictor),
                                               jstr(env, textEncoder),
                                               jstr(env, vectorEstimator),
                                               jstr(env, vocoder),
                                               configBytes,
                                               static_cast<int>(threads));
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("load failed: %s", e.what());
        ctx->engine.reset();
        return JNI_FALSE;
    } catch (...) {
        LOGE("load failed: unknown error");
        ctx->engine.reset();
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_setStyle(
        JNIEnv* env,
        jobject,
        jlong handle,
        jstring path) {
    auto ctx = lookup(handle);
    if (ctx == nullptr) {
        LOGE("setStyle called with unknown handle %lld", static_cast<long long>(handle));
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> lock(ctx->mutex);
    if (!ctx->engine) {
        LOGE("setStyle called before load (handle %lld)", static_cast<long long>(handle));
        return JNI_FALSE;
    }
    try {
        if (path == nullptr) {
            // Seam semantics: null restores the neutral "no bound style"
            // state; synthesize then fails loudly until a style is set. The
            // app layer always binds the default style at load().
            ctx->engine->clearStyle();
            return JNI_TRUE;
        }
        const auto stylePath = jstr(env, path);
        std::string styleBytes;
        if (!readFileBytes(stylePath, &styleBytes)) {
            LOGE("cannot read style: %s", stylePath.c_str());
            return JNI_FALSE;
        }
        // parseStyle throws on malformed/dim-mismatched files; the engine
        // only swaps on success, so a bad file keeps the previous style.
        auto style = ctx->engine->parseStyle(styleBytes);
        ctx->engine->setStyle(style);
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("setStyle failed: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("setStyle failed: unknown error");
        return JNI_FALSE;
    }
}

JNIEXPORT jfloatArray JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_synthesize(
        JNIEnv* env,
        jobject,
        jlong handle,
        jlongArray tokens,
        jfloat speed) {
    auto ctx = lookup(handle);
    if (ctx == nullptr) {
        LOGE("synthesize called with unknown handle %lld", static_cast<long long>(handle));
        return nullptr;
    }
    std::lock_guard<std::mutex> lock(ctx->mutex);
    if (!ctx->engine) {
        LOGE("synthesize called before load (handle %lld)", static_cast<long long>(handle));
        return nullptr;
    }
    try {
        std::vector<int64_t> ids;
        if (tokens != nullptr) {
            const jsize n = env->GetArrayLength(tokens);
            ids.resize(static_cast<size_t>(n));
            env->GetLongArrayRegion(tokens, 0, n, reinterpret_cast<jlong*>(ids.data()));
        }
        auto wav = ctx->engine->synthesize(ids, speed);
        jfloatArray out = env->NewFloatArray(static_cast<jsize>(wav.size()));
        if (out == nullptr) {
            LOGE("synthesize: failed to allocate %zu-sample output", wav.size());
            return nullptr;  // OutOfMemoryError pending on the JVM side.
        }
        if (!wav.empty()) {
            env->SetFloatArrayRegion(out, 0, static_cast<jsize>(wav.size()), wav.data());
        }
        return out;
    } catch (const std::exception& e) {
        LOGE("synthesize failed: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("synthesize failed: unknown error");
        return nullptr;
    }
}

JNIEXPORT jint JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_sampleRate(
        JNIEnv*,
        jobject,
        jlong handle) {
    auto ctx = lookup(handle);
    if (ctx == nullptr) {
        return 0;  // 0 = not loaded; the app layer stands in pre-load.
    }
    std::lock_guard<std::mutex> lock(ctx->mutex);
    if (!ctx->engine) {
        return 0;
    }
    return ctx->engine->sampleRate();
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_setSeed(
        JNIEnv*,
        jobject,
        jlong handle,
        jlong seed) {
    auto ctx = lookup(handle);
    if (ctx == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> lock(ctx->mutex);
    if (ctx->engine) {
        ctx->engine->setSeed(static_cast<uint32_t>(seed));
    }
}

JNIEXPORT jboolean JNICALL
Java_app_versta_translate_bridge_speech_Supertonic3_close(
        JNIEnv*,
        jobject,
        jlong handle) {
    std::shared_ptr<SupertonicContext> ctx;
    {
        std::lock_guard<std::mutex> lock(contextsMutex);
        auto it = contexts.find(handle);
        if (it == contexts.end()) {
            return JNI_FALSE;
        }
        ctx = it->second;
        contexts.erase(it);
    }
    // ctx is destroyed here, outside the map lock: the engine is freed
    // immediately unless a call is still in flight on another thread.
    return JNI_TRUE;
}

}  // extern "C"
