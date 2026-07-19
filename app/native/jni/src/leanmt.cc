#include "leanmt.hh"

#include <jni.h>

#include <string>
#include <vector>

using namespace leanmt;  // NOLINT

// Our JNI bindings mirror leanmt's blocking service, consumed as a plain C++
// library. Package root: app.versta.translate.bridge.leanmt
using Service = Blocking;

extern "C" {

#define LEANMT_JNI(cls, method) \
  JNICALL Java_app_versta_translate_bridge_leanmt_##cls##_##method

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

// Model

JNIEXPORT jlong LEANMT_JNI(LeanmtModel, ncreate)(JNIEnv *env, jclass,
                                                 jlong encoder_layers,
                                                 jlong decoder_layers,
                                                 jlong feed_forward_depth,
                                                 jlong num_heads, jstring model,
                                                 jstring vocabulary,
                                                 jstring target_vocabulary,
                                                 jstring shortlist) {
  Model::Config config;
  config.encoder_layers = static_cast<size_t>(encoder_layers);
  config.decoder_layers = static_cast<size_t>(decoder_layers);
  config.feed_forward_depth = static_cast<size_t>(feed_forward_depth);
  config.num_heads = static_cast<size_t>(num_heads);

  Package<std::string> package;
  package.model = jstr(env, model);
  package.vocabulary = jstr(env, vocabulary);
  package.target_vocabulary = jstr(env, target_vocabulary);
  package.shortlist = jstr(env, shortlist);

  Model *model_ptr = new Model(config, package);
  return reinterpret_cast<jlong>(model_ptr);
}

JNIEXPORT void LEANMT_JNI(LeanmtModel, ndestroy)(JNIEnv *, jobject,
                                                 jlong handle) {
  delete reinterpret_cast<Model *>(handle);
}

// Service

JNIEXPORT jlong LEANMT_JNI(LeanmtService, ncreate)(JNIEnv *, jclass,
                                                    jlong cache_size) {
  Config config;
  config.cache_size = static_cast<size_t>(cache_size);
  Service *service = new Service(config);
  return reinterpret_cast<jlong>(service);
}

JNIEXPORT void LEANMT_JNI(LeanmtService, ndestroy)(JNIEnv *, jobject,
                                                   jlong handle) {
  delete reinterpret_cast<Service *>(handle);
}

JNIEXPORT jobjectArray LEANMT_JNI(LeanmtService, ntranslate)(
    JNIEnv *env, jobject, jlong service_handle, jlong model_handle,
    jobjectArray texts, jlong max_beam_width, jlong max_sequence_length) {
  Service *service = reinterpret_cast<Service *>(service_handle);
  Model *model_raw = reinterpret_cast<Model *>(model_handle);

  std::vector<std::string> sources;
  jsize length = env->GetArrayLength(texts);
  for (int i = 0; i < length; ++i) {
    jobject element = env->GetObjectArrayElement(texts, i);
    if (element != nullptr) {
      sources.push_back(jstr(env, static_cast<jstring>(element)));
      env->DeleteLocalRef(element);
    }
  }

  auto noop_deleter = [](Model *) {};
  Ptr<Model> model(model_raw, noop_deleter);

  Options options;
  options.max_beam_width = static_cast<size_t>(max_beam_width);
  options.max_sequence_length = static_cast<size_t>(max_sequence_length);
  Responses responses =
      service->translate(model, std::move(sources), options);

  jobjectArray targets = env->NewObjectArray(
      static_cast<jsize>(responses.size()),
      env->FindClass("java/lang/String"), nullptr);
  for (size_t i = 0; i < responses.size(); ++i) {
    env->SetObjectArrayElement(targets, static_cast<jsize>(i),
                               env->NewStringUTF(responses[i].target.text.c_str()));
  }

  return targets;
}

#undef LEANMT_JNI

}  // extern "C"
