//
// Created by Ricardo Snoek on 16/12/2024.
//

#include <jni.h>
#include <vector>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif
JNIEXPORT jobject JNICALL
Java_app_versta_translate_bridge_tokenize_Vocabulary_load(JNIEnv *env, jobject,
                                                              jstring filePath) {
    const char *nativeFilePath = env->GetStringUTFChars(filePath, nullptr);
    int fd = open(nativeFilePath, O_RDONLY);
    if (fd < 0) return nullptr;

    size_t fileSize = lseek(fd, 0, SEEK_END);
    char *data = (char *) mmap(nullptr, fileSize, PROT_READ, MAP_PRIVATE, fd, 0);
    close(fd);

    std::vector<std::string> words; // Store words in order
    char *ptr = data;
    while (ptr < data + fileSize) {
        std::string word(ptr);
        ptr += word.size() + 1; // Skip the null-terminated word
        ptr += sizeof(int);    // Skip the int value (we're inferring from position)
        words.push_back(word);
    }

    munmap(data, fileSize);
    env->ReleaseStringUTFChars(filePath, nativeFilePath);

    // Convert the vector of strings to a Java ArrayList
    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID arrayListInit = env->GetMethodID(arrayListClass, "<init>", "()V");
    jmethodID arrayListAdd = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    jobject arrayList = env->NewObject(arrayListClass, arrayListInit);
    for (const std::string &word: words) {
        jstring wordStr = env->NewStringUTF(word.c_str());
        env->CallBooleanMethod(arrayList, arrayListAdd, wordStr);
        env->DeleteLocalRef(wordStr); // Clean up local references
    }

    return arrayList;
}
#ifdef __cplusplus
}
#endif