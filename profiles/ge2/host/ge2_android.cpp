#include <jni.h>
#include <android/log.h>

#include <string>

#ifndef GE2_PROFILE_ID
#define GE2_PROFILE_ID "ge2"
#endif

#ifndef GE2_TARGET_ABI
#define GE2_TARGET_ABI "arm64-v8a"
#endif

namespace {
constexpr char kLogTag[] = "GE2Native";

void log_info(const char *message) {
    __android_log_write(ANDROID_LOG_INFO, kLogTag, message);
}

jstring make_status(JNIEnv *env) {
    const std::string status =
        std::string("profile=") + GE2_PROFILE_ID +
        "; abi=" + GE2_TARGET_ABI +
        "; state=scaffold_only; game_runtime=not_implemented";
    return env->NewStringUTF(status.c_str());
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *) {
    JNIEnv *env = nullptr;
    if (vm == nullptr || vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK ||
        env == nullptr) {
        return JNI_ERR;
    }

    log_info("God Eater 2 PSPRecomp native profile loaded; game runtime is not implemented yet.");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_psprecomp_ge2_NativeBridge_getStatus(JNIEnv *env, jclass) {
    if (env == nullptr) {
        return nullptr;
    }
    return make_status(env);
}
