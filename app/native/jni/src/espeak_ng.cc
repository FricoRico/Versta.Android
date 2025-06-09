#include <jni.h>
#include <string>

#include "espeak-ng/speak_lib.h"

enum synthesis_result {
    SYNTH_CONTINUE = 0,
    SYNTH_ABORT = 1
};

static JavaVM *jvm = nullptr;
jmethodID METHOD_callback;

static JNIEnv *getJniEnv() {
    JNIEnv *env = nullptr;
    jvm->AttachCurrentThread(&env, nullptr);
    return env;
}

static int SynthCallback(short *audioData, int numSamples,
                         espeak_EVENT *events) {
    JNIEnv *env = getJniEnv();
    auto object = (jobject) events->user_data;

    if (numSamples < 1) {
        env->CallVoidMethod(object, METHOD_callback, nullptr);
        return SYNTH_ABORT;
    }

    jbyteArray arrayAudioData = env->NewByteArray(numSamples * 2);
    env->SetByteArrayRegion(arrayAudioData, 0, (numSamples * 2), (jbyte *) audioData);
    env->CallVoidMethod(object, METHOD_callback, arrayAudioData);
    return SYNTH_CONTINUE;

}

#ifdef __cplusplus
extern "C" {
#endif
#define BUFFER_SIZE_IN_MILLISECONDS 300

JNIEXPORT jint
JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    jvm = vm;
    JNIEnv *env;

    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }

    return JNI_VERSION_1_6;
}

JNIEXPORT jboolean
JNICALL Java_app_versta_translate_bridge_speech_ESpeakNG_initialize(
        JNIEnv *env, jclass clazz) {
    METHOD_callback = env->GetMethodID(clazz, "callback", "([B)V");

    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_speech_ESpeakNG_construct(
        JNIEnv *env,
        jobject,
        jstring path
) {
    const char *c_path = path ? env->GetStringUTFChars(path, nullptr) : nullptr;

    espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, BUFFER_SIZE_IN_MILLISECONDS, c_path, 0);

    if (c_path) {
        env->ReleaseStringUTFChars(path, c_path);
    }
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_speech_ESpeakNG_terminate(JNIEnv *env, jobject) {
    espeak_Terminate();
}

JNIEXPORT jstring JNICALL
Java_app_versta_translate_bridge_speech_ESpeakNG_phonemize(JNIEnv *env, jobject, jstring text,
                                                           jstring language) {
    const char *nativeText = env->GetStringUTFChars(text, nullptr);
    const char *nativeLanguage = env->GetStringUTFChars(language, nullptr);

    const void *textPtr = nativeText;

    espeak_SetVoiceByName(nativeLanguage);
    espeak_SetParameter(espeakEMPHASIS, 1, 0);

    auto phonemes = espeak_TextToPhonemes(&textPtr, espeakCHARS_UTF8, espeakPHONEMES_IPA);
    jstring result = env->NewStringUTF(phonemes);

    env->ReleaseStringUTFChars(text, nativeText);
    env->ReleaseStringUTFChars(language, nativeLanguage);

    return result;
}

JNIEXPORT void JNICALL
Java_app_versta_translate_bridge_speech_ESpeakNG_synthesize(JNIEnv *env, jobject instance,
                                                            jstring text,
                                                            jstring language) {
    const char *nativeText = env->GetStringUTFChars(text, nullptr);
    const char *nativeLanguage = env->GetStringUTFChars(language, nullptr);
    size_t len = strlen(nativeText);

    unsigned int unique_identifier;

    espeak_SetVoiceByName(nativeLanguage);
    espeak_SetParameter(espeakEMPHASIS, 1, 0);
    espeak_SetParameter(espeakRATE, 100, 0);
    espeak_SetParameter(espeakPITCH, 436, 732);
    espeak_SetParameter(espeakVOLUME, 100, 0);
    espeak_SetSynthCallback(SynthCallback);
    espeak_Synth(nativeText, len, 0, POS_CHARACTER, 0, espeakCHARS_UTF8, &unique_identifier,
                 instance);
    espeak_Synchronize();

    env->ReleaseStringUTFChars(text, nativeText);
    env->ReleaseStringUTFChars(language, nativeLanguage);
}

JNIEXPORT void
JNICALL Java_app_versta_translate_bridge_speech_ESpeakNG_cancel(JNIEnv *env, jobject) {
    espeak_Cancel();
}
#ifdef __cplusplus
}
#endif