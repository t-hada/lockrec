#include <jni.h>
#include <string.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "lockrec-whisper"

static void log_callback(enum ggml_log_level level, const char *text, void *user_data) {
    (void) user_data;
    __android_log_write(level >= GGML_LOG_LEVEL_ERROR ? ANDROID_LOG_ERROR : ANDROID_LOG_DEBUG, TAG, text);
}

JNIEXPORT jlong JNICALL
Java_dev_thada_lockrec_Whisper_nativeInit(JNIEnv *env, jclass clazz, jstring model_path) {
    (void) clazz;
    whisper_log_set(log_callback, NULL);
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_dev_thada_lockrec_Whisper_nativeFree(JNIEnv *env, jclass clazz, jlong ctx) {
    (void) env;
    (void) clazz;
    whisper_free((struct whisper_context *) ctx);
}

JNIEXPORT jint JNICALL
Java_dev_thada_lockrec_Whisper_nativeTranscribe(JNIEnv *env, jclass clazz, jlong ctx,
                                                jfloatArray samples, jint n_threads, jstring language) {
    (void) clazz;
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);
    jfloat *data = (*env)->GetFloatArrayElements(env, samples, NULL);
    const jsize n_samples = (*env)->GetArrayLength(env, samples);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = lang;
    params.translate = false;
    params.n_threads = n_threads;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    const int result = whisper_full((struct whisper_context *) ctx, params, data, n_samples);

    (*env)->ReleaseFloatArrayElements(env, samples, data, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, language, lang);
    return result;
}

JNIEXPORT jint JNICALL
Java_dev_thada_lockrec_Whisper_nativeSegmentCount(JNIEnv *env, jclass clazz, jlong ctx) {
    (void) env;
    (void) clazz;
    return whisper_full_n_segments((struct whisper_context *) ctx);
}

JNIEXPORT jlong JNICALL
Java_dev_thada_lockrec_Whisper_nativeSegmentStartCs(JNIEnv *env, jclass clazz, jlong ctx, jint index) {
    (void) env;
    (void) clazz;
    return whisper_full_get_segment_t0((struct whisper_context *) ctx, index);
}

// whisper のトークン境界で UTF-8 が途中で切れることがあり NewStringUTF だと abort するため、バイト列で返す
JNIEXPORT jbyteArray JNICALL
Java_dev_thada_lockrec_Whisper_nativeSegmentText(JNIEnv *env, jclass clazz, jlong ctx, jint index) {
    (void) clazz;
    const char *text = whisper_full_get_segment_text((struct whisper_context *) ctx, index);
    const jsize length = (jsize) strlen(text);
    jbyteArray bytes = (*env)->NewByteArray(env, length);
    (*env)->SetByteArrayRegion(env, bytes, 0, length, (const jbyte *) text);
    return bytes;
}
