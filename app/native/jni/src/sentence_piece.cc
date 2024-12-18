//
// Created by Ricardo Snoek on 16/12/2024.
//

#include <jni.h>
#include <sentencepiece/sentencepiece_processor.h>

using sentencepiece::SentencePieceProcessor;
using sentencepiece::util::Status;
using absl::string_view;

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jlong JNICALL
Java_app_versta_translate_bridge_tokenize_SentencePiece_constructor(JNIEnv *env, jobject) {
    auto *instance = new SentencePieceProcessor();
    return (jlong) instance;
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_tokenize_SentencePiece_destructor(JNIEnv *env, jobject, jlong ptr) {
    auto *instance = (SentencePieceProcessor *) ptr;
    delete instance;
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_tokenize_SentencePiece_load(JNIEnv *env, jobject, jlong ptr,
                                                           jstring filename) {
    auto *instance = (SentencePieceProcessor *) ptr;

    jsize len = env->GetStringUTFLength(filename);

    const char *str = env->GetStringUTFChars(filename, nullptr);
    Status status = instance->Load(string_view(str, len));
    env->ReleaseStringUTFChars(filename, str);
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_tokenize_SentencePiece_loadFromSerializedProto(JNIEnv *env, jobject,
                                                                              jlong ptr,
                                                                              jbyteArray serialized) {
    auto *instance = (SentencePieceProcessor *) ptr;

    jsize len = env->GetArrayLength(serialized);

    void *str = env->GetPrimitiveArrayCritical(serialized, nullptr);
    Status status = instance->LoadFromSerializedProto(
            string_view(static_cast<const char *>(str), len));
    env->ReleasePrimitiveArrayCritical(serialized, str, JNI_ABORT);
}

JNIEXPORT jobjectArray JNICALL
Java_app_versta_translate_bridge_tokenize_SentencePiece_encodeAsPieces(JNIEnv *env, jobject,
                                                                jlong ptr, jstring input) {
    auto *instance = (SentencePieceProcessor *) ptr;

    std::vector<std::string> vec;
    jsize len = env->GetStringUTFLength(input);

    const char *str = env->GetStringUTFChars(input, nullptr);
    Status status = instance->Encode(string_view(str, len), &vec);
    env->ReleaseStringUTFChars(input, str);

    if (!status.ok()) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), status.ToString().c_str());
    }

    jobjectArray array = env->NewObjectArray(vec.size(), env->FindClass("java/lang/String"),
                                             nullptr);
    for (int i = 0; i < vec.size(); ++i) {
        env->SetObjectArrayElement(array, i, env->NewStringUTF(vec[i].c_str()));
    }
    return array;
}
#ifdef __cplusplus
}
#endif